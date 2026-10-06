# Acceptance Test A — User Authentication (OIDC Login)

Test Analysis Report · Clan Management Platform · Sprint 2 Acceptance Testing

**Result: PASS — 5 / 5 procedure steps passed. No defects.**

## Execution details

| Field | Value |
|---|---|
| Date executed | 28 September 2026 |
| Tester | Bryan Sie Kai Wong (24636061) |
| Means of control | Manual, via browser interaction |
| Application under test | Spring Boot build, profiles `dev, oidc`, at `http://localhost:8080` |
| Database | PostgreSQL 16 (`faf-db`), loaded with the 1,477-clan FAForever dataset |
| External OIDC provider | ORY Hydra v2.2.0 — issuer `http://localhost:4444`; login/consent app at `http://localhost:3000` |
| Test identity | `foo@bar.com` (registered at the OIDC provider; provider-side password) |

## What this test verifies

Demonstrates satisfaction of the client's authentication requirement: the application authenticates via an external OpenID Connect (OIDC) provider and does not implement local password management on that path. Methods tested: login redirect flow, session establishment, protected-route enforcement, and logout.

## Procedure results

| # | Procedure | Observed outcome | Result |
|---|---|---|---|
| 1 | Navigate to a protected page (`/settings`) while logged out; confirm redirect to login. | Browser was redirected from `/settings` to `http://localhost:8080/login`. The login page rendered with the "Sign in with FAForever" option and the development-account form. | PASS |
| 2 | Initiate login ("Sign in with FAForever"); confirm redirect to the external OIDC provider. | Via `/oauth2/authorization/hydra` the browser was redirected to the provider's login app at `http://localhost:3000/login?login_challenge=…` — a distinct host/port from the application. | PASS |
| 3 | Authenticate with valid test credentials at the provider. | Entered the test identity's credentials at the provider's login page and submitted. The provider accepted the credentials and continued the authorization-code flow. | PASS |
| 4 | Confirm redirect back to the application with an active session and visible account info. | Redirected back to the originally-requested page `http://localhost:8080/settings` (the saved request was preserved). The account page showed display name `foo@bar.com`, sign-in ID `foo@bar.com`, "member since 15 September 2026", and clan "Member of ducik [duk]"; the top bar showed the account avatar and menu. | PASS |
| 5 | Log out; confirm the session is invalidated and protected routes again redirect to login. | Clicked Sign Out (`POST /logout`). Redirected to `http://localhost:8080/login?logout` showing "You have been signed out." Re-navigating to `/settings` redirected to `/login` again. | PASS |

## Test analysis

- **Function:** OIDC login, session establishment, protected-route enforcement and logout all function correctly. Authentication is delegated entirely to the external OIDC provider; no local password management is used on the OIDC path. The external identity was provisioned to an application account, so account information renders after sign-in.
- **Performance:** The login round-trip (redirect chain plus token exchange, from initiating login to the authenticated landing page) completed in approximately 1–2 seconds of system processing, and the first sign-in succeeded without failure (the provider's token-signing keys were pre-warmed). Remaining wall-clock time was manual credential entry at the provider.
- **Data measures:** Pass/Fail recorded against each of the five procedure steps — 5 / 5 PASS.
- **Errors / deficiencies:** None. No authentication failures were observed, so no high-severity defects were recorded. (Any authentication failure would be high severity, since all other functionality is gated behind login.)

## Additional observations

- **Saved-request handling:** After authenticating, the user was returned to the specific protected page originally requested (`/settings`), not a generic landing page — confirming saved-request handling.
- **Account provisioning:** The external identity (`foo@bar.com`) was linked to an existing application account (member of clan "ducik [duk]"), confirming end-to-end integration between the OIDC provider and the application's user model.
- **Sign-in options:** Both sign-in options (OIDC and the development form accounts) appeared on the login page, as expected with the `dev` and `oidc` profiles both active. A deployed build without the `dev` profile presents OIDC only.

## Result

**PASS**

Test A passed in full. The Clan Management Platform authenticates users through an external OpenID Connect provider, establishes and invalidates sessions correctly, enforces protected routes for unauthenticated users, and displays authenticated account information — satisfying the client's authentication requirements for Sprint 2.
