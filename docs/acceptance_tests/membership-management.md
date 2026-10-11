# Acceptance Test C — Membership Management

Test Analysis Report · Clan Management Platform · Sprint 3 Acceptance Testing

**Result: PASS — 7 / 7 procedure steps passed. No defects.**

## Execution details

| Field | Value |
|---|---|
| Date executed | 11 October 2026 |
| Tester | Ziying Zhou |
| Means of control | Automated — Selenium UI acceptance suite (`selenium_tests/test_membership_acceptance.py::test_c_membership_management_workflow`, a single end-to-end flow using multiple test accounts), supplemented by service-layer tests (`MembershipAcceptanceTest`, `ClanManagementServiceTest`) |
| Application under test | Spring Boot build (`main`), profiles `dev, oidc`, at `http://localhost:8080` |
| Database | PostgreSQL 16 (`faf-db`) |
| Test accounts | Multiple development users (owner, member, and a user with no active clan) |

## What this test verifies

Demonstrates satisfaction of the client's Membership Management requirements: the supported operations (join, leave, remove member, transfer ownership), the hard constraint that each user may hold **at most one active membership**, and the clan visibility rules governing how a user may join a Public clan (request to join) versus a Private clan (invitation only).

## Procedure results

| # | Procedure | Observed outcome (evidence) | Result |
|---|---|---|---|
| 1 | User joins Clan A; confirm membership recorded with role `Member`. | The user joined a public clan and the roster showed them with role `MEMBER` (`test_c_membership_management_workflow`, join + role assertion). | PASS |
| 2 | The same user attempts to join Clan B while still in Clan A; confirm rejected. | The second join was refused while the user still held an active membership — the single-active-membership rule blocked it ("Leave your current clan…"), confirmed at the service layer in `MembershipAcceptanceTest`. A partial unique index on `clan_membership(user_id) WHERE left_at IS NULL` enforces this at the database. | PASS |
| 3 | User leaves Clan A; confirm membership removed and the user can now join Clan B. | After leaving, the user's active membership was cleared (historical row retained with `left_at` set) and they successfully joined a second clan. | PASS |
| 4 | Clan owner removes a member; confirm the membership is revoked. | The owner removed a member from the roster and the member's active membership was revoked (`removeMember`). | PASS |
| 5 | Clan owner transfers ownership to another member; confirm roles update accordingly. | Ownership transfer promoted the target member to `OWNER` and demoted the former owner to `MODERATOR`, with roles reflected on the roster (`transferOwnership`; `ClanManagementServiceTest`). | PASS |
| 6 | A user requests to join a Public clan without an invitation; confirm accepted per public join rules. | Joining a public clan without an invitation succeeded immediately (public clans allow request-to-join). | PASS |
| 7 | A user attempts to join a Private clan without an invitation; confirm rejected (invitation only). | A private clan could not be joined without an invitation — to an outsider the private clan is reported as not found (404) and the join is refused; the invitation path was the only way in (`MembershipAcceptanceTest` "by invitation"). | PASS |

## Test analysis

- **Function:** All membership operations (join, leave, remove, transfer), the single-active-membership constraint, and the Public-vs-Private join rules function correctly. The one-active-membership rule is enforced in both the service layer and the database (partial unique index), so it cannot be bypassed.
- **Performance:** Each membership action (join / leave / remove / transfer) completed within the 2-second threshold — the Selenium flow times each action (`measure_action`) and all were comfortably under 2 s.
- **Data measures:** Pass/Fail recorded per operation — 7 / 7 PASS (join, blocked second join, leave-then-join, remove, transfer, public join, private rejection).
- **Errors / deficiencies:** None. No breach of the one-active-membership rule was observed; the database constraint guarantees it even under concurrent requests (verified separately by the row-lock integration test). No high-severity defects recorded.

## Result

**PASS**

Test C passed in full. Joining, leaving, member removal and ownership transfer all operate correctly, each user is held to exactly one active clan membership (enforced at both the service and database layers), and the Public (request-to-join) versus Private (invitation-only) join rules are correctly distinguished — satisfying the client's Membership Management requirements.
