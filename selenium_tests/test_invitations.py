"""Selenium tests for the Invitations page (/invitations).

  * the shared setup (free player, fresh clan for admin, send an invite)
    lives in one fixture and one helper instead of being repeated per test.
  * Accept / Decline / Revoke press the button in the row for THIS test's
    clan or player, not the first matching button on the page.
  * each action checks the app's confirmation message, and decline/revoke
    are checked from both sides (admin's sent list and player's inbox).
  * admin and player are taken out of their clans again at the end.

Accounts (dev profile): admin owns a fresh clan and sends invites; player
receives them. player must be in no clan, or the invite cannot be accepted.
"""

import pytest
from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC

from helpers import (
    create_clan,
    ensure_not_in_clan,
    flash_error,
    flash_notice,
    login,
    path_of,
    text_of,
    wait,
)

MANAGER, INVITEE = "admin", "player"


# --------------------------------------------------------------------------
# Helpers
# --------------------------------------------------------------------------

def open_invitations(driver, base_url):
    driver.get(base_url + "/invitations")
    wait(driver).until(EC.presence_of_element_located((By.ID, "inboxList")))


def row_in(driver, list_id, text):
    """The invite row in #inboxList / #sentList whose name contains `text`, or None."""
    for row in driver.find_elements(By.CSS_SELECTOR, f"#{list_id} .invite-row"):
        if text.lower() in text_of(row.find_element(By.CSS_SELECTOR, ".invite-row-name")).lower():
            return row
    return None


def send_invite(driver, base_url, username):
    """As the signed-in manager, invite `username` through the form."""
    open_invitations(driver, base_url)
    box = wait(driver).until(EC.presence_of_element_located((By.ID, "inviteUsername")))
    box.send_keys(username)
    driver.find_element(By.CSS_SELECTOR, ".invite-form button[type='submit']").click()
    wait(driver).until(lambda d: flash_notice(d) or flash_error(d))
    assert flash_error(driver) is None, flash_error(driver)
    return flash_notice(driver)


@pytest.fixture
def invite_clan(driver, base_url):
    """A fresh clan owned by admin, with player in no clan. Returns its name."""
    login(driver, base_url, INVITEE)
    ensure_not_in_clan(driver, base_url)
    login(driver, base_url, MANAGER)
    ensure_not_in_clan(driver, base_url)
    name, _tag = create_clan(driver, base_url, description="Clan used for Selenium invitation testing.")
    return name


@pytest.fixture(scope="module", autouse=True)
def _tidy_up(driver, base_url):
    yield
    for user in (INVITEE, MANAGER):
        try:
            login(driver, base_url, user)
            ensure_not_in_clan(driver, base_url)
        except Exception:  # noqa: BLE001 - cleanup must not hide results
            pass
    driver.delete_all_cookies()


# --------------------------------------------------------------------------
# Tests
# --------------------------------------------------------------------------

def test_manager_can_send_invitation(driver, base_url, invite_clan):
    notice = send_invite(driver, base_url, INVITEE)
    assert notice == f"Invite sent to {INVITEE}."

    row = row_in(driver, "sentList", INVITEE)
    assert row is not None, "player should be listed under Invites Sent"
    assert text_of(row.find_element(By.CSS_SELECTOR, ".invite-status")) == "Pending"
    assert text_of(row.find_element(By.CSS_SELECTOR, ".invite-row-meta")).startswith("Invited as Member")


def test_recipient_can_see_invitation(driver, base_url, invite_clan):
    send_invite(driver, base_url, INVITEE)

    login(driver, base_url, INVITEE)
    open_invitations(driver, base_url)
    row = row_in(driver, "inboxList", invite_clan)
    assert row is not None, f"{invite_clan} should be in player's inbox"
    assert f"Invited by {MANAGER} as Member" in text_of(row.find_element(By.CSS_SELECTOR, ".invite-row-meta"))
    assert row.find_elements(By.CSS_SELECTOR, "form[action$='/accept'] button")
    assert row.find_elements(By.CSS_SELECTOR, "form[action$='/decline'] button")


def test_recipient_can_decline_invitation(driver, base_url, invite_clan):
    send_invite(driver, base_url, INVITEE)

    login(driver, base_url, INVITEE)
    open_invitations(driver, base_url)
    row = row_in(driver, "inboxList", invite_clan)
    assert row is not None
    row.find_element(By.CSS_SELECTOR, "form[action$='/decline'] button").click()

    wait(driver).until(lambda d: flash_notice(d) == "Invitation declined.")
    assert row_in(driver, "inboxList", invite_clan) is None
    # The manager no longer sees it as pending either.
    login(driver, base_url, MANAGER)
    open_invitations(driver, base_url)
    assert row_in(driver, "sentList", INVITEE) is None


def test_manager_can_revoke_invitation(driver, base_url, invite_clan):
    send_invite(driver, base_url, INVITEE)

    row = row_in(driver, "sentList", INVITEE)
    assert row is not None
    row.find_element(By.CSS_SELECTOR, "form[action$='/revoke'] button").click()

    wait(driver).until(lambda d: flash_notice(d) == "Invitation revoked.")
    assert row_in(driver, "sentList", INVITEE) is None
    # It has gone from the player's inbox too.
    login(driver, base_url, INVITEE)
    open_invitations(driver, base_url)
    assert row_in(driver, "inboxList", invite_clan) is None


def test_recipient_can_accept_invitation(driver, base_url, invite_clan):
    send_invite(driver, base_url, INVITEE)

    login(driver, base_url, INVITEE)
    open_invitations(driver, base_url)
    row = row_in(driver, "inboxList", invite_clan)
    assert row is not None
    row.find_element(By.CSS_SELECTOR, "form[action$='/accept'] button").click()

    # InvitationController sends an accepted invite to My Clan.
    wait(driver).until(lambda d: path_of(d) == "/clanhome")
    assert flash_notice(driver) == f"Welcome to {invite_clan}."
    assert text_of(driver.find_element(By.CSS_SELECTOR, ".clan-name")) == invite_clan
    me_row = driver.find_element(By.CSS_SELECTOR, f".member-row[data-name='{INVITEE}']")
    assert text_of(me_row.find_element(By.CSS_SELECTOR, ".member-role")) == "Member"
