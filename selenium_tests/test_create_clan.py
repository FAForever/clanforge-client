"""Selenium tests for the Create Clan page (/create-clan).

Covers the live preview driven by clan-form.js, the browser-side tag rule,
the image picker, HTML5 required fields, the server-side refusals from
ClanManagementService, and a successful creation.

Accounts used (dev profile):
    guest  - the person creating clans; reset to "no clan" before each test
    admin  - owns an existing clan, used for the duplicate-name/tag and
             "already in a clan" refusals
"""

import base64

import pytest
from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import Select

from helpers import (
    text_of,
    create_clan,
    ensure_not_in_clan,
    fill_create_form,
    flash_error,
    flash_notice,
    is_in_clan,
    login,
    path_of,
    random_tag,
    unique_clan_name,
    wait,
)

CREATOR = "guest"
EXPECTED_REGIONS = ["EU/NA", "EU", "NA", "Asia", "Oceania", "Global"]

# A valid 1x1 PNG, for the image picker test.
TINY_PNG = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
)


@pytest.fixture(scope="module")
def existing_clan(driver, base_url):
    """A clan owned by admin, so its name and tag are already taken."""
    login(driver, base_url, "admin")
    if is_in_clan(driver, base_url):
        name = text_of(driver.find_element(By.CSS_SELECTOR, ".clan-name")).strip()
        tag = text_of(driver.find_element(By.CSS_SELECTOR, ".clan-tag")).strip().strip("[]")
    else:
        name, tag = create_clan(driver, base_url)
    driver.delete_all_cookies()
    return {"name": name, "tag": tag}


@pytest.fixture
def create_page(driver, base_url):
    """Signed in as a user with no clan, on the Create Clan page."""
    login(driver, base_url, CREATOR)
    ensure_not_in_clan(driver, base_url)
    driver.get(base_url + "/create-clan")
    wait(driver).until(EC.presence_of_element_located((By.ID, "clanName")))
    return driver


def preview_text(driver, element_id):
    return text_of(driver.find_element(By.ID, element_id)).strip()


# --------------------------------------------------------------------------

def test_page_loads_with_all_form_fields(create_page):
    d = create_page
    assert d.title == "Create Clan · Clanforge"
    assert text_of(d.find_element(By.CSS_SELECTOR, "h1.page-title")) == "Create Clan"

    for field_id in ["clanName", "clanTag", "clanDesc", "clanRegion",
                     "publicToggle", "inviteOnlyToggle", "clanImageInput", "createClanBtn"]:
        assert d.find_elements(By.ID, field_id), f"missing #{field_id}"

    assert d.find_element(By.ID, "clanName").get_attribute("maxlength") == "40"
    assert d.find_element(By.ID, "clanTag").get_attribute("maxlength") == "3"
    assert text_of(d.find_element(By.ID, "clanTagHint")) == \
        "1 to 3 letters or numbers. Shown in brackets, like [IVG]."

    region = Select(d.find_element(By.ID, "clanRegion"))
    assert [text_of(o) for o in region.options] == EXPECTED_REGIONS
    assert text_of(region.first_selected_option) == "EU/NA"

    # ClanForm defaults: public, open to join.
    assert d.find_element(By.ID, "publicToggle").is_selected()
    assert not d.find_element(By.ID, "inviteOnlyToggle").is_selected()


def test_live_preview_mirrors_name_and_description(create_page):
    d = create_page
    assert preview_text(d, "previewName") == "Your Clan Name"
    assert preview_text(d, "previewDesc") == "Your clan description will appear here."

    fill_create_form(d, name="Iron Vanguard", description="Late-night team games.")
    assert preview_text(d, "previewName") == "Iron Vanguard"
    assert preview_text(d, "previewDesc") == "Late-night team games."

    # Clearing the inputs brings the placeholders back.
    d.find_element(By.ID, "clanName").clear()
    d.find_element(By.ID, "clanName").send_keys(" ")  # clear() fires no input event
    d.find_element(By.ID, "clanDesc").clear()
    d.find_element(By.ID, "clanDesc").send_keys(" ")
    assert preview_text(d, "previewName") == "Your Clan Name"
    assert preview_text(d, "previewDesc") == "Your clan description will appear here."


def test_tag_input_keeps_only_three_letters_or_digits(create_page):
    d = create_page
    tag = d.find_element(By.ID, "clanTag")
    tag.send_keys("a-")
    tag.send_keys("!b")
    tag.send_keys(" 1")
    tag.send_keys("Z")  # a 4th valid character is dropped by maxlength
    assert tag.get_attribute("value") == "ab1"
    assert preview_text(d, "previewTag") == "[ab1]"
    assert preview_text(d, "previewBadgeText") == "ab1"


def test_public_toggle_switches_preview_between_public_and_private(create_page):
    d = create_page
    status = d.find_element(By.ID, "previewStatus")
    assert text_of(status) == "Public"
    assert "is-public" in status.get_attribute("class")

    fill_create_form(d, public=False)
    assert text_of(status) == "Private"
    assert "is-private" in status.get_attribute("class")
    assert "is-public" not in status.get_attribute("class")

    fill_create_form(d, public=True)
    assert text_of(status) == "Public"


def test_image_upload_shows_preview_and_can_be_removed(create_page, tmp_path):
    d = create_page
    png = tmp_path / "logo.png"
    png.write_bytes(TINY_PNG)

    preview_img = d.find_element(By.ID, "imagePreviewImg")
    remove_btn = d.find_element(By.ID, "removeImageBtn")
    assert not preview_img.is_displayed()
    assert not remove_btn.is_displayed()

    d.find_element(By.ID, "clanImageInput").send_keys(str(png))
    wait(d).until(lambda _: preview_img.is_displayed())
    assert preview_img.get_attribute("src").startswith("data:image/png")
    assert d.find_element(By.ID, "previewBadgeImg").is_displayed()
    assert not d.find_element(By.ID, "imagePreviewText").is_displayed()

    remove_btn.click()
    wait(d).until(lambda _: not preview_img.is_displayed())
    assert not remove_btn.is_displayed()
    assert d.find_element(By.ID, "imagePreviewText").is_displayed()
    assert d.find_element(By.ID, "clanImageInput").get_attribute("value") == ""


def test_empty_required_fields_block_submission(create_page, base_url):
    d = create_page
    d.find_element(By.ID, "createClanBtn").click()

    # The browser's required check stops the POST: still on the form, still no clan.
    assert path_of(d).endswith("/create-clan")
    name = d.find_element(By.ID, "clanName")
    assert d.execute_script("return arguments[0].validity.valueMissing;", name)
    assert not d.execute_script("return arguments[0].form.checkValidity();", name)
    assert not is_in_clan(d, base_url)


def test_blank_name_is_refused_by_server(create_page, base_url):
    d = create_page
    # Spaces satisfy the browser's `required` but the service trims them away.
    fill_create_form(d, name="   ", tag=random_tag(), description="Keep this text")
    d.find_element(By.ID, "createClanBtn").click()

    wait(d).until(lambda _: flash_error(d))
    assert flash_error(d) == "Give the clan a name."
    assert path_of(d).endswith("/create-clan")
    # The refused form comes back with what was typed.
    assert d.find_element(By.ID, "clanDesc").get_attribute("value") == "Keep this text"
    assert not is_in_clan(d, base_url)


@pytest.mark.parametrize("taken", ["name", "tag"])
def test_duplicate_name_or_tag_is_refused(create_page, existing_clan, taken):
    d = create_page
    if taken == "name":
        # Names are compared case-insensitively.
        name, tag = existing_clan["name"].upper(), random_tag()
        expected = f"The clan name '{name}' is already in use."
    else:
        name, tag = unique_clan_name(), existing_clan["tag"].lower()
        expected = f"The clan tag '{tag}' is already in use."

    fill_create_form(d, name=name, tag=tag)
    d.find_element(By.ID, "createClanBtn").click()
    wait(d).until(lambda _: flash_error(d))

    err = flash_error(d)
    if taken == "tag" and err.startswith("The clan name"):
        pytest.skip("random name collided, rerun")
    if taken == "name" and err.startswith("The clan tag"):
        pytest.skip("random tag collided, rerun")
    assert err == expected
    assert d.find_element(By.ID, "clanName").get_attribute("value") == name


def test_user_already_in_a_clan_cannot_create_another(driver, base_url, existing_clan):
    login(driver, base_url, "admin")
    driver.get(base_url + "/create-clan")
    fill_create_form(driver, name=unique_clan_name(), tag=random_tag())
    driver.find_element(By.ID, "createClanBtn").click()

    wait(driver).until(lambda d: flash_error(d))
    assert flash_error(driver) == "You are already in a clan. Leave it before creating another."
    # Still the owner of the original clan.
    driver.get(base_url + "/clanhome")
    assert text_of(driver.find_element(By.CSS_SELECTOR, ".clan-name")) == existing_clan["name"]


def test_successful_creation_lands_on_my_clan(create_page, base_url):
    d = create_page
    name = unique_clan_name()
    # A tag can collide with an existing clan, so let the helper retry.
    name, tag = create_clan(
        d, base_url, name=name, description="Selenium created this clan.",
        region="Oceania", public=True, invite_only=True,
    )

    assert path_of(d).endswith("/clanhome")
    assert flash_notice(d) == "Your clan is live."
    assert text_of(d.find_element(By.CSS_SELECTOR, ".clan-name")) == name
    assert text_of(d.find_element(By.CSS_SELECTOR, ".clan-tag")) == f"[{tag}]"
    assert d.title == f"{name} [{tag}] · Clanforge"

    info = [text_of(p) for p in d.find_elements(By.CSS_SELECTOR, ".clan-id .clan-desc")]
    assert "Selenium created this clan." in info
    assert "Oceania" in info
    assert "Public, joins by invitation" in info

    # The creator is the owner and the only member.
    rows = d.find_elements(By.CSS_SELECTOR, ".member-row")
    assert len(rows) == 1
    assert text_of(rows[0].find_element(By.CSS_SELECTOR, ".member-role")) == "Owner"
    # The top bar no longer offers Create Clan.
    assert not d.find_elements(By.CSS_SELECTOR, ".topbar a.btn-create")

    ensure_not_in_clan(d, base_url)  # tidy up: frees the name and tag again
