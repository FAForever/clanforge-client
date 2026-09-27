# Selenium UI tests: My Clan and Create Clan

End-to-end browser tests (Python, Selenium 4, pytest) for `/clanhome` (My Clan)
and `/create-clan` (Create Clan). They drive the real running application.

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
