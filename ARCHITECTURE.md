# Architecture Documentation — Clan Management Platform (Clanforge)

A clan management platform for the Forged Alliance Forever community.
**Java 21 · Spring Boot 4.1 · Spring Security · Thymeleaf · PostgreSQL 16 · Flyway · Docker**

This document describes the system's structure: its layers, every controller and
service, the data model, the security model, the audit/synchronisation stream, and
the deployment shape. It complements the README (build/run/deploy) and the Swagger /
OpenAPI document (`/swagger-ui.html`, the full REST contract).

---

## 1. Architectural style

A single, self-contained Spring Boot application following the scope's preferred
**Option A (JVM server-side)** architecture: **one application, one repository, one
deployment artifact, one integrated web UI, one database.** No separate frontend.

- **Server-side rendering** (Thymeleaf) for the browser UI, plus a **stateless REST
  API** under `/api` — both backed by the *same* service layer, so a rule is written
  once and cannot be bypassed by a route.
- **Layered** (controllers → services → repositories → database), with a strict rule:
  **controllers never touch repositories.** Everything that reaches the database goes
  through a service, so visibility and permission rules have exactly one home.

```
                           ┌────────────────────────────────────────────┐
   Browser ───session────► │  WEB layer (7 @Controllers, Thymeleaf)     │
                           │        AdminController, ClanController…    │
   API client ─bearer JWT► │  API layer (5 @RestControllers, JSON)      │
                           │        ClanApiController, ReportApi…       │
                           └───────────────────┬────────────────────────┘
                                               │ (controllers call services only)
                           ┌───────────────────▼───────────────────────┐
                           │  SERVICE layer (10 @Services) — the rules │
                           │  ClanService, MembershipService, Report…  │
                           └───────────────────┬───────────────────────┘
                                               │ (Spring Data interfaces)
                           ┌───────────────────▼───────────────────────┐
                           │  REPOSITORY layer (9 JPA repositories)    │
                           └───────────────────┬───────────────────────┘
                                               │  JDBC
                           ┌───────────────────▼───────────────────────┐
                           │  PostgreSQL 16  (schema owned by Flyway)  │
                           │  + append-only clan_event (CDC/sync)      │
                           └───────────────────────────────────────────┘

   Cross-cutting: security/ (two Spring Security filter chains, CSP, rate limiting)
```

### Package layout

```
com.faforever.clan_management
  entity/       JPA entities (the persistent model). Shared; treated as frozen.
  repository/   Spring Data interfaces. Filtering happens inside the query.
  service/      Business rules. Shared by the web pages AND the REST API.
  web/          @Controllers — HTTP in, model + view name out. Thin.
  api/          @RestControllers — JSON DTOs in/out, never entities.
  security/     The two filter chains, security headers, login rate limiter.
```

---

## 2. Controllers (12)

Controllers are deliberately thin: validate the request, call **one** service, return
a view (web) or a DTO (API). They hold no business rules.

### 2.1 Web controllers — server-rendered pages (7)

| Controller | Routes | Responsibility |
|---|---|---|
| **ClanController** | `/clans`, `/clans/{id}`, `/clans/{id}/logo`, `/clans/{id}/join`, `/clans/{id}/report` | Public clan browse/search list, clan detail page, logo serving (members-only for private clans), joining, and the report form + submission. |
| **CreateClanController** | `/create-clan` | The "create a clan" form and its submission (name, tag, description, visibility, join policy, optional logo). |
| **ClanHomeController** | `/clanhome` | "My Clan" home for the signed-in user — their own clan's dashboard and member view. |
| **SettingsController** | `/settings`, `/settings/logo`, `/settings/transfer` | Clan settings for owners/moderators: edit clan info, upload/remove the logo, transfer ownership. |
| **InvitationController** | `/invitations` | The user's invitations page — view received/sent, accept, decline; owner/moderator invite and revoke. |
| **AdminController** | `/admin`, `/admin/clans`, `/admin/clans/{id}`, `/admin/reports` | The moderator console: list every clan (incl. private/deleted), report queue, and the moderation actions (suspend, rename, retag, delete, dismiss). Double-gated on the admin role. |
| **PageController** | `/`, landing/static pages | Serves the landing and migrated design pages. |

*(Two `@ControllerAdvice` helpers — `CurrentUserAdvice` and `LoginOptionsAdvice` —
inject the signed-in user and login options into every page model. They are advices,
not routed controllers.)*

### 2.2 API controllers — REST / JSON under `/api` (5)

All are documented in OpenAPI / Swagger. Public reads need no token; writes and admin
calls require a bearer JWT from the OIDC provider.

| Controller | Base path | Responsibility |
|---|---|---|
| **ClanApiController** | `/api/clans` | List / get / create / patch / delete clans, and join / leave. |
| **InvitationApiController** | `/api/clans/{id}/invite`, `/api/invitations` | Invite a user, list invitations, accept / decline / revoke. |
| **MembershipApiController** | `/api/clan/...` | Membership operations for the caller. The clan is always resolved from the token — no clan id in the path — so a caller can only act on their own clan. |
| **ReportApiController** | `/api/reports` | File a report against a clan (offensive name / tag / inappropriate content). |
| **AdminApiController** | `/api/admin/...` | Admin-only: list clans, list the report queue, perform moderation actions, read a clan's moderation history. Gated by the admin role in the filter chain *and* re-checked here. |

---

## 3. Services (10)

The service layer is where every rule lives. `ClanService` is the reference pattern:
the clan-visibility rule is written there once, so no route can reach clan data and
forget it. View-shaped data leaves as small carriers (e.g. `ClanCard`, `MemberCard`)
so entities never grow presentation concerns.

| Service | Responsibility |
|---|---|
| **ClanService** | The read side: find *visible* clans (public + active + not deleted), search / filter / sort, build browse cards, and the landing statistics (clan / commander / recruiting counts). Owns the visibility rule. |
| **ClanManagementService** | The write side of a clan: create, update, patch, delete (soft), transfer ownership, logo set/remove, message-of-the-day. Enforces tag/name rules and uniqueness. |
| **MembershipService** | Join, leave, remove a member, change a role. Enforces **exactly one active membership per user**, the owner/moderator authority checks, and "the sole owner cannot just leave." |
| **InvitationService** | The invitation lifecycle: invite, accept, decline, revoke; the five states (Pending / Accepted / Declined / Expired / Revoked) and expiry. Enforces "one pending invite per person per clan." |
| **ReportService** | File a report, serve the moderator queue by state, and close / dismiss. Enforces **one open report per person per clan** and the three report categories. |
| **AdminService** | What a moderator may do to *any* clan: suspend / unsuspend, rename, retag, delete, and answer an open report. Every action writes a `moderation_action` row and a moderation event. (Callers are authorised by `AdminAccess` first.) |
| **ClanEventService** | Writes the **append-only `clan_event`** audit records (JSONB payload) for every meaningful change — the hook external systems sync from. |
| **AppUserService** | Resolves the signed-in principal to an `AppUser` (via the OIDC subject identifier), and user lookups used across the other services. |
| **NotificationService** | In-app notifications for a user (e.g. "you have an invitation"), read by the top bar. |
| **CalendarService** | Clan calendar events (create / list / remove), scoped to the actor's own clan. |

*(Supporting classes in `service/` are not themselves services: `AdminAccess` (authority
check), `TagRule` / `Regions` / `Names` (configurable validation & formatting), the
`*Card` / `*Form` / `*Overview` DTOs, and the `ClanRuleViolation` / `ClanNotFound` /
`ClanAccessRefused` / `DuplicateReport` exceptions mapped to HTTP statuses.)*

---

## 4. Data model

PostgreSQL, schema owned by Flyway (`validate` mode — the code can never silently
drift from the schema; `EntityColumnsExistInMigrationTest` catches drift without a DB).

### Entities (9)

| Entity | Table | Purpose |
|---|---|---|
| **AppUser** | `app_user` | A user / referenced player. Keyed by the OIDC subject. |
| **Clan** | `clan` | A clan: name, tag, description, owner, visibility, join policy, `deleted_at` (soft delete). |
| **ClanMembership** | `clan_membership` | A user's membership of a clan, with role and `left_at` (soft close, not deleted). |
| **ClanInvitation** | `clan_invitation` | An invitation: invitee, inviter, state, created/expires timestamps. |
| **ClanReport** | `clan_report` | A report against a clan: reporter, category, state, resolver. |
| **ModerationAction** | `moderation_action` | A record of a moderator action, with the previous values as JSONB. |
| **ClanEvent** | `clan_event` | **Append-only** audit / sync log (entity_type, entity_id, event_type, JSONB payload, actor, timestamp). |
| **ClanLogo** | `clan_logo` | A clan's image bytes + content type. |
| **CalendarEvent** | `clan_calendar_event` | A clan calendar entry. |

### Relationships (ER sketch)

```
            ┌──────────┐ owner        ┌───────────────────┐
            │ AppUser  │◄─────────────│      Clan         │
            └────┬─────┘              └───┬───┬───┬───┬───┘
                 │                        │   │   │   │
   member of     │  (ClanMembership)      │   │   │   └──1:1── ClanLogo
   ───────────── │ ◄──────────────────────┘   │   │
   invitee/      │  (ClanInvitation)          │   └──1:*── CalendarEvent
   inviter ───── │ ◄──────────────────────────┘
   reporter/     │  (ClanReport)        ClanEvent  (append-only; references any
   resolver ──── │ ◄──────────────────  entity by entity_type + entity_id + actor)
   admin ─────── │  (ModerationAction)
```

### Constraints the application relies on (enforced by the DB)

- `clan_tag_unique` / `clan_name_unique` — partial unique on `lower(tag)` / `lower(name)` where `deleted_at IS NULL` (unique, case-insensitive).
- `membership_one_active_per_user` — partial unique on `clan_membership(user_id) WHERE left_at IS NULL` (**one active membership per user**).
- `invitation_one_pending` — partial unique per `(clan_id, invitee_id)` where state = PENDING.
- `clan_report_one_open` — partial unique per `(clan_id, reporter_id)` where state = OPEN.
- Referential integrity via foreign keys throughout.

### Migrations (Flyway, additive only)

`V1__initial_schema` · `V2__design_extras` · `V3__one_open_report` ·
`V4__close_memberships_of_deleted_clans`. Applied in version order on startup; a
migration already run is never edited (Flyway checksums them).

---

## 5. Security architecture

Two Spring Security filter chains, selected by path:

- **`@Order(1)` — `/api/**`**: stateless, **bearer-JWT** resource server. Tokens are
  validated against the OIDC issuer (signature, issuer, expiry). Roles come from the
  `ext.roles` claim. CSRF is disabled here (stateless).
- **`@Order(2)` — everything else**: session-based browser chain. OIDC login
  (`oauth2Login`) when a provider is configured; CSRF protection on; a login rate
  limiter (`LoginRateLimitFilter`).

Other security measures: a strict **Content-Security-Policy** (no inline scripts/styles
— the main XSS defence), user data always escaped (`th:text`, never `th:utext`),
request-body size caps (`ApiBodyLimit`), and admin routes gated by the role named in
`clan.admin.role` (`ADMIN_CLAN`). Authentication is **external OIDC only** — no local
password management; the dev-profile in-memory accounts are gated by `@Profile("dev")`
and cannot exist in a deployed build.

`security/` classes: `SecurityConfig`, `ApiTokenConverter`, `TokenRoles`, `OidcRoles`,
`ApiBodyLimit`, `LoginRateLimitFilter`.

---

## 6. Audit history & external synchronisation

The `clan_event` table is **append-only** (a Postgres rule turns UPDATE/DELETE into
no-ops) and matches the scope's `event_history` shape: `id`, `occurred_at`,
`entity_type`, `entity_id`, `event_type`, `payload` (JSONB), plus the actor.

`ClanEventService` writes one event for every meaningful change. Recorded event types
include: `ClanCreated`, `ClanUpdated`, `ClanDeleted`, `MemberJoined`, `MemberLeft`,
`MemberRemoved`, `InvitationCreated`, `InvitationAccepted`, `InvitationDeclined`,
`InvitationRevoked`, `OwnershipTransferred`, `ModerationActionPerformed`,
`ClanLogoUpdated`, `ClanMotdUpdated`, `CalendarEventRemoved`.

**This is the integration point for FAForever's ecosystem**: external systems consume
the stream via Change Data Capture (e.g. Debezium). RabbitMQ is not required — the
append-only table is the contract.

---

## 7. Key design decisions

- **Shared service layer for web + API** — a rule (visibility, authority, validation)
  is written once and both surfaces reuse it; no route can bypass it.
- **Soft deletes** (`clan.deleted_at`, `membership.left_at`) — history is preserved for
  the audit stream; nothing is hard-deleted.
- **Rules backed by DB invariants** — pessimistic locks around mutations plus partial
  unique indexes mean races cannot corrupt state even under concurrency.
- **Configurable game-specific rules** — tag validation is a property-driven regex
  (`TagRule`), isolating the FAForever 3-char rule so the platform stays reusable.
- **Flyway owns the schema**, Hibernate only validates — no surprise schema changes.

---

## 8. Deployment

A single deployable artifact (executable JAR / Docker image) plus PostgreSQL.

- **`Dockerfile`** (multi-stage build) + **`docker-compose.yml`** (app + Postgres,
  `--profile app`). `docker-compose.hydra.yml` adds ORY Hydra for the full OIDC flow.
- **All configuration via environment variables.** Production runs the **`oidc`
  profile** (never `dev`) with secrets supplied by the environment; without those it
  fails fast rather than starting insecurely.
- **CI** (GitHub Actions): checkout, dependency cache, run the full test suite, JaCoCo
  coverage, SpotBugs static analysis, and a licence-compliance check, on every push and
  pull request.

---

*Component names, routes, entities, constraints and event types in this document are
taken directly from the source tree (`com.faforever.clan_management`) and the Flyway
migrations. For the exact REST request/response contracts, see the OpenAPI document at
`/v3/api-docs` and Swagger UI at `/swagger-ui.html`.*
