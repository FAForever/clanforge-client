"""Pytest fixtures for the Clanforge Selenium suite.

Run the application first with the dev profile (it provides the admin, player
and guest accounts these tests sign in as):

    docker compose up -d
    ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

then, from the project root:

    pip install -r selenium_tests/requirements.txt
    pytest selenium_tests

Options:
    --base-url  where the app is running (default http://localhost:8080,
                or the CLANFORGE_URL environment variable)
    --headed    show the browser window instead of running headless
    --slow N    pause N seconds after each click, keystroke and page load,
                so a person can follow along in a --headed run
    --browser   chrome (default) or firefox
"""

import os
import time

import pytest
from selenium import webdriver
from selenium.webdriver.support.event_firing_webdriver import EventFiringWebDriver
from selenium.webdriver.support.events import AbstractEventListener

from helpers import dismiss_any_alert
from resilient import FALLBACKS, ResilientWebElement


def pytest_addoption(parser):
    parser.addoption(
        "--base-url",
        action="store",
        default=os.environ.get("CLANFORGE_URL", "http://localhost:8080"),
        help="Base URL of the running Clanforge app",
    )
    parser.addoption("--headed", action="store_true", help="Show the browser window")
    parser.addoption("--slow", action="store", type=float, default=0.0,
                     help="Seconds to pause after each action (for watching a --headed run)")
    parser.addoption("--browser", action="store", default="chrome", choices=["chrome", "firefox"])


@pytest.fixture(scope="session")
def base_url(request):
    return request.config.getoption("--base-url").rstrip("/")


@pytest.fixture(scope="session")
def driver(request):
    headed = request.config.getoption("--headed")
    browser = request.config.getoption("--browser")

    if browser == "firefox":
        opts = webdriver.FirefoxOptions()
        opts.set_preference("ui.prefersReducedMotion", 1)  # see the Chrome note below
        opts.unhandled_prompt_behavior = "ignore"
        if not headed:
            opts.add_argument("-headless")
        drv = webdriver.Firefox(options=opts)
    else:
        opts = webdriver.ChromeOptions()
        if not headed:
            opts.add_argument("--headless=new")
            # Headless Chrome on Windows with display scaling above 100% can
            # size its viewport in the wrong units: clicks in the lower part of
            # the page then land outside it and silently do nothing. Pinning the
            # scale factor keeps the viewport and the click coordinates in step.
            opts.add_argument("--force-device-scale-factor=1")
        opts.add_argument("--window-size=1400,1000")
        # styles.css fades every .panel in from opacity 0 (the rise-in
        # animation) unless reduced motion is requested. Selenium treats an
        # element at opacity 0 as not displayed, so ask for reduced motion.
        opts.add_argument("--force-prefers-reduced-motion")
        # Leave confirm() dialogs open for the test to accept or dismiss;
        # the default would dismiss them on the next command.
        opts.unhandled_prompt_behavior = "ignore"
        opts.add_argument("--no-sandbox")
        opts.add_argument("--disable-dev-shm-usage")
        # Optional: point at a specific Chrome/Chromium binary.
        if os.environ.get("CHROME_BINARY"):
            opts.binary_location = os.environ["CHROME_BINARY"]
        # Selenium Manager finds a matching driver automatically; CHROMEDRIVER
        # overrides it with a specific chromedriver executable.
        service = None
        if os.environ.get("CHROMEDRIVER"):
            from selenium.webdriver.chrome.service import Service

            service = Service(executable_path=os.environ["CHROMEDRIVER"])
        drv = webdriver.Chrome(options=opts, service=service)

    drv.set_window_size(1400, 1000)
    # Every element found is a ResilientWebElement (see resilient.py).
    drv._web_element_cls = ResilientWebElement
    # Wrap the driver so every click and every send_keys/clear first scrolls
    # the element to the middle of the viewport (see _ScrollIntoCentre).
    yield EventFiringWebDriver(drv, _ScrollIntoCentre(request.config.getoption("--slow")))
    drv.quit()


class _ScrollIntoCentre(AbstractEventListener):
    """Scroll an element to the centre of the viewport before interacting.

    The top bar is `position: sticky; z-index: 50`. Recent ChromeDriver
    versions scroll an off-screen element to the *top* edge before clicking
    it, which leaves it underneath the top bar, so the click (or the focus
    click before typing) lands on the bar instead. Centring avoids that on
    every browser version.

    With a `slow` delay it also pauses after each action so a --headed run
    can be followed by eye.
    """

    def __init__(self, slow=0.0):
        self.slow = slow

    def _pause(self):
        if self.slow:
            time.sleep(self.slow)

    @staticmethod
    def _centre(element, driver):
        driver.execute_script(
            "arguments[0].scrollIntoView({block: 'center', inline: 'center'});", element
        )

    def before_click(self, element, driver):
        self._centre(element, driver)

    def before_change_value_of(self, element, driver):
        self._centre(element, driver)

    def after_click(self, element, driver):
        self._pause()

    def after_change_value_of(self, element, driver):
        self._pause()

    def after_navigate_to(self, url, driver):
        self._pause()


@pytest.fixture(autouse=True)
def _clean_browser(driver):
    """Each test starts signed out with no stray confirm() dialog open."""
    yield
    dismiss_any_alert(driver)
    driver.delete_all_cookies()


def pytest_terminal_summary(terminalreporter):
    """Say whether any click or typing needed the JavaScript fallback."""
    if FALLBACKS:
        terminalreporter.section("input fallbacks")
        terminalreporter.write_line(
            "Native WebDriver input did not reach the page this many times, "
            "so the same action was performed from JavaScript (see resilient.py):"
        )
        for kind, n in sorted(FALLBACKS.items()):
            terminalreporter.write_line(f"  {kind}: {n}")
