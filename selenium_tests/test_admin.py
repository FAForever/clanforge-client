"""Project Acceptance Test E - Administration / moderation interface (web UI).

The admin development account moderates a clan created by the guest account, then every
action is checked by the state it actually leaves on the clan's dossier - the "Lift
Suspension" form appearing, the new name in the title, the new tag in the badge, the
"Clan Deleted" panel - and by the moderation-history timeline, rather than by a loose
substring. A no-op, a refusal or a page that merely re-rendered therefore cannot pass.

Run the app with the dev profile (for the admin/player/guest accounts) and:
    pytest selenium_tests/test_admin.py
"""

import random
import string
from urllib.parse import quote

from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC

from helpers import (
    wait,
    login,
    path_of,
    create_clan,
    ensure_not_in_clan,
    click_expecting_confirm,
)

clan_name = None
clan_tag = None
renamed_clan_name = None
new_clan_tag = None


def _random_tag():
    return "".join(random.choice(string.ascii_uppercase + string.digits) for _ in range(3))


def open_admin_clan(driver, base_url, name):
    """Sign in as admin and open the dossier of the clan called `name`.

    The admin search is a plain GET form (name="q") with no live filtering, so the query
    must be submitted - here by loading /admin/clans?q=<name> directly. The original test
    only typed into the box and never submitted, so the new clan was never on the first
    page of the ~1,500-clan list and the link was never found.
    """
    login(driver, base_url, "admin")
    driver.get(base_url + "/admin/clans?q=" + quote(name))
    link = wait(driver).until(
        EC.element_to_be_clickable((By.XPATH, f"//a[contains(normalize-space(), '{name}')]"))
    )
    link.click()
    wait(driver).until(lambda d: "/admin/clans/" in path_of(d))


def test_01_non_admin_cannot_access_admin_page(driver, base_url):
    login(driver, base_url, "player")
    driver.get(base_url + "/admin/clans")

    # a non-admin is refused (403) and never reaches the admin console
    assert len(driver.find_elements(By.ID, "adminSearch")) == 0
    assert "403" in driver.page_source or "forbidden" in driver.page_source.lower()


def test_02_create_test_clan(driver, base_url):
    global clan_name, clan_tag

    login(driver, base_url, "guest")
    # guest may still be in a clan from an earlier interrupted run; leave it first,
    # or create() would refuse with "You are already in a clan."
    ensure_not_in_clan(driver, base_url)

    clan_name, clan_tag = create_clan(driver, base_url)

    assert clan_name
    assert clan_tag
    assert path_of(driver).endswith("/clanhome")


def test_03_admin_can_find_and_suspend_clan(driver, base_url):
    assert clan_name is not None

    open_admin_clan(driver, base_url, clan_name)

    reason = wait(driver).until(EC.presence_of_element_located((By.ID, "suspendReason")))
    reason.clear()
    reason.send_keys("Offensive clan content")

    suspend_btn = wait(driver).until(
        EC.element_to_be_clickable((By.XPATH, "//button[contains(normalize-space(), 'Suspend Clan')]"))
    )
    click_expecting_confirm(driver, suspend_btn).accept()

    # success: the dossier now offers "Lift Suspension", which renders only while the clan
    # is suspended, and the success flash is shown. ("suspended" alone is on the page even
    # before the action, so it would pass vacuously.)
    wait(driver).until(
        EC.presence_of_element_located((By.XPATH, "//button[contains(normalize-space(), 'Lift Suspension')]"))
    )
    assert "Clan suspended" in driver.page_source


def test_04_suspend_is_recorded_in_history(driver, base_url):
    assert clan_name is not None

    open_admin_clan(driver, base_url, clan_name)

    timeline = wait(driver).until(EC.presence_of_element_located((By.CSS_SELECTOR, ".timeline")))
    assert "suspend" in timeline.text.lower()


def test_05_admin_can_rename_clan(driver, base_url):
    global renamed_clan_name

    assert clan_name is not None

    open_admin_clan(driver, base_url, clan_name)

    renamed_clan_name = f"Moderated {clan_name}"

    name_input = wait(driver).until(EC.presence_of_element_located((By.ID, "renameName")))
    name_input.clear()
    name_input.send_keys(renamed_clan_name)

    reason = driver.find_element(By.ID, "renameReason")
    reason.clear()
    reason.send_keys("Removing inappropriate clan name")

    wait(driver).until(
        EC.element_to_be_clickable((By.XPATH, "//button[contains(normalize-space(), 'Rename Clan')]"))
    ).click()

    # success: the dossier title is the new name, and the success flash is shown
    wait(driver).until(
        lambda d: d.find_element(By.CSS_SELECTOR, ".dossier-title").text.strip() == renamed_clan_name
    )
    assert "Clan renamed" in driver.page_source


def test_06_rename_is_recorded_in_history(driver, base_url):
    assert renamed_clan_name is not None

    open_admin_clan(driver, base_url, renamed_clan_name)

    timeline = wait(driver).until(EC.presence_of_element_located((By.CSS_SELECTOR, ".timeline")))
    assert "rename" in timeline.text.lower()


def test_07_admin_can_modify_clan_tag(driver, base_url):
    global new_clan_tag

    assert renamed_clan_name is not None

    # a random tag, retried on the off chance the first is already taken in the ~1,500-clan
    # dataset (the old test hard-coded "TST", which both collided and was echoed back by the
    # "already in use" error, so the substring check passed even when the retag failed).
    for _ in range(6):
        open_admin_clan(driver, base_url, renamed_clan_name)
        candidate = _random_tag()

        tag_input = wait(driver).until(EC.presence_of_element_located((By.ID, "retagTag")))
        tag_input.clear()
        tag_input.send_keys(candidate)

        reason = driver.find_element(By.ID, "retagReason")
        reason.clear()
        reason.send_keys("Replacing inappropriate clan tag")

        wait(driver).until(
            EC.element_to_be_clickable((By.XPATH, "//button[contains(normalize-space(), 'Change Tag')]"))
        ).click()
        wait(driver).until(
            lambda d: "Clan tag changed" in d.page_source or "already in use" in d.page_source
        )
        if "Clan tag changed" in driver.page_source:
            new_clan_tag = candidate
            break

    assert new_clan_tag is not None, "retag did not succeed with any candidate tag"
    badge = driver.find_element(By.CSS_SELECTOR, ".dossier-badge")
    assert badge.text.strip().upper() == new_clan_tag


def test_08_retag_is_recorded_in_history(driver, base_url):
    assert renamed_clan_name is not None

    open_admin_clan(driver, base_url, renamed_clan_name)

    timeline = wait(driver).until(EC.presence_of_element_located((By.CSS_SELECTOR, ".timeline")))
    assert "retag" in timeline.text.lower()


def test_09_admin_can_view_reports_queue(driver, base_url):
    login(driver, base_url, "admin")
    driver.get(base_url + "/admin/reports")

    wait(driver).until(lambda d: path_of(d).endswith("/admin/reports"))
    assert "Report Queue" in driver.page_source


def test_10_admin_can_delete_clan(driver, base_url):
    assert renamed_clan_name is not None

    open_admin_clan(driver, base_url, renamed_clan_name)

    reason = wait(driver).until(EC.presence_of_element_located((By.ID, "deleteReason")))
    reason.clear()
    reason.send_keys("Clan removed after moderation")

    delete_btn = wait(driver).until(
        EC.element_to_be_clickable((By.XPATH, "//button[contains(normalize-space(), 'Delete Clan')]"))
    )
    click_expecting_confirm(driver, delete_btn).accept()

    # success: the dossier now shows the deleted-state panel ("Clan Deleted", rendered only
    # once the clan is deleted) and the success flash. The old test only checked that the URL
    # still contained /admin/clans, which is true on success AND on a refusal.
    wait(driver).until(lambda d: "Clan deleted" in d.page_source)
    assert "Clan Deleted" in driver.page_source
