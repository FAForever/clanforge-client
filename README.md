# Clan Management Platform

A clan management platform for the [Forged Alliance Forever](https://faforever.com)
community, built for CITS3200. Browse clans, manage membership and roles, handle
invitations, and administer a clan.

Java 21 · Spring Boot 4.1 · Spring Security · Thymeleaf · PostgreSQL 16 · Flyway · Docker

**Want to _use_ the app?** See the **[User Guide](docs/user-guide.md)** — a screenshot walkthrough of signing in, finding and joining clans, running your own clan, invitations and moderation. This README covers building, running and deploying it.

The initial target environment is the FAForever ecosystem, but the solution should be
designed as a generic and reusable platform. Game-specific rules should be isolated
where possible so that the application can be adapted for other communities. The
platform shall provide a user-facing web interface, an administration interface, a
REST API, PostgreSQL-based persistence, authentication through an external OpenID
Connect provider, and an audit history mechanism for external synchronization.

---

## Running it

Two things to install: **Java 21 or later**, and **Docker Desktop**. No PostgreSQL
install needed. Start Docker Desktop and wait until it reports it is running before
any `docker` command below — a stopped Docker is the most common first-run error. Run
everything from the project root, the folder that holds this README.

```bash
docker compose up -d
```

**macOS / Linux**

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

**Windows PowerShell** — the flag must be quoted, or PowerShell splits it at the dot
and Maven reports `Unknown lifecycle phase ".run.profiles=dev"`:

```powershell
./mvnw spring-boot:run "-Dspring-boot.run.profiles=dev"
```

The app keeps running and prints logs in that terminal. Once you see
`Started ClanManagementApplication`, open <http://localhost:8080>. Press `Ctrl+C` in
the terminal to stop it.

The `dev` profile adds three in-memory accounts, `admin` / `admin`,
`player` / `player` and `guest` / `guest`, and nothing else. `@Profile("dev")` keeps
them out of any deployed build. Three accounts let one person show both sides of an
invitation, a join or a transfer from two windows. `admin` is also a site
administrator, so it can open the admin console at <http://localhost:8080/admin>.

Because those passwords are public, a `dev` run listens on this machine only
(`127.0.0.1`), and every port the compose files publish is bound to `127.0.0.1` as
well. Nobody else on the same network, a campus wifi for instance, can reach the app,
the database or Hydra. To show the app on another device on purpose, start it with
`SERVER_ADDRESS=0.0.0.0`, and only on a network you trust.

### Signing in with OpenID Connect

The scope calls for an external OIDC provider, and FAForever runs
[ORY Hydra](https://www.ory.sh/hydra/). `docker-compose.hydra.yml` runs Hydra locally
as a stand-in, so the same code that works here works against the real provider by
changing one issuer URL.

```bash
docker compose -f docker-compose.yml -f docker-compose.hydra.yml up -d
```

```powershell
$env:SPRING_PROFILES_ACTIVE="dev,oidc"
./mvnw spring-boot:run
```

The login page then offers **Sign in with FAForever** alongside the development
accounts. Hydra has no user database and no login page of its own, so the stack also
runs the ORY reference login app: sign in with `foo@bar.com` and password `foobar`.
That is a development stand-in, not a user store.

Which sign-in is offered is decided by configuration rather than a flag. Spring Boot
only creates a `ClientRegistrationRepository` when a provider is configured, so
`application-oidc.properties` is what turns OIDC on, and `SecurityConfig` and the
login page read the same signal. A deployed build gets OIDC only, because the form
has no accounts to authenticate against without the `dev` profile.

A `hydra-init` container registers the application as an OAuth2 client and warms the
Hydra JWKS endpoint. The warm-up matters: Hydra generates its token-signing keys
lazily on first request, taking about two seconds, and Spring gives up waiting while
verifying the ID token, so without it the very first sign-in always fails.

### The admin console

Site moderators get an admin console at `/admin`. It lists every clan, private and
deleted ones included, and shows each clan's moderation history and the report queue.
From it a moderator can suspend, rename, retag or delete a clan, and dismiss reports.
Every action is written to the clan's moderation history, and an action can answer
an open report, which closes it.

An administrator is anyone holding the role named by `clan.admin.role` (`ADMIN_CLAN`
by default, which is what FAForever calls it). Through the provider the role comes
from `ext.roles`, the same claim the REST API reads from a bearer token. It is taken
from the ID token, and from the access token when that is a JWT the application can
verify. Locally, the `dev` profile's `admin` account holds the role. Hydra's reference
login app hands out no roles, so `foo@bar.com` is not an administrator.

### The REST API

The REST API is documented with OpenAPI and browsable in Swagger UI, both reachable
without a login:

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI document: <http://localhost:8080/v3/api-docs>

It covers clan browsing and management, membership, invitations, reports and
administration. Public reads need no token; write and admin calls authenticate with a
bearer JWT from the OIDC provider (the same `ext.roles` claim the admin console reads).

### Running the application in Docker

To run both PostgreSQL and the application in containers:

```bash
docker compose --profile app up --build -d
```

Then <http://localhost:8080/clans>. Stop with `docker compose --profile app down`.

### Loading the real clan data

The database starts empty and the application creates the schema on its first start,
so run the app once before loading anything. Then, for the 1,477 real clans out of
the published FAForever 2023 dump:

```bash
git clone --depth 1 https://github.com/FAForever/faf-db-dump reference/faforever/faf-db-dump
```

**macOS / Linux**

```bash
PSQL_CMD="docker exec -i faf-db psql" PGPORT=5432 python db/load_dump.py
```

**Windows PowerShell**

```powershell
$env:PSQL_CMD="docker exec -i -e PGCLIENTENCODING=UTF8 faf-db psql"; $env:PGPORT="5432"
python db/load_dump.py
```

The loader runs fourteen checks and prints `ok` for each. It defaults to a local
PostgreSQL on port 5433 rather than the container, which is why `PSQL_CMD` and
`PGPORT` are set above; `PGHOST`, `PGUSER` and `FAF_DUMP` are overridable the same
way. The dump is 28 MB and is not committed; `reference/` is gitignored.

Every connection value is read from an environment variable. With the `dev` profile
each has a default matching `docker-compose.yml`, so nothing above needs configuring.
Without the profile, as in a deployed build, all three must be set or the application
refuses to start. `.env` is gitignored; `.env.example` lists what exists.

### Deploying

A production deployment runs with **OIDC only** — never the `dev` profile, whose
in-memory `admin/admin` account and form login must not be exposed. Run with the
`oidc` profile (not the `dev,oidc` used for local demos) and supply every secret from
the environment:

```bash
SPRING_PROFILES_ACTIVE=oidc \
SPRING_DATASOURCE_URL=... SPRING_DATASOURCE_USERNAME=... SPRING_DATASOURCE_PASSWORD=... \
OIDC_ISSUER_URI=... OIDC_CLIENT_ID=... OIDC_CLIENT_SECRET=... \
SESSION_COOKIE_SECURE=true \
java -jar target/clan-management-*.jar
```

Without the `oidc` profile the REST write and admin endpoints have no JWT to validate
against, so the provider must be configured before the app is exposed.
`docker compose --profile app` defaults to the `dev` profile for local demos; override
`SPRING_PROFILES_ACTIVE` for a real deployment.

---

## Tests

```bash
./mvnw test
```

Runs without a database. The tests that need one skip themselves unless
`SPRING_DATASOURCE_URL` is set, which is the signal that a database is available:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/clan_management SPRING_DATASOURCE_USERNAME=clanadmin SPRING_DATASOURCE_PASSWORD=devpassword ./mvnw test
```

`ClanLockIntegrationTest` races two real transactions on one clan, so it commits what it
writes, and `clan_event` keeps every row for good. It runs only when `CLAN_RACE_TESTS`
names the database it may write to, so point it at one made for the run, never at
`clan_management`:

```bash
createdb clan_race
CLAN_RACE_TESTS=clan_race SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/clan_race SPRING_DATASOURCE_USERNAME=clanadmin SPRING_DATASOURCE_PASSWORD=devpassword ./mvnw test -Dtest=ClanLockIntegrationTest
dropdb clan_race
```

GitHub Actions runs the suite and validates `docker-compose.yml` on every push and
pull request. There is no database on the runner, which is why the skip rule matters:
the build stays green without one.

### Coverage and static analysis

`./mvnw test` also writes a JaCoCo coverage report to `target/site/jacoco/` (open
`index.html`). Line coverage is about **87%** (instruction coverage about 88%), over the
~70% the scope asks for; CI prints the figure in each run's summary.

```bash
./mvnw spotbugs:check   # static analysis: fails on a high-confidence bug
```

CI runs `spotbugs:check` on every push and pull request. It is set to Max effort and a
High threshold, so it catches real defects rather than style, and the build currently
passes it with none.

---

## The schema belongs to Flyway

The files under `src/main/resources/db/migration/` are the schema:
`V1__initial_schema.sql`, `V2__design_extras.sql`, `V3__one_open_report.sql` and
`V4__close_memberships_of_deleted_clans.sql`. Flyway applies them in version order on
startup and records what it has applied in `flyway_schema_history`.

Adding a column means adding the next file, `V5__add_whatever.sql`, beside them. It
does not mean editing a migration that has already been applied: Flyway checksums
every migration it has run and refuses to start if one changes underneath it. A
database that already had V1 applied by hand is handled by `baseline-on-migrate`, so
nobody has to drop and recreate anything.

Hibernate runs in `validate` mode and never generates the schema. An entity that names
a column no migration creates is a startup failure rather than a surprise later.
`EntityColumnsExistInMigrationTest` catches the same mistake without a database at
all, so it fails on every machine and in CI.

---

## Where code goes

```
src/main/java/com/faforever/clan_management/
    entity/       JPA entities. Shared by everyone, so treat as frozen.
    repository/   Spring Data interfaces. Filtering happens in the query.
    service/      The rules. Both the web pages and the REST API use these.
    web/          Controllers. Thin: validate, call one service, return a view.
    api/          REST controllers. Return JSON DTOs, never entities.
    security/     The filter chain, the headers, the login rate limiter.
src/main/resources/
    db/migration/ Flyway migrations, applied in version order.
    templates/    Thymeleaf pages.
    static/       css, js, images.
```

Controllers do not talk to repositories. Everything that reaches the database goes
through a service, so a visibility or permission rule has exactly one place to live
and cannot be forgotten on a new route. Most packages carry a `package-info.java`
saying what they own.

### Front-end conventions

**Put CSS and JavaScript in separate files.** The Content-Security-Policy in
`SecurityConfig` blocks inline `<style>` blocks, inline `<script>` blocks and
`style="..."` attributes. This is the main defence against cross-site scripting, so
inline code silently fails to load rather than erroring. Add stylesheets under
`static/css`, scripts under `static/js`, and reference them with `th:href="@{/css/...}"`
and `th:src="@{/js/...}"`.

**Guard every `querySelector`.** Blocks are rendered conditionally, so an element on
one page may be absent on another. An unguarded `null` throws and every later handler
in that file is never registered. The symptom is a button that silently does nothing.

**Escape user data.** Use `th:text`, never `th:utext`. Clan names and descriptions come
from user input.

**Never commit credentials.** `application.properties` uses `${SPRING_DATASOURCE_URL}`
style placeholders on purpose.

---

## Troubleshooting

The useful error is usually a few lines **above** the final
`[ERROR] Failed to execute goal...`, in a block beginning:

```
***************************
APPLICATION FAILED TO START
***************************
```

| Problem | Cause and fix |
|---|---|
| `Unknown lifecycle phase ".run.profiles=dev"` | PowerShell split the argument. Quote it, or use `$env:SPRING_PROFILES_ACTIVE` |
| `Port 8080 was already in use` | An earlier run is still going. Windows: `Get-NetTCPConnection -LocalPort 8080 -State Listen` then `Stop-Process -Id <PID> -Force` |
| `Failed to configure a DataSource` | The `dev` profile is not active and the three `SPRING_DATASOURCE_*` variables are not set |
| `Connection to localhost:5432 refused` | The database is not running: `docker compose up -d` |
| `./mvnw: not found` in a Docker build | `mvnw` has CRLF line endings. `.gitattributes` prevents this on fresh checkouts; an older clone needs `git rm --cached mvnw` then `git checkout -- mvnw` |
| Docker commands fail | Docker Desktop is not running |

### Useful commands

| Command | Effect |
|---|---|
| `docker compose down` | Stop the database, keep the data |
| `docker compose down -v` | Delete the database and all data |
| `docker exec -it faf-db psql -U clanadmin -d clan_management` | SQL prompt (`\dt` lists tables, `\q` quits) |

---

## Project status

**Working:** PostgreSQL with Flyway migrations, the FAForever data import with 1,477
clans, clan browsing and detail pages, clan creation, invitations, membership and
roles, clan settings, a documented REST API for clan browsing and management,
membership, invitations, reports and administration, the admin console for site
moderators, OIDC sign in through ORY Hydra, security headers, CSP, login rate
limiting, containers and CI.

**Not yet built:** a production login provider. Hydra does not include a login page,
so deployment requires either the FAForever provider or an application that owns that
page.

## Licence

MIT — see [LICENSE](LICENSE).
