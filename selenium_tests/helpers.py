"""Shared helpers for the Clanforge Selenium tests.

These drive the real UI the same way a person would: sign in through the
login form, click the buttons on the pages and accept the confirm() dialogs
that site.js raises before a leave, remove or delete.

The dev profile provides three accounts (admin/admin, player/player,
guest/guest). The database is shared between test runs, so every test that
needs a particular starting state (in a clan / not in a clan) sets that state
up itself with the helpers below instead of assuming it.
"""

import logging
import random
import time
import string
import uuid

from selenium.common.exceptions import (
    NoAlertPresentException,
    UnexpectedAlertPresentException,
    NoSuchElementException,
    StaleElementReferenceException,
    TimeoutException,
)
from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import WebDriverWait

DEFAULT_WAIT = 10

# The tag input only accepts letters and digits, 1 to 3 of them
# (clan.tag.pattern in application.properties).
TAG_ALPHABET = string.ascii_uppercase + string.digits


def wait(driver, timeout=DEFAULT_WAIT):
    # While a form post redirects, elements from the old page go stale or
    # disappear; keep polling instead of failing on the first try.
    return WebDriverWait(
        driver, timeout,
        ignored_exceptions=(NoSuchElementException, StaleElementReferenceException),
    )


def path_of(driver):
    """The current URL's path, without scheme, host or query string."""
    from urllib.parse import urlparse

    return urlparse(driver.current_url).path


def unique_clan_name(prefix="Selenium"):
    # 40 characters is the maximum clan name length.
    return f"{prefix} {uuid.uuid4().hex[:10]}"[:40]


def random_tag():
    return "".join(random.choice(TAG_ALPHABET) for _ in range(3))


def accept_confirm(driver, timeout=8):
    """Accept the window.confirm() that site.js shows for data-confirm forms."""
    wait(driver, timeout).until(EC.alert_is_present())
    driver.switch_to.alert.accept()


def dismiss_confirm(driver, timeout=8):
    wait(driver, timeout).until(EC.alert_is_present())
    driver.switch_to.alert.dismiss()


def text_of(element):
    """The element's text as written in the page, with whitespace collapsed.

    Selenium's `.text` returns text as rendered, so it is upper-cased wherever
    styles.css applies `text-transform: uppercase` (role labels, stat labels,
    status badges...) and empty while an element is still fading in. The
    tests check content, so they read the DOM text instead; visibility is
    checked separately with `is_displayed()`.
    """
    return " ".join((element.get_attribute("textContent") or "").split())


def flash_notice(driver):
    els = driver.find_elements(By.CSS_SELECTOR, ".flash.flash-notice")
    return text_of(els[0]) if els else None


def flash_error(driver):
    """The refusal sentence, whether it arrives as a flash or an inline form error."""
    els = driver.find_elements(By.CSS_SELECTOR, ".flash.flash-error, .field-hint.is-error")
    return text_of(els[0]) if els else None


# --------------------------------------------------------------------------
# Signing in and out
# --------------------------------------------------------------------------

def logout(driver, base_url):
    """Drop the session. Clearing cookies is enough to sign out cleanly."""
    driver.get(base_url + "/login")
    driver.delete_all_cookies()


def login(driver, base_url, username, password=None):
    """Sign in through the development login form on /login.

    The form sits inside the animated login popup, so the fields may not be
    interactable until the popup opens. Filling them through the DOM keeps
    the helper independent of that animation; the POST (with its CSRF token)
    is still the real form submission.
    """
    password = password or username
    logout(driver, base_url)
    driver.get(base_url + "/login")
    form = wait(driver).until(
        EC.presence_of_element_located((By.CSS_SELECTOR, "form[action$='/login']"))
    )
    driver.execute_script(
        """
        const f = arguments[0];
        f.querySelector("input[name='username']").value = arguments[1];
        f.querySelector("input[name='password']").value = arguments[2];
        f.submit();
        """,
        form,
        username,
        password,
    )
    wait(driver).until(lambda d: "/login" not in path_of(d))


# --------------------------------------------------------------------------
# Clan state
# --------------------------------------------------------------------------

def is_in_clan(driver, base_url):
    driver.get(base_url + "/clanhome")
    wait(driver).until(EC.presence_of_element_located((By.TAG_NAME, "main")))
    return not driver.find_elements(By.CSS_SELECTOR, "main.success-wrap")


class ConfirmPrompt:
    """The confirm() a data-confirm form raised after a click.

    `native` is True when Chrome showed a real dialog. When the click had to
    be made from JavaScript (see resilient.py), Chrome refuses to show the
    dialog because no real user action triggered it, and confirm() returns
    false straight away. The page still called it, so `text` is the message
    the page asked with, recorded by a wrapper around window.confirm.
    """

    def __init__(self, driver, button, text, native):
        self.driver, self.button, self.text, self.native = driver, button, text, native

    def accept(self):
        if self.native:
            self.driver.switch_to.alert.accept()
        else:
            # Answer "OK" this time and click again: the form's own submit
            # handler runs and posts, exactly as after a real OK.
            self.driver.execute_script(
                "window.confirm = () => true; arguments[0].click();", self.button
            )

    def dismiss(self):
        if self.native:
            self.driver.switch_to.alert.dismiss()
        # Otherwise confirm() already returned false, so the form was not posted.


_RECORD_CONFIRMS = """
window.__confirms = [];
if (!window.__confirmWrapped) {
  window.__confirmWrapped = true;
  const original = window.confirm;
  window.confirm = function (msg) { window.__confirms.push(String(msg)); return original.call(window, msg); };
}
"""


def click_expecting_confirm(driver, button, timeout=8):
    """Click `button` and return the ConfirmPrompt its form raises."""
    driver.execute_script(_RECORD_CONFIRMS)
    button.click()
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        try:
            return ConfirmPrompt(driver, button, driver.switch_to.alert.text, native=True)
        except NoAlertPresentException:
            pass
        try:
            asked = driver.execute_script("return window.__confirms || [];")
        except UnexpectedAlertPresentException:
            continue  # a real dialog opened in between; read it next loop
        if asked:
            return ConfirmPrompt(driver, button, asked[-1], native=False)
        time.sleep(0.2)
    raise AssertionError("clicking the button did not ask for confirmation")


def _submit_confirmed_form(driver, button):
    """Leave/delete during setup and teardown: click, then confirm."""
    click_expecting_confirm(driver, button).accept()


def ensure_not_in_clan(driver, base_url):
    """Leave (member/moderator) or delete (owner) whatever clan the user is in."""
    if not is_in_clan(driver, base_url):
        return
    leave_buttons = driver.find_elements(
        By.CSS_SELECTOR, "form[action$='/clanhome/leave'] button[type='submit']"
    )
    if leave_buttons:
        _submit_confirmed_form(driver, leave_buttons[0])
    else:
        # The owner cannot leave; the clan is deleted from Settings instead.
        driver.get(base_url + "/settings")
        _submit_confirmed_form(driver, wait(driver).until(EC.element_to_be_clickable((By.ID, "disbandBtn"))))
    # Both leaving and deleting redirect to Browse Clans once they've worked.
    wait(driver).until(lambda d: path_of(d).rstrip("/").endswith("/clans"))
    assert not is_in_clan(driver, base_url), "could not reset the user to 'no clan'"


def fill_create_form(driver, name=None, tag=None, description=None, region=None,
                     public=None, invite_only=None):
    """Fill the Create Clan form. Only the arguments given are touched."""
    if name is not None:
        el = driver.find_element(By.ID, "clanName")
        el.clear()
        el.send_keys(name)
    if tag is not None:
        el = driver.find_element(By.ID, "clanTag")
        el.clear()
        el.send_keys(tag)
    if description is not None:
        el = driver.find_element(By.ID, "clanDesc")
        el.clear()
        el.send_keys(description)
    if region is not None:
        from selenium.webdriver.support.ui import Select

        Select(driver.find_element(By.ID, "clanRegion")).select_by_visible_text(region)
    if public is not None:
        set_toggle(driver, "publicToggle", public)
    if invite_only is not None:
        set_toggle(driver, "inviteOnlyToggle", invite_only)


def set_toggle(driver, checkbox_id, on):
    """The checkboxes are visually hidden inside a styled label, so click the label."""
    box = driver.find_element(By.ID, checkbox_id)
    if box.is_selected() != on:
        box.find_element(By.XPATH, "./ancestor::label[contains(@class,'toggle-switch')]").click()
    wait(driver, 3).until(lambda _: box.is_selected() == on)


def create_clan(driver, base_url, name=None, description="Created by the Selenium suite.",
                region="EU/NA", public=True, invite_only=False, attempts=6):
    """Create a clan as the signed-in user and return (name, tag).

    The tag is random and may collide with an existing clan (the real FAF
    dump has ~1,500), so a tag refusal is retried with a fresh tag.
    """
    name = name or unique_clan_name()
    for _ in range(attempts):
        tag = random_tag()
        driver.get(base_url + "/create-clan")
        wait(driver).until(EC.presence_of_element_located((By.ID, "clanName")))
        fill_create_form(driver, name=name, tag=tag, description=description,
                         region=region, public=public, invite_only=invite_only)
        driver.find_element(By.ID, "createClanBtn").click()
        try:
            wait(driver).until(lambda d: path_of(d).endswith("/clanhome"))
            return name, tag
        except TimeoutException:
            err = flash_error(driver) or ""
            if "tag" in err.lower() and "already in use" in err.lower():
                continue
            raise AssertionError(f"creating clan failed: {err!r}")
    raise AssertionError("could not find a free clan tag")


def join_clan_by_name(driver, base_url, clan_name):
    """Find a public, open clan on Browse Clans and press Join."""
    from urllib.parse import quote

    driver.get(f"{base_url}/clans?q={quote(clan_name)}")
    view = wait(driver).until(EC.element_to_be_clickable((
        By.XPATH,
        f"//article[.//*[contains(@class,'clan-card-name') and normalize-space()='{clan_name}']]"
        "//a[contains(normalize-space(),'View Clan')]",
    )))
    view.click()
    wait(driver).until(EC.element_to_be_clickable(
        (By.CSS_SELECTOR, "form[action$='/join'] button[type='submit']")
    )).click()
    wait(driver).until(lambda d: path_of(d).endswith("/clanhome"))


def dismiss_any_alert(driver):
    try:
        driver.switch_to.alert.dismiss()
    except NoAlertPresentException:
        pass
