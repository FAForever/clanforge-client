# Acceptance Test E — Administration & Moderation

Test Analysis Report · Clan Management Platform · Sprint 3 Acceptance Testing

**Result: PASS — 7 / 7 procedure steps passed. No defects.**

## Execution details

| Field | Value |
|---|---|
| Date executed | 11 October 2026 |
| Tester | Bryan Sie Kai Wong (24636061) |
| Means of control | Automated — Selenium UI acceptance suite (`selenium_tests/test_admin.py`, 10 tests), supplemented by service-layer tests (`AdminServiceTest`, `ReportServiceTest`) and REST-API integration tests (`AdminActionsApiIntegrationTest`, `ReportAdminApiIntegrationTest`) |
| Application under test | Spring Boot build (`main`), profiles `dev, oidc`, at `http://localhost:8080` |
| Database | PostgreSQL 16 (`faf-db`) |
| Test accounts | `admin` (platform administrator) and a standard (non-admin) development user |

## What this test verifies

Demonstrates satisfaction of the client's Administration Interface requirements: an administrator can list, search, suspend, rename, re-tag (fix an invalid tag) and delete clans; a user-submitted report surfaces in the admin report queue; every moderation action is recorded in the append-only audit history; and these actions are restricted to administrators (access control).

## Procedure results

| # | Procedure | Observed outcome (evidence) | Result |
|---|---|---|---|
| 1 | Standard user submits a report against a clan for an offensive tag; confirm it appears in the admin reports queue. | A standard user filing a report causes it to appear in the admin report queue, and a non-admin cannot read the queue (`ReportAdminApiIntegrationTest.reportThenQueue`). The report submission form is present on the clan page, and the admin "Report Queue" page renders for the administrator (`test_09_admin_can_view_reports_queue`). | PASS |
| 2 | Admin reviews the report and the clan's moderation history. | The administrator opens a clan's dossier and views its moderation history; each action is listed with its actor (history assertions in `test_04/06/08`; `AdminService.history`). | PASS |
| 3 | Admin suspends the clan; confirm suspended status is visible and an audit history event is recorded. | Suspending a clan showed the "Lift Suspension" control and flash "Clan suspended"; the suspend action was recorded in the clan's history (`test_03_admin_can_find_and_suspend_clan`, `test_04_suspend_is_recorded_in_history`; audit event in `AdminServiceTest`). | PASS |
| 4 | Admin renames the clan; confirm the update and corresponding audit log entry. | Renaming updated the clan's displayed name and recorded a rename entry in history (`test_05_admin_can_rename_clan`, `test_06_rename_is_recorded_in_history`). | PASS |
| 5 | Admin deletes a clan; confirm the update and corresponding audit log entry. | Deleting a clan showed the "Clan Deleted" confirmation; the clan was soft-deleted (row retained) and the deletion recorded (`test_10_admin_can_delete_clan`; `AdminService.delete` writes a `moderation_action` + `ClanDeleted` event). | PASS |
| 6 | Admin modifies a clan's invalid tag to a valid one; confirm the update is successful and logged. | Re-tagging changed the clan's tag (passing the same uniqueness/format rules an owner must meet) and recorded a re-tag entry in history (`test_07_admin_can_modify_clan_tag`, `test_08_retag_is_recorded_in_history`). | PASS |
| 7 | Confirm a non-admin user cannot access any of the admin actions. | A non-admin user is denied the admin pages and the admin API (`test_01_non_admin_cannot_access_admin_page` → forbidden; `AdminActionsApiIntegrationTest` access-control cases → 401/403; `AdminApiControllerAccessTest`, `ApiAdminSecurityTest`). The admin role is enforced at both the HTTP layer and again in the service layer (`AdminAccess`). | PASS |

## Test analysis

- **Function:** All admin moderation actions (suspend, rename, re-tag, delete), the report queue, and access control function correctly. Each moderation action writes a `moderation_action` audit row plus the corresponding `clan_event`, and the history is append-only (the database rejects edits/deletes of history rows).
- **Performance:** Each admin action (suspend / rename / re-tag / delete) completed within the 2-second threshold — the actions execute in-process against the database and returned promptly in every test.
- **Data measures:** Pass/Fail recorded per action — 7 / 7 PASS — and each state-changing action was confirmed to generate the correct audit history event (suspend, rename, re-tag, delete all verified in history).
- **Errors / deficiencies:** None. No admin action was reachable by a non-admin user — access control held at both the HTTP and service layers — so no critical security defect was found.

## Additional observations

- **Defence in depth:** The admin role is checked at the HTTP route (`/admin/**`, `/api/admin/**`) and again inside the service layer (`AdminAccess.require`), so a mis-wired route alone could not expose a moderation action.
- **Soft delete:** Deleting a clan retains the row (marked deleted) rather than destroying it, preserving the audit trail and allowing recovery.

## Result

**PASS**

Test E passed in full. Administrators can list, search, suspend, rename, re-tag and delete clans; user reports surface in the admin queue; every moderation action is recorded in the append-only audit history; and all admin actions are restricted to administrators at both the HTTP and service layers — satisfying the client's Administration Interface requirements.
