"""Selenium tests for the My Clan page (/clanhome).

Covers the "not in a clan" empty state, the clan sidebar, the roster with its
client-side search and role filter (clanhome.js), what the owner and an
ordinary member each see, promoting/demoting, and the confirm() dialogs.

Accounts used (dev profile):
    admin  - owner of a fresh clan built by the `clan` fixture
    player - an ordinary member of that clan
    guest  - a user in no clan (empty-state test)
"""

from datetime import datetime

import pytest
from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import Select

from helpers import (
    click_expecting_confirm,
    text_of,
    create_clan,
    ensure_not_in_clan,
    flash_notice,
    join_clan_by_name,
    login,
    path_of,
    wait,
)

OWNER, MEMBER, OUTSIDER = "admin", "player", "guest"
DESCRIPTION = "Selenium test clan for the My Clan page."
REGION = "Oceania"


@pytest.fixture(scope="module")
def clan(driver, base_url):
    """A fresh public, open clan owned by admin with player as a member."""
    login(driver, base_url, MEMBER)
    ensure_not_in_clan(driver, base_url)
    login(driver, base_url, OWNER)
    ensure_not_in_clan(driver, base_url)
    name, tag = create_clan(driver, base_url, description=DESCRIPTION,
                            region=REGION, public=True, invite_only=False)
    login(driver, base_url, MEMBER)
    join_clan_by_name(driver, base_url, name)
    driver.delete_all_cookies()
    return {"name": name, "tag": tag}


def open_my_clan(driver, base_url, user):
    login(driver, base_url, user)
    driver.get(base_url + "/clanhome")
    wait(driver).until(EC.presence_of_element_located((By.CSS_SELECTOR, "main")))
    return driver


def visible_rows(driver):
    return [r for r in driver.find_elements(By.CSS_SELECTOR, ".member-row") if r.is_displayed()]


def row_for(driver, username):
    return driver.find_element(By.CSS_SELECTOR, f".member-row[data-name='{username}']")


def member_count_label(driver):
    return text_of(driver.find_element(By.CSS_SELECTOR, ".js-member-count"))


# --------------------------------------------------------------------------

def test_user_without_a_clan_sees_empty_state(driver, base_url):
    login(driver, base_url, OUTSIDER)
    ensure_not_in_clan(driver, base_url)
    driver.get(base_url + "/clanhome")

    assert driver.title == "My Clan · Clanforge"
    assert text_of(driver.find_element(By.CSS_SELECTOR, ".success-title")) == "You are not in a clan yet"
    assert not driver.find_elements(By.CSS_SELECTOR, ".roster")

    # Three ways in: browse, invitations (may carry a count badge) and create.
    actions = driver.find_elements(By.CSS_SELECTOR, ".form-actions a")
    assert len(actions) == 3
    assert text_of(actions[0]) == "Browse Clans"
    assert text_of(actions[1]).startswith("Invitations")
    assert text_of(actions[2]) == "Create Clan"
    assert actions[0].get_attribute("href").endswith("/clans")
    assert actions[1].get_attribute("href").endswith("/invitations")

    driver.find_element(By.CSS_SELECTOR, ".form-actions a.btn-primary").click()
    wait(driver).until(lambda d: path_of(d).endswith("/create-clan"))


def test_sidebar_shows_clan_identity_and_info(driver, base_url, clan):
    d = open_my_clan(driver, base_url, OWNER)

    assert d.title == f"{clan['name']} [{clan['tag']}] · Clanforge"
    assert text_of(d.find_element(By.CSS_SELECTOR, "h1.clan-name")) == clan["name"]
    assert text_of(d.find_element(By.CSS_SELECTOR, ".clan-tag")) == f"[{clan['tag']}]"
    assert text_of(d.find_element(By.CSS_SELECTOR, ".founder-name")) == OWNER

    stats = {
        text_of(s.find_element(By.CSS_SELECTOR, ".stat-label")): text_of(s.find_element(By.CSS_SELECTOR, ".stat-value"))
        for s in d.find_elements(By.CSS_SELECTOR, ".clan-id .stat")
    }
    assert stats["Members"] == "2"
    assert stats["Founded"] == str(datetime.now().year)

    info = [text_of(p) for p in d.find_elements(By.CSS_SELECTOR, ".clan-id .clan-desc")]
    assert info == [DESCRIPTION, REGION, "Public, open to join"]

    # "My Clan" is the active item in the top navigation.
    active = d.find_element(By.CSS_SELECTOR, ".primary-nav a[aria-current='page']")
    assert text_of(active) == "My Clan"


def test_roster_lists_every_member_with_their_role(driver, base_url, clan):
    d = open_my_clan(driver, base_url, OWNER)

    rows = d.find_elements(By.CSS_SELECTOR, ".member-row")
    roles = {text_of(r.find_element(By.CSS_SELECTOR, ".member-name")):
             text_of(r.find_element(By.CSS_SELECTOR, ".member-role")) for r in rows}
    assert roles == {OWNER: "Owner", MEMBER: "Member"}
    assert member_count_label(d) == "2"

    owner_avatar = row_for(d, OWNER).find_element(By.CSS_SELECTOR, ".avatar")
    assert "is-owner" in owner_avatar.get_attribute("class")
    assert text_of(owner_avatar) == "AD"  # first two letters of the display name


def test_member_search_filters_roster_and_clear_resets_it(driver, base_url, clan):
    d = open_my_clan(driver, base_url, OWNER)
    search = d.find_element(By.ID, "memberSearch")
    empty = d.find_element(By.ID, "emptyState")
    assert not empty.is_displayed()

    search.send_keys("PLA")  # case-insensitive, partial match
    names = [r.get_attribute("data-name") for r in visible_rows(d)]
    assert names == [MEMBER]
    assert member_count_label(d) == "1"

    search.clear()
    search.send_keys("nobody-by-this-name")
    assert visible_rows(d) == []
    assert empty.is_displayed()
    assert "No members match your search." in text_of(empty)

    d.find_element(By.ID, "clearFilters").click()
    assert search.get_attribute("value") == ""
    assert len(visible_rows(d)) == 2
    assert member_count_label(d) == "2"
    assert not empty.is_displayed()
    # Focus returns to the search box.
    assert d.execute_script("return document.activeElement.id") == "memberSearch"


def test_role_filter_shows_only_the_selected_role(driver, base_url, clan):
    d = open_my_clan(driver, base_url, OWNER)
    role_filter = Select(d.find_element(By.ID, "roleFilter"))
    assert [text_of(o) for o in role_filter.options] == ["All Roles", "Owner", "Moderator", "Member"]

    role_filter.select_by_value("owner")
    assert [r.get_attribute("data-name") for r in visible_rows(d)] == [OWNER]

    role_filter.select_by_value("member")
    assert [r.get_attribute("data-name") for r in visible_rows(d)] == [MEMBER]

    role_filter.select_by_value("moderator")
    assert visible_rows(d) == []
    assert d.find_element(By.ID, "emptyState").is_displayed()

    role_filter.select_by_value("all")
    assert len(visible_rows(d)) == 2


def test_owner_sees_management_controls(driver, base_url, clan):
    d = open_my_clan(driver, base_url, OWNER)

    titles = [text_of(t) for t in d.find_elements(By.CSS_SELECTOR, ".mgmt-link-title")]
    assert titles == ["Clan Settings", "Invitations"]
    assert d.find_element(By.LINK_TEXT, "Manage Invites").get_attribute("href").endswith("/invitations")

    # Actions on the member's row, none on the owner's own row.
    member_actions = [text_of(b) for b in row_for(d, MEMBER).find_elements(By.CSS_SELECTOR, ".member-actions button")]
    assert member_actions == ["Make moderator", "Remove"]
    assert not row_for(d, OWNER).find_elements(By.CSS_SELECTOR, ".member-actions")

    # The owner cannot simply leave.
    assert not d.find_elements(By.CSS_SELECTOR, "form[action$='/clanhome/leave']")
    assert "transfer ownership or delete the clan" in d.page_source


def test_owner_can_promote_and_demote_a_member(driver, base_url, clan):
    d = open_my_clan(driver, base_url, OWNER)

    row_for(d, MEMBER).find_element(By.XPATH, ".//button[normalize-space()='Make moderator']").click()
    wait(d).until(lambda _: flash_notice(d) == "Role updated.")
    row = row_for(d, MEMBER)
    assert row.get_attribute("data-role") == "moderator"
    assert text_of(row.find_element(By.CSS_SELECTOR, ".member-role")) == "Moderator"
    assert [text_of(b) for b in row.find_elements(By.CSS_SELECTOR, ".member-actions button")] == \
        ["Make member", "Remove"]

    # Put it back so the other tests see an ordinary member.
    row.find_element(By.XPATH, ".//button[normalize-space()='Make member']").click()
    wait(d).until(lambda _: row_for(d, MEMBER).get_attribute("data-role") == "member")
    assert text_of(row_for(d, MEMBER).find_element(By.CSS_SELECTOR, ".member-role")) == "Member"


def test_regular_member_has_no_management_controls(driver, base_url, clan):
    d = open_my_clan(driver, base_url, MEMBER)

    assert text_of(d.find_element(By.CSS_SELECTOR, "h1.clan-name")) == clan["name"]
    assert not d.find_elements(By.CSS_SELECTOR, ".mgmt-link")
    assert not d.find_elements(By.LINK_TEXT, "Manage Invites")
    assert not d.find_elements(By.CSS_SELECTOR, ".member-actions")
    assert not d.find_elements(By.CSS_SELECTOR, ".panel-header-action")  # no edit pencils

    membership = d.find_element(By.XPATH, "//p[contains(@class,'panel-title')][contains(.,'Your Membership')]/..")
    assert text_of(membership.find_element(By.CSS_SELECTOR, ".motd-text")).startswith("Member since ")
    assert membership.find_element(By.XPATH, ".//button[normalize-space()='Leave Clan']").is_displayed()


def test_cancelling_remove_confirmation_keeps_the_member(driver, base_url, clan):
    d = open_my_clan(driver, base_url, OWNER)

    remove = row_for(d, MEMBER).find_element(By.XPATH, ".//button[normalize-space()='Remove']")
    prompt = click_expecting_confirm(d, remove)
    assert prompt.text == f"Remove {MEMBER} from the clan?"
    prompt.dismiss()

    # Nothing was posted: still on the page, both members still listed.
    assert path_of(d).endswith("/clanhome")
    assert flash_notice(d) is None
    d.refresh()
    assert len(d.find_elements(By.CSS_SELECTOR, ".member-row")) == 2


def test_member_can_leave_the_clan(driver, base_url, clan):
    d = open_my_clan(driver, base_url, MEMBER)
    try:
        prompt = click_expecting_confirm(d, d.find_element(By.XPATH, "//button[normalize-space()='Leave Clan']"))
        assert prompt.text == "Leave this clan?"
        prompt.accept()

        wait(d).until(lambda _: path_of(d).endswith("/clans"))
        assert flash_notice(d) == "You have left the clan."
        d.get(base_url + "/clanhome")
        assert text_of(d.find_element(By.CSS_SELECTOR, ".success-title")) == "You are not in a clan yet"

        # The owner now sees a roster of one.
        open_my_clan(d, base_url, OWNER)
        assert [r.get_attribute("data-name") for r in d.find_elements(By.CSS_SELECTOR, ".member-row")] == [OWNER]
    finally:
        # Rejoin so the module's clan is back to two members.
        login(d, base_url, MEMBER)
        join_clan_by_name(d, base_url, clan["name"])
