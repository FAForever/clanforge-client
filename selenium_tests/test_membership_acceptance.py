"""Test C: Membership Management through the real browser interface.

The service-level MembershipAcceptanceTest proves the database rules.  This
test complements it by driving the pages a user sees and by measuring the
elapsed time from each submitted action until its result is visible.

Development accounts:
    admin  owns Clan A
    guest  owns Clan B
    player performs the membership operations
"""

import time
from urllib.parse import quote

from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC

from helpers import (
    click_expecting_confirm,
    create_clan,
    ensure_not_in_clan,
    flash_error,
    flash_notice,
    login,
    path_of,
    set_toggle,
    text_of,
    wait,
)


CLAN_A_OWNER = "admin"
CLAN_B_OWNER = "guest"
MEMBER = "player"
PERFORMANCE_LIMIT_SECONDS = 2.0
PERFORMANCE_RESULTS = {}


def measure_action(driver, label, action, completed):
    """Run one browser action and require its visible result within two seconds."""
    start = time.perf_counter()
    action()
    wait(driver, 10).until(completed)
    elapsed = time.perf_counter() - start
    result = "PASS" if elapsed <= PERFORMANCE_LIMIT_SECONDS else "FAIL"
    print(f"Test C | {label}: {elapsed:.3f} seconds | {result}")
    PERFORMANCE_RESULTS[label] = elapsed
    assert elapsed <= PERFORMANCE_LIMIT_SECONDS, (
        f"{label} took {elapsed:.3f} seconds; "
        f"the acceptance limit is {PERFORMANCE_LIMIT_SECONDS:.1f} seconds"
    )
    return elapsed


def open_clan_detail(driver, base_url, clan_name):
    """Open a public clan by name and return the stable detail-page URL."""
    driver.get(f"{base_url}/clans?q={quote(clan_name)}")
    view = wait(driver).until(EC.element_to_be_clickable((
        By.XPATH,
        f"//article[.//*[contains(@class,'clan-card-name') "
        f"and normalize-space()='{clan_name}']]"
        "//a[contains(normalize-space(),'View Clan')]",
    )))
    detail_url = view.get_attribute("href")
    view.click()
    wait(driver).until(EC.presence_of_element_located((By.CSS_SELECTOR, "main.page")))
    return detail_url


def timed_join(driver, label):
    button = wait(driver).until(EC.element_to_be_clickable((
        By.CSS_SELECTOR,
        "form[action$='/join'] button[type='submit']",
    )))
    measure_action(
        driver,
        label,
        button.click,
        lambda d: path_of(d).endswith("/clanhome")
        and flash_notice(d) == "You have joined the clan.",
    )


def timed_leave(driver, label="Leave clan"):
    button = wait(driver).until(EC.element_to_be_clickable((
        By.XPATH,
        "//button[normalize-space()='Leave Clan']",
    )))

    def leave():
        click_expecting_confirm(driver, button).accept()

    measure_action(
        driver,
        label,
        leave,
        lambda d: path_of(d).endswith("/clans")
        and flash_notice(d) == "You have left the clan.",
    )


def member_row(driver, username):
    return driver.find_element(By.CSS_SELECTOR, f".member-row[data-name='{username}']")


def invitation_row(driver, clan_name):
    for row in driver.find_elements(By.CSS_SELECTOR, "#inboxList .invite-row"):
        if clan_name.lower() in text_of(row).lower():
            return row
    return None


def clean_test_accounts(driver, base_url):
    """Return the three development accounts to a no-clan state."""
    for username in (MEMBER, CLAN_B_OWNER, CLAN_A_OWNER):
        login(driver, base_url, username)
        ensure_not_in_clan(driver, base_url)


def test_c_membership_management_workflow(driver, base_url, record_property):
    """Execute the complete Test C workflow and print performance evidence."""
    PERFORMANCE_RESULTS.clear()
    clean_test_accounts(driver, base_url)

    try:
        # Two independent public clans are needed to test the one-clan rule.
        login(driver, base_url, CLAN_B_OWNER)
        clan_b, _ = create_clan(
            driver,
            base_url,
            description="Test C second public clan.",
            public=True,
            invite_only=False,
        )
        clan_b_url = open_clan_detail(driver, base_url, clan_b)

        login(driver, base_url, CLAN_A_OWNER)
        clan_a, _ = create_clan(
            driver,
            base_url,
            description="Test C public and private membership clan.",
            public=True,
            invite_only=False,
        )
        clan_a_url = open_clan_detail(driver, base_url, clan_a)

        # 1 and 6: a user with no clan can join a public, open clan.
        login(driver, base_url, MEMBER)
        driver.get(clan_a_url)
        timed_join(driver, "Join public Clan A")
        assert text_of(driver.find_element(By.CSS_SELECTOR, ".clan-name")) == clan_a
        assert text_of(member_row(driver, MEMBER).find_element(By.CSS_SELECTOR, ".member-role")) == "Member"

        # 2: the UI must not offer a second join while the user is in Clan A.
        driver.get(clan_b_url)
        wait(driver).until(EC.presence_of_element_located((By.CSS_SELECTOR, "main.page")))
        assert not driver.find_elements(By.CSS_SELECTOR, "form[action$='/join']")
        driver.get(base_url + "/clanhome")
        assert text_of(driver.find_element(By.CSS_SELECTOR, ".clan-name")) == clan_a
        print("Test C | Second active membership prevented | PASS")

        # 3: after leaving Clan A, the same user can join Clan B.
        timed_leave(driver)
        driver.get(clan_b_url)
        timed_join(driver, "Join Clan B after leaving Clan A")

        # 4: Clan B's owner can remove the member.
        login(driver, base_url, CLAN_B_OWNER)
        driver.get(base_url + "/clanhome")
        remove_button = member_row(driver, MEMBER).find_element(
            By.XPATH,
            ".//button[normalize-space()='Remove']",
        )

        def remove_member():
            click_expecting_confirm(driver, remove_button).accept()

        measure_action(
            driver,
            "Remove member",
            remove_member,
            lambda d: flash_notice(d) == "Member removed."
            and not d.find_elements(By.CSS_SELECTOR, f".member-row[data-name='{MEMBER}']"),
        )

        # Make Clan A private and invitation-only, then prove an outsider cannot
        # enumerate or join it directly.  The deliberate response is 404, not a
        # message confirming that a private clan exists.
        login(driver, base_url, CLAN_A_OWNER)
        driver.get(base_url + "/settings")
        set_toggle(driver, "publicToggle", False)
        set_toggle(driver, "inviteOnlyToggle", True)
        driver.find_element(By.ID, "saveBtn").click()
        wait(driver).until(lambda d: flash_notice(d) == "Settings saved.")

        login(driver, base_url, MEMBER)
        start = time.perf_counter()
        driver.get(clan_a_url)
        wait(driver).until(lambda d: "status=404" in d.page_source)
        private_elapsed = time.perf_counter() - start
        print(
            "Test C | Private clan direct access blocked: "
            f"{private_elapsed:.3f} seconds | PASS"
        )
        assert private_elapsed <= PERFORMANCE_LIMIT_SECONDS
        PERFORMANCE_RESULTS["Private clan direct access blocked"] = private_elapsed

        # A private clan is joined through an invitation instead.
        login(driver, base_url, CLAN_A_OWNER)
        driver.get(base_url + "/invitations")
        invite_box = wait(driver).until(EC.presence_of_element_located((By.ID, "inviteUsername")))
        invite_box.send_keys(MEMBER)
        driver.find_element(By.CSS_SELECTOR, ".invite-form button[type='submit']").click()
        wait(driver).until(lambda d: flash_notice(d) == f"Invite sent to {MEMBER}.")

        login(driver, base_url, MEMBER)
        driver.get(base_url + "/invitations")
        wait(driver).until(EC.presence_of_element_located((By.ID, "inboxList")))
        row = invitation_row(driver, clan_a)
        assert row is not None
        accept_button = row.find_element(By.CSS_SELECTOR, "form[action$='/accept'] button")
        measure_action(
            driver,
            "Join private Clan A by invitation",
            accept_button.click,
            lambda d: path_of(d) == "/clanhome"
            and flash_notice(d) == f"Welcome to {clan_a}.",
        )
        assert text_of(member_row(driver, MEMBER).find_element(By.CSS_SELECTOR, ".member-role")) == "Member"

        # Leave the private clan, rejoin Clan B and transfer its ownership.
        timed_leave(driver, "Leave private Clan A")
        driver.get(clan_b_url)
        timed_join(driver, "Rejoin Clan B for ownership transfer")

        login(driver, base_url, CLAN_B_OWNER)
        driver.get(base_url + "/settings")
        transfer_button = wait(driver).until(EC.element_to_be_clickable((By.ID, "transferBtn")))

        def transfer_ownership():
            click_expecting_confirm(driver, transfer_button).accept()

        measure_action(
            driver,
            "Transfer ownership",
            transfer_ownership,
            lambda d: path_of(d) == "/clanhome"
            and flash_notice(d) == "Ownership transferred. You are now a moderator.",
        )
        assert text_of(member_row(driver, MEMBER).find_element(By.CSS_SELECTOR, ".member-role")) == "Owner"
        assert text_of(member_row(driver, CLAN_B_OWNER).find_element(By.CSS_SELECTOR, ".member-role")) == "Moderator"

        record_property("performance_limit_seconds", f"{PERFORMANCE_LIMIT_SECONDS:.1f}")
        record_property("second_active_membership_prevented", "PASS")
        for label, elapsed in PERFORMANCE_RESULTS.items():
            key = "elapsed_" + label.lower().replace(" ", "_")
            record_property(key, f"{elapsed:.3f}")
    finally:
        # Keep repeat runs independent even if an assertion above fails.
        clean_test_accounts(driver, base_url)
