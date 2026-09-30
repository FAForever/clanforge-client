from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC

from helpers import (
    wait,
    login,
    ensure_not_in_clan,
    create_clan,
    text_of,
    path_of,
)


# ---------------------------------------------------------
# Test 1: Manager can send an invitation
# ---------------------------------------------------------

def test_manager_can_send_invitation(driver, base_url):
    # Make sure player is available to receive an invitation.
    login(driver, base_url, "player")
    ensure_not_in_clan(driver, base_url)

    # Make sure admin can create a fresh clan.
    login(driver, base_url, "admin")
    ensure_not_in_clan(driver, base_url)

    clan_name, clan_tag = create_clan(
        driver,
        base_url,
        description="Clan used for Selenium invitation testing."
    )

    # Open Invitations page.
    driver.get(base_url + "/invitations")

    # Enter the player username.
    username = wait(driver).until(
        EC.presence_of_element_located((By.ID, "inviteUsername"))
    )
    username.send_keys("player")

    # Send the invitation.
    driver.find_element(
        By.CSS_SELECTOR,
        ".invite-form button[type='submit']"
    ).click()

    # Check the sent list.
    sent_list = wait(driver).until(
        EC.presence_of_element_located((By.ID, "sentList"))
    )

    wait(driver).until(
        lambda d: "player" in text_of(sent_list).lower()
    )

    assert "player" in text_of(sent_list).lower()


# ---------------------------------------------------------
# Test 2: Recipient can see an invitation
# ---------------------------------------------------------

def test_recipient_can_see_invitation(driver, base_url):
    # Player must not already be in a clan.
    login(driver, base_url, "player")
    ensure_not_in_clan(driver, base_url)

    # Admin creates a fresh clan.
    login(driver, base_url, "admin")
    ensure_not_in_clan(driver, base_url)

    clan_name, clan_tag = create_clan(
        driver,
        base_url,
        description="Clan used for Selenium invitation testing."
    )

    # Admin sends an invitation to player.
    driver.get(base_url + "/invitations")

    username = wait(driver).until(
        EC.presence_of_element_located((By.ID, "inviteUsername"))
    )
    username.send_keys("player")

    driver.find_element(
        By.CSS_SELECTOR,
        ".invite-form button[type='submit']"
    ).click()

    # Switch to player.
    login(driver, base_url, "player")
    driver.get(base_url + "/invitations")

    inbox = wait(driver).until(
        EC.presence_of_element_located((By.ID, "inboxList"))
    )

    # The clan that invited player should appear in the inbox.
    wait(driver).until(
        lambda d: clan_name in text_of(inbox)
    )

    assert clan_name in text_of(inbox)


# ---------------------------------------------------------
# Test 3: Recipient can decline an invitation
# ---------------------------------------------------------

def test_recipient_can_decline_invitation(driver, base_url):
    # Player must be free to receive an invitation.
    login(driver, base_url, "player")
    ensure_not_in_clan(driver, base_url)

    # Admin creates a clan.
    login(driver, base_url, "admin")
    ensure_not_in_clan(driver, base_url)

    clan_name, clan_tag = create_clan(
        driver,
        base_url,
        description="Clan used for Selenium invitation testing."
    )

    # Admin sends invitation.
    driver.get(base_url + "/invitations")

    username = wait(driver).until(
        EC.presence_of_element_located((By.ID, "inviteUsername"))
    )
    username.send_keys("player")

    driver.find_element(
        By.CSS_SELECTOR,
        ".invite-form button[type='submit']"
    ).click()

    # Switch to player.
    login(driver, base_url, "player")
    driver.get(base_url + "/invitations")

    inbox = wait(driver).until(
        EC.presence_of_element_located((By.ID, "inboxList"))
    )

    wait(driver).until(
        lambda d: clan_name in text_of(inbox)
    )

    # Decline the invitation.
    decline_button = wait(driver).until(
        EC.element_to_be_clickable(
            (By.CSS_SELECTOR, "form[action$='/decline'] button")
        )
    )
    decline_button.click()

    # Invitation should disappear from the inbox.
    wait(driver).until(
        lambda d: clan_name not in text_of(
            d.find_element(By.ID, "inboxList")
        )
    )

    assert clan_name not in text_of(
        driver.find_element(By.ID, "inboxList")
    )


# ---------------------------------------------------------
# Test 4: Manager can revoke an invitation
# ---------------------------------------------------------

def test_manager_can_revoke_invitation(driver, base_url):
    # Player must be free to receive an invitation.
    login(driver, base_url, "player")
    ensure_not_in_clan(driver, base_url)

    # Admin creates a clan.
    login(driver, base_url, "admin")
    ensure_not_in_clan(driver, base_url)

    clan_name, clan_tag = create_clan(
        driver,
        base_url,
        description="Clan used for Selenium invitation testing."
    )

    # Admin sends invitation.
    driver.get(base_url + "/invitations")

    username = wait(driver).until(
        EC.presence_of_element_located((By.ID, "inviteUsername"))
    )
    username.send_keys("player")

    driver.find_element(
        By.CSS_SELECTOR,
        ".invite-form button[type='submit']"
    ).click()

    # Confirm player appears in sent invitations.
    sent_list = wait(driver).until(
        EC.presence_of_element_located((By.ID, "sentList"))
    )

    wait(driver).until(
        lambda d: "player" in text_of(sent_list).lower()
    )

    # Revoke the invitation.
    revoke_button = wait(driver).until(
        EC.element_to_be_clickable(
            (By.CSS_SELECTOR, "form[action$='/revoke'] button")
        )
    )
    revoke_button.click()

    # Player should disappear from sent invitations.
    wait(driver).until(
        lambda d: "player" not in text_of(
            d.find_element(By.ID, "sentList")
        ).lower()
    )

    assert "player" not in text_of(
        driver.find_element(By.ID, "sentList")
    ).lower()


# ---------------------------------------------------------
# Test 5: Recipient can accept an invitation
# ---------------------------------------------------------

def test_recipient_can_accept_invitation(driver, base_url):
    # Player must not already belong to a clan.
    login(driver, base_url, "player")
    ensure_not_in_clan(driver, base_url)

    # Admin creates a fresh clan.
    login(driver, base_url, "admin")
    ensure_not_in_clan(driver, base_url)

    clan_name, clan_tag = create_clan(
        driver,
        base_url,
        description="Clan used for Selenium invitation testing."
    )

    # Admin sends invitation to player.
    driver.get(base_url + "/invitations")

    username = wait(driver).until(
        EC.presence_of_element_located((By.ID, "inviteUsername"))
    )
    username.send_keys("player")

    driver.find_element(
        By.CSS_SELECTOR,
        ".invite-form button[type='submit']"
    ).click()

    # Switch to player.
    login(driver, base_url, "player")
    driver.get(base_url + "/invitations")

    inbox = wait(driver).until(
        EC.presence_of_element_located((By.ID, "inboxList"))
    )

    wait(driver).until(
        lambda d: clan_name in text_of(inbox)
    )

    # Accept the invitation.
    accept_button = wait(driver).until(
        EC.element_to_be_clickable(
            (By.CSS_SELECTOR, "form[action$='/accept'] button")
        )
    )
    accept_button.click()

    # The controller redirects a successful acceptance to /clanhome.
    wait(driver).until(
        lambda d: path_of(d) == "/clanhome"
    )

    assert path_of(driver) == "/clanhome"

    # Verify the player is now viewing the clan they joined.
    assert clan_name in driver.page_source