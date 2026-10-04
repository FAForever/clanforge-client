# Selenium UI tests: Browse Clans, Create Clan, My Clan, Login and Invitations

End-to-end browser tests (Python, Selenium 4, pytest) for `/clans` (Browse Clans),
`/create-clan` (Create Clan) and `/clanhome` (My Clan). They drive the real running application.

## Running

1. Start the database and the app with the **dev** profile. The tests sign in
   as its `admin`, `player` and `guest` accounts:

   ```bash
   docker compose up -d
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
   ```

2. In a second terminal, from the project root:

   ```bash
   pip install -r selenium_tests/requirements.txt
   pytest selenium_tests
   ```

You need Chrome (or Firefox) installed. Selenium 4 downloads a matching driver
automatically.

| Option / variable | Purpose |
|---|---|
| `--base-url http://host:port` or `CLANFORGE_URL` | App location (default `http://localhost:8080`) |
| `--headed` | Show the browser instead of running headless |
| `--slow 0.4` | Pause after each click, keystroke and page load so you can follow a `--headed` run |
| `--browser firefox` | Use Firefox instead of Chrome |
| `CHROME_BINARY`, `CHROMEDRIVER` | Point at a specific Chrome / chromedriver |

Run one page's tests: `pytest selenium_tests/test_my_clan.py`.
Run a single test: `pytest selenium_tests -k promote`.

## State and side effects

The tests use your real dev database, so each test sets up the state it needs
and doesn't rely on a clean database:

- **Before each Create Clan test**, `guest` leaves or deletes any clan they're in.
- **For the My Clan tests**, `admin` and `player` are removed from their current
  clans (an owned clan is **deleted** from Settings). `admin` then founds a new
  public "Selenium xxxxxxxxxx" clan and `player` joins it.
- Clans created by the tests are soft-deleted when the test finishes where
  possible. The My Clan test clan is left in place so you can inspect it.

Don't run the suite while you're using those three accounts for a demo.

## Tests

**Login** (`test_login.py`, 5 tests)

| Test | Checks |
|---|---|
| `test_login_button_opens_popup` | On Home (`/`) the popup starts closed, and Login opens it with the username field visible. `/login` opens it by itself, so it can't test the button |
| `test_login_popup_closes_with_x` | On `/login` the popup is open, and × closes it |
| `test_invalid_login_shows_error` | A wrong password shows "Invalid username or password.", and you stay signed out |
| `test_valid_login_succeeds` | Signing in through the form lands on Browse Clans with the account menu showing |
| `test_login_returns_user_to_protected_page` | Opening `/settings` signed out goes to login, and signing in returns you to `/settings` |

**Invitations** (`test_invitations.py`, 5 tests). Each test gives `admin` a fresh clan and starts with `player` in no clan

| Test | Checks |
|---|---|
| `test_manager_can_send_invitation` | "Invite sent to player.", and player is listed under Invites Sent as Pending, invited as Member |
| `test_recipient_can_see_invitation` | player's inbox shows the clan, "Invited by admin as Member", and Accept/Decline |
| `test_recipient_can_decline_invitation` | Declining the right row shows "Invitation declined." and removes it from player's inbox and admin's sent list |
| `test_manager_can_revoke_invitation` | Revoking shows "Invitation revoked." and removes it from admin's sent list and player's inbox |
| `test_recipient_can_accept_invitation` | Accepting goes to My Clan with "Welcome to <clan>.", and player is listed as Member |


**Browse Clans** (`test_browse_clans.py`, 10 tests, run signed out because the page is public)

| Test | Checks |
|---|---|
| `test_page_loads_for_anonymous_visitor` | Title, active nav item, Sign In button, search placeholder, filter and sort options, the new "Most Members" default sort, card count and the "Showing X of Y · page P of N" caption |
| `test_header_stats_agree_with_the_listing` | "Clans" equals the unfiltered total, and "Recruiting Now" equals the total with the Recruiting filter |
| `test_search_by_name_shows_the_clan_card` | Searching "Paper Tigers": URL and search box keep the text; the card's tag, region, description, member count, badge, founded year and full-name tooltip |
| `test_search_matches_tags_case_insensitively` | "sxc" finds Southern Cross [SXC], and every result contains the text in its name or tag |
| `test_no_matches_shows_empty_state_and_clear_search_resets` | Empty state, no caption, and "Clear search" brings the full list back |
| `test_sql_wildcards_in_search_are_matched_literally` | `%%%` and `___` are treated as plain text, not "match anything" |
| `test_recruiting_filter_splits_open_and_invite_only_clans` | Recruiting shows only "Recruiting" badges and Not Recruiting only "Invite Only"; the choice persists; invite-only Jade Lotus appears in the right one |
| `test_sort_by_members_then_by_name` | Member counts descend by default, names run A to Z under name sort, and switching back works |
| `test_pagination_keeps_search_and_filters` | Next and Previous carry the filters, pages don't overlap, and the sort stays selected. With only one page, checks there's no pager |
| `test_view_clan_opens_the_matching_detail_page` | View Clan opens the clan's page with the same member count, region and membership type, a sign-in prompt instead of Join, and a working "Back to clans" |

The Browse Clans tests rely on the 12 demo clans from `seed_clans.sql` for exact
checks. If they're missing, the tests add them through `docker exec`, or skip with
a message telling you to run `seed_clans.cmd`.

**Create Clan** (`test_create_clan.py`, 10 tests, 11 cases)

| Test | Checks |
|---|---|
| `test_page_loads_with_all_form_fields` | Title, every field, maxlengths, tag hint, region list and default, toggle defaults |
| `test_live_preview_mirrors_name_and_description` | clan-form.js preview updates as you type, and placeholders come back when the fields are cleared |
| `test_tag_input_keeps_only_three_letters_or_digits` | Symbols and spaces are stripped, the tag is capped at 3 characters, and the preview tag and badge update |
| `test_public_toggle_switches_preview_between_public_and_private` | Status badge text and `is-public` / `is-private` classes |
| `test_image_upload_shows_preview_and_can_be_removed` | Choosing a PNG shows both previews, and Remove hides them and clears the input |
| `test_empty_required_fields_block_submission` | HTML5 `required` stops the POST |
| `test_blank_name_is_refused_by_server` | A name of only spaces gets "Give the clan a name." and the typed values are kept |
| `test_duplicate_name_or_tag_is_refused[name/tag]` | Case-insensitive uniqueness refusals |
| `test_user_already_in_a_clan_cannot_create_another` | "You are already in a clan…" refusal |
| `test_successful_creation_lands_on_my_clan` | Redirect, "Your clan is live.", sidebar details, the creator is Owner, and the Create button leaves the top bar |

**My Clan** (`test_my_clan.py`, 10 tests)

| Test | Checks |
|---|---|
| `test_user_without_a_clan_sees_empty_state` | "Not in a clan yet" panel, its three links, and Create Clan navigation |
| `test_sidebar_shows_clan_identity_and_info` | Page title, name, tag, owner, member count, founded year, description, region, visibility, active nav item |
| `test_roster_lists_every_member_with_their_role` | Roster names and roles, count, owner avatar styling and initials |
| `test_member_search_filters_roster_and_clear_resets_it` | Case-insensitive search, count update, empty state, Clear filters, focus |
| `test_role_filter_shows_only_the_selected_role` | Each role option, including one that matches nobody |
| `test_owner_sees_management_controls` | Management links, Manage Invites, per-member actions, no Leave for the owner |
| `test_owner_can_promote_and_demote_a_member` | Make moderator and Make member, the flash, and the role label (the member is restored afterwards) |
| `test_regular_member_has_no_management_controls` | No management, action or edit controls, "Member since…", a Leave button |
| `test_cancelling_remove_confirmation_keeps_the_member` | The confirm() text, and that dismissing it posts nothing |
| `test_member_can_leave_the_clan` | Leave with the confirm() accepted, the flash, the empty state, and the owner's roster shrinking (the member rejoins afterwards) |

## Demo clans for Browse Clans

`seed_clans.sql` adds 12 made-up public clans, 4 to 15 members each, across
all regions. Three of them are invitation-only. The seeded players have no
password, so they fill rosters but can't sign in. It's safe to rerun: existing
seed clans and any name or tag already in use are skipped. Double-click
`seed_clans.cmd` in the project root, or run:

```bash
docker exec -i faf-db psql -U clanadmin -d clan_management < selenium_tests/seed_clans.sql
```

The removal commands are at the top of the file.

## Notes on how the tests are written

- **Input that doesn't arrive.** On some Windows setups, Selenium's
  synthetic mouse and keyboard input silently never reaches Chrome. Every
  element the tests use is a `ResilientWebElement` (`resilient.py`). It tries
  the normal click or typing first, checks whether the page got a `click` or
  `keydown` event, and if not, does the same thing from JavaScript. The page's
  own scripts and the server are exercised either way. An "input fallbacks"
  section at the end of the run shows how often this happened.
- **Confirm dialogs.** Chrome only shows `confirm()` after a real user action,
  so after a JavaScript fallback click it silently answers Cancel.
  `click_expecting_confirm()` records every `confirm()` call the page makes.
  The Remove and Leave tests assert the exact message either way. They
  answer the real dialog when Chrome shows one, or answer the recorded call
  when it doesn't. Cancel posts nothing and OK posts the form, as in the app.

- **Scrolling before clicks.** The top bar is sticky. Newer ChromeDriver
  versions scroll an element to the top edge before clicking it, which puts it
  under the bar. The driver is wrapped so every click and every keystroke
  first scrolls the element to the middle of the window.
- **Headless on Windows.** With display scaling above 100%, headless Chrome
  can drop clicks in the lower part of the page. Headless runs pin the scale
  factor to fix this. `run_selenium_tests.cmd` runs `--headed` anyway.

- **Reduced motion.** `styles.css` fades each `.panel` in from `opacity: 0`.
  Selenium treats that as "not displayed", so the browser is started with
  reduced motion requested, which your CSS already honours.
- **`text_of()` instead of `.text`.** Many labels are uppercased by CSS
  (roles, stat labels, badges), and `.text` returns what's on screen
  ("MODERATOR"). The tests compare the DOM text, which is what the template
  writes ("Moderator").
- **Login.** The dev login form sits inside an animated popup. The helper fills
  it through the DOM and submits it. It's still a real form post with the CSRF
  token.
- **Random tags.** Tags are random 3-character strings. If one collides with
  an existing clan (likely after loading the FAF dump), the helper retries.
