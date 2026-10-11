# Acceptance Test B — Clan Creation & Tag Validation

Test Analysis Report · Clan Management Platform · Sprint 2/3 Acceptance Testing

**Result: PASS — 6 / 6 procedure steps passed. No defects.**

## Execution details

| Field | Value |
|---|---|
| Date executed | 11 October 2026 |
| Tester | Jeremy Pang |
| Means of control | Automated — Selenium UI acceptance suite (`selenium_tests/test_create_clan.py`, `test_browse_clans.py`), supplemented by service-layer tests (`ClanManagementServiceTest`, `TagRuleTest`) and REST-API integration tests (`ClanRestApiAcceptanceIntegrationTest`) |
| Application under test | Spring Boot build (`main`), profiles `dev, oidc`, at `http://localhost:8080` |
| Database | PostgreSQL 16 (`faf-db`), loaded with the FAForever dataset |
| Location | Web UI "Create Clan" form, and `POST /api/clans` |

## What this test verifies

Demonstrates satisfaction of the client's Clan Tag Rules and the "create a clan" user feature: clan creation succeeds only with a valid tag, and all four tag rules are enforced — uniqueness, case-insensitivity, maximum length (3), and allowed characters (alphanumeric). A successfully created clan stores and returns all of its fields (name, tag, description, owner, visibility, creation timestamp) and appears in search and listing.

## Procedure results

| # | Procedure | Observed outcome (evidence) | Result |
|---|---|---|---|
| 1 | Create a clan with a valid 3-character alphanumeric tag; confirm success. | Creating a clan with a valid name and tag succeeded and landed the owner on the "My Clan" page (`test_successful_creation_lands_on_my_clan`). At the service and API layers the clan row was persisted with the caller as owner (`ClanManagementServiceTest`, `f1PostValidClan`). | PASS |
| 2 | Attempt a second clan with the same tag in a different case; confirm rejection (case-insensitive uniqueness). | A second clan whose tag differed only in case was refused with a duplicate-tag error (`test_duplicate_name_or_tag_is_refused[tag]`; `createRefusesTakenTagIgnoringCase`; `f2PostDuplicateTagDifferentCase` → 400 "The clan tag '…' is already in use."). The database enforces this with a partial unique index on `lower(tag)`. | PASS |
| 3 | Attempt a tag longer than 3 characters; confirm rejection. | A 4+ character tag was refused — the UI input accepts at most three letters/digits (`test_tag_input_keeps_only_three_letters_or_digits`), and the service/API reject an over-length tag (`createRefusesBadTag`; `TagRule` "ABCD" → invalid; REST `f2` "TOOLONG" → 400). A `char_length(tag) <= 3` CHECK constraint backs this at the database. | PASS |
| 4 | Attempt a tag containing non-alphanumeric characters; confirm rejection. | A tag containing punctuation was refused (`TagRule` "A-B"/"[†]" → invalid; REST `f2` illegal tag "T-F!" → 400 with the rule's message). The UI additionally strips non-alphanumeric characters on input. | PASS |
| 5 | Create a clan supplying name, description and visibility with the tag; confirm all fields are saved and returned (not just the tag). | A clan created with name, description and visibility stored and returned every field correctly; the create form's live preview mirrored name/description and the public/private toggle (`test_live_preview_mirrors_name_and_description`, `test_public_toggle_switches_preview_between_public_and_private`). The REST response was validated field-by-field against the published `ClanDetailResponse` schema (`f1PostValidClan`). | PASS |
| 6 | Confirm the created clan appears correctly in clan search and listing. | A newly created clan is returned by clan search when searching for its tag (`ClanWriteIntegrationTest.searchRunsInTheDatabase` — `byTag` contains the created tag), and the Browse listing renders clan cards and matches tags case-insensitively (`test_browse_clans.py::test_search_matches_tags_case_insensitively`). | PASS |

## Test analysis

- **Function:** Clan creation and all four tag validation rules function correctly. Each rule is enforced at the service layer and, for length and uniqueness, additionally at the database, so no invalid clan can be persisted even if a client bypasses the UI.
- **Performance:** Validation errors are returned promptly — the REST baseline for a rejected `POST /api/clans` (400) averaged a few milliseconds of in-process time; successful creation averaged ~15–30 ms steady-state. All well within interactive thresholds.
- **Data measures:** Pass/Fail recorded per validation rule plus the success case — 6 / 6 PASS (success, case-insensitive uniqueness, max length, allowed characters, all-fields-stored, appears-in-search).
- **Errors / deficiencies:** None. No unenforced rule was found; the database uniqueness and length constraints provide a backstop, so there is no high-severity risk of a duplicate or malformed tag being stored.

## Result

**PASS**

Test B passed in full. Clan creation succeeds only with a valid tag, all four tag rules (uniqueness, case-insensitivity, maximum length, allowed characters) are enforced across the UI, service and database layers, all clan fields are stored and returned correctly, and a created clan appears in search and listing — satisfying the client's Clan Tag Rules and create-a-clan requirements.
