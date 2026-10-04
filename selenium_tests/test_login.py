"""Selenium tests for signing in (the login popup on / and /login).

Originally written by a teammate; integrated into the suite with these
changes:
  * the "opens popup" test starts on / (Home). /login renders the popup
    already open (PageController sets showLogin), so starting there could
    never catch a broken Login button.
  * valid sign-in goes through the real form instead of the login() helper,
    which fills the form from JavaScript.
  * text is read with text_of() (see helpers.py), and every test starts
    signed out.

Accounts: the dev profile's admin/admin. One wrong password is used, well
under LoginRateLimitFilter's 5 attempts per window.
"""

from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC

from helpers import logout, path_of, text_of, wait

POPUP_OPEN = "active-popup"


def wrapper(driver):
    return driver.find_element(By.CSS_SELECTOR, ".wrapper")


def popup_is_open(driver):
    return POPUP_OPEN in wrapper(driver).get_attribute("class")


def submit_login_form(driver, username, password):
    """Type into the visible login form and press its Login button."""
    form = wait(driver).until(EC.visibility_of_element_located((By.CSS_SELECTOR, ".form-box.login form")))
    form.find_element(By.NAME, "username").send_keys(username)
    form.find_element(By.NAME, "password").send_keys(password)
    form.find_element(By.CSS_SELECTOR, "button[type='submit']").click()


# --------------------------------------------------------------------------

def test_login_button_opens_popup(driver, base_url):
    logout(driver, base_url)
    driver.get(base_url + "/")
    assert not popup_is_open(driver), "the popup should start closed on the home page"

    wait(driver).until(EC.element_to_be_clickable((By.CSS_SELECTOR, ".btn-login"))).click()

    wait(driver).until(lambda d: popup_is_open(d))
    assert wait(driver).until(EC.visibility_of_element_located((By.NAME, "username")))


def test_login_popup_closes_with_x(driver, base_url):
    logout(driver, base_url)
    driver.get(base_url + "/login")
    assert popup_is_open(driver), "/login should show the popup straight away"

    wait(driver).until(EC.element_to_be_clickable((By.CSS_SELECTOR, ".icon-close"))).click()

    wait(driver).until(lambda d: not popup_is_open(d))
    assert not popup_is_open(driver)


def test_invalid_login_shows_error(driver, base_url):
    logout(driver, base_url)
    driver.get(base_url + "/login")
    submit_login_form(driver, "wronguser", "wrongpassword")

    error = wait(driver).until(EC.presence_of_element_located((By.CSS_SELECTOR, ".form-message-error")))
    assert text_of(error) == "Invalid username or password."
    assert path_of(driver) == "/login" and "error" in driver.current_url
    # Still signed out: a protected page sends us back to the login page.
    driver.get(base_url + "/clanhome")
    wait(driver).until(lambda d: path_of(d) == "/login")


def test_valid_login_succeeds(driver, base_url):
    logout(driver, base_url)
    driver.get(base_url + "/login")
    submit_login_form(driver, "admin", "admin")

    # SecurityConfig's default target after signing in is Browse Clans.
    wait(driver).until(lambda d: path_of(d) == "/clans")
    assert path_of(driver) == "/clans"
    # Signed in: the top bar shows the account menu instead of Sign In.
    assert driver.find_elements(By.ID, "avatarBtn")
    assert not driver.find_elements(By.CSS_SELECTOR, ".topbar-actions a[href$='/login']")


def test_login_returns_user_to_protected_page(driver, base_url):
    logout(driver, base_url)

    # Settings is protected, so a signed-out visitor is sent to the login page.
    driver.get(base_url + "/settings")
    wait(driver).until(lambda d: path_of(d) == "/login")
    assert popup_is_open(driver)

    submit_login_form(driver, "admin", "admin")

    # Spring Security remembers /settings and sends us back there.
    wait(driver).until(lambda d: path_of(d) == "/settings")
    assert path_of(driver) == "/settings"
    # Signed in now: another protected page loads instead of redirecting.
    driver.get(base_url + "/clanhome")
    assert path_of(driver) == "/clanhome"
