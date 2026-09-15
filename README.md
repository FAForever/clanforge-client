# Clan Management Platform - Project


The initial target environment is the FAForever ecosystem, but the solution should be designed as a generic and reusable platform. Game-specific rules should be isolated where possible so that the application can be adapted for other communities.
The platform shall provide:
- a user-facing web interface
- an administration interface
- a REST API
- PostgreSQL-based persistence
- authentication through an external OpenID Connect provider
- an audit history mechanism for external synchronization

The project should demonstrate modern software engineering practices, including:
- secure authentication
- database design
- web application development
- API design
- automated testing
- containerization
- continuous integration and delivery
- open-source software development practices

## Running it

Two things to install: Java 21 or later, and Docker.

```
cp .env.example .env          # optional, the defaults already match
docker compose up -d          # PostgreSQL on 5432, schema applied by Flyway on first run
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Then http://localhost:8080. The `dev` profile adds three in-memory accounts,
`admin` / `admin`, `player` / `player` and `guest` / `guest`, and nothing else;
they are the only way to sign in until OpenID Connect is wired up, and
`@Profile("dev")` keeps them out of any deployed build. Three accounts let one
person show both sides of an invitation, a join or a transfer from two windows.

The container starts empty and the application creates the schema on its first
start, so run the app once before loading anything. Then, for the 1,477 real
clans out of FAForever's published 2023 dump:

```
git clone --depth 1 https://github.com/FAForever/faf-db-dump \
    reference/faforever/faf-db-dump
PSQL_CMD="docker exec -i faf-db psql" PGPORT=5432 python db/load_dump.py
```

The loader runs fourteen checks and prints `ok` for each. It defaults to a local
PostgreSQL on port 5433 rather than the container, which is why `PSQL_CMD` and
`PGPORT` are set above; `PGHOST`, `PGUSER` and `FAF_DUMP` are overridable the
same way.

Every connection value is read from an environment variable. With the `dev`
profile each one has a default that matches `docker-compose.yml`, so nothing
above needs configuring to work. Without the profile, as in a deployed build,
all three must be set or the application refuses to start. `.env` is
gitignored; `.env.example` is the list of what exists.

### Tests

```
./mvnw test
```

Runs without a database. The four tests that need one skip themselves unless
`SPRING_DATASOURCE_URL` is set, which is the signal that a database is
available:

```
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/clan_management SPRING_DATASOURCE_USERNAME=clanadmin SPRING_DATASOURCE_PASSWORD=devpassword ./mvnw test
```

## The schema belongs to Flyway

`src/main/resources/db/migration/V1__initial_schema.sql` is the schema. Flyway
applies it on startup and records what it has applied in `flyway_schema_history`.

Adding a column means adding `V2__add_whatever.sql` beside V1. It does not mean
editing V1: Flyway checksums every migration it has applied and refuses to start
if one changes underneath it. A database that already had V1 applied by hand is
handled by `baseline-on-migrate`, so nobody has to drop and recreate anything.

Hibernate runs in `validate` mode and never generates the schema. An entity that
names a column no migration creates is a startup failure rather than a surprise
later. `EntityColumnsExistInMigrationTest` catches the same mistake without a
database at all, so it fails on every machine and in CI.

## Where code goes

```
src/main/java/com/faforever/clan_management/
    entity/       JPA entities. Shared by everyone, so treat as frozen.
    repository/   Spring Data interfaces. Filtering happens in the query.
    service/      The rules. Both the web pages and the future REST API use these.
    web/          Controllers. Thin: validate, call one service, return a view.
    security/     The filter chain, the headers, the login rate limiter.
src/main/resources/
    db/migration/ Flyway migrations, applied in version order.
    templates/    Thymeleaf pages.
    static/       css, js, images.
```

Controllers do not talk to repositories. Everything that reaches the database
goes through a service, so a visibility or permission rule has exactly one place
to live and cannot be forgotten on a new route. Each package carries a
`package-info.java` saying what it owns.
