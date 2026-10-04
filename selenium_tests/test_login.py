from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC

from helpers import wait, login, path_of


def test_login_button_opens_popup(driver, base_url):
    driver.get(base_url + "/login")

    login_button = wait(driver).until(
        EC.element_to_be_clickable((By.CSS_SELECTOR, ".btn-login"))
    )
    login_button.click()

    wrapper = driver.find_element(By.CSS_SELECTOR, ".wrapper")

    wait(driver).until(
        lambda d: "active-popup" in wrapper.get_attribute("class")
    )

    assert "active-popup" in wrapper.get_attribute("class")


def test_login_popup_closes_with_x(driver, base_url):
    driver.get(base_url + "/login")

    login_button = wait(driver).until(
        EC.element_to_be_clickable((By.CSS_SELECTOR, ".btn-login"))
    )
    login_button.click()

    wrapper = driver.find_element(By.CSS_SELECTOR, ".wrapper")

    wait(driver).until(
        lambda d: "active-popup" in wrapper.get_attribute("class")
    )

    close_button = driver.find_element(By.CSS_SELECTOR, ".icon-close")
    close_button.click()

    wait(driver).until(
        lambda d: "active-popup" not in wrapper.get_attribute("class")
    )

    assert "active-popup" not in wrapper.get_attribute("class")


def test_invalid_login_shows_error(driver, base_url):
    driver.get(base_url + "/login")

    username = driver.find_element(By.NAME, "username")
    password = driver.find_element(By.NAME, "password")

    username.send_keys("wronguser")
    password.send_keys("wrongpassword")

    driver.find_element(
        By.CSS_SELECTOR,
        ".form-box.login button[type='submit']"
    ).click()

    wait(driver).until(
        EC.presence_of_element_located(
            (By.CSS_SELECTOR, ".form-message-error")
        )
    )

    error_message = driver.find_element(
        By.CSS_SELECTOR, ".form-message-error"
    )

    assert error_message.text == "Invalid username or password."


def test_valid_login_succeeds(driver, base_url):
    login(driver, base_url, "admin")

    assert path_of(driver) == "/clans"


def test_login_returns_user_to_protected_page(driver, base_url):
    # Make sure the browser starts logged out.
    driver.get(base_url + "/login")
    driver.delete_all_cookies()

    # Settings is protected, so a logged-out user should be sent to login.
    driver.get(base_url + "/settings")

    wait(driver).until(
        lambda d: path_of(d) == "/login"
    )

    assert path_of(driver) == "/login"

    # Enter valid credentials into the login form.
    username = driver.find_element(By.NAME, "username")
    password = driver.find_element(By.NAME, "password")

    username.send_keys("admin")
    password.send_keys("admin")

    driver.find_element(
        By.CSS_SELECTOR,
        ".form-box.login button[type='submit']"
    ).click()

    # Spring Security should remember /settings and send us back there.
    wait(driver).until(
        lambda d: path_of(d) == "/settings"
    )

    assert path_of(driver) == "/settings"