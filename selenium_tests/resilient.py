"""A WebElement whose click() and send_keys() check that the page got them.

Selenium clicks and types by sending synthetic mouse and keyboard input to
the browser. On some Windows setups (seen with Chrome 153 while other
software was also driving the desktop) that input silently never reaches
the page: nothing throws, but nothing is clicked or typed, and tests then
fail far from the real cause.

This class tries the normal WebDriver action first, then checks whether
the page actually saw a `click` / `keydown` event. If it did not, it
performs the same action from JavaScript:

* click     -> element.click(), which fires the click event, toggles a
               checkbox through its label, follows a link and submits a form
               (with the browser's `required` checks), just as a user click does
* send_keys -> sets the value one character at a time, honouring maxlength,
               and fires an `input` event per character, then `change`

So the page's own JavaScript (live preview, tag cleaning, roster filters,
confirm() dialogs) and the server are still exercised either way. Each
fallback is counted and reported at the end of the run, so it is visible
when it happened.

Set CLANFORGE_FORCE_JS_INPUT=1 to skip the native attempt entirely (useful
for checking the fallback path itself).
"""

import logging
import os
import time
from collections import Counter

from selenium.common.exceptions import (
    NoAlertPresentException,
    NoSuchWindowException,
    StaleElementReferenceException,
    UnexpectedAlertPresentException,
    WebDriverException,
)
from selenium.webdriver.remote.webelement import WebElement

FALLBACKS = Counter()
log = logging.getLogger("clanforge.input")
FORCE_JS = os.environ.get("CLANFORGE_FORCE_JS_INPUT") == "1"

_ARM_CLICK = """
const e = arguments[0];
e.__selClicked = false;
e.addEventListener('click', () => { e.__selClicked = true; }, {capture: true, once: true});
"""

_JS_CLICK = "const e = arguments[0]; setTimeout(() => e.click(), 0);"  # async: a confirm() must not block the call

_ARM_KEYS = """
const e = arguments[0];
e.__selKeys = 0;
if (!e.__selKeyListener) {
  e.__selKeyListener = true;
  e.addEventListener('keydown', () => { e.__selKeys = (e.__selKeys || 0) + 1; }, true);
}
"""

_JS_TYPE = """
const e = arguments[0], text = arguments[1];
e.focus();
const max = e.maxLength;
for (const ch of text) {
  if (max >= 0 && e.value.length >= max) continue;
  e.value = e.value + ch;
  e.dispatchEvent(new InputEvent('input', {bubbles: true, data: ch, inputType: 'insertText'}));
}
e.dispatchEvent(new Event('change', {bubbles: true}));
"""

_JS_SELECT_OPTION = """
const o = arguments[0], s = o.closest('select');
o.selected = true;
if (s) { s.dispatchEvent(new Event('input', {bubbles: true})); s.dispatchEvent(new Event('change', {bubbles: true})); }
"""


def _alert_open(driver):
    try:
        driver.switch_to.alert  # noqa: B018 - raises when there is none
        return True
    except NoAlertPresentException:
        return False
    except WebDriverException:
        return False


class ResilientWebElement(WebElement):

    # ------------------------------------------------------------------ click
    def _describe(self):
        try:
            return self._parent.execute_script(
                "const e=arguments[0];return e.tagName.toLowerCase()+(e.id?'#'+e.id:'')+' '+(e.textContent||'').trim().slice(0,30);",
                self)
        except WebDriverException:
            return "<element>"

    def click(self):
        driver = self._parent
        if self.tag_name.lower() == "option":
            return self._click_option(driver)

        if FORCE_JS:
            FALLBACKS["click (forced)"] += 1
            driver.execute_script(_JS_CLICK, self)
            time.sleep(0.2)
            return

        try:
            driver.execute_script(_ARM_CLICK, self)
        except WebDriverException:
            return super().click()

        super().click()

        if _alert_open(driver):
            return  # the click reached the page and opened a confirm()
        try:
            got_it = driver.execute_script("return arguments[0].__selClicked === true;", self)
        except (StaleElementReferenceException, UnexpectedAlertPresentException, NoSuchWindowException):
            return  # the page navigated or a dialog opened: the click landed
        except WebDriverException:
            return
        if not got_it:
            FALLBACKS["click"] += 1
            log.info("click did not reach the page, clicking from JavaScript: %s", self._describe())
            try:
                driver.execute_script(_JS_CLICK, self)
            except (StaleElementReferenceException, UnexpectedAlertPresentException):
                return
            time.sleep(0.2)

    def _click_option(self, driver):
        if not FORCE_JS:
            super().click()
            if self.is_selected():
                return
        FALLBACKS["select option"] += 1
        driver.execute_script(_JS_SELECT_OPTION, self)

    # -------------------------------------------------------------- send_keys
    def send_keys(self, *value):
        driver = self._parent
        # File inputs are filled by path, not by key events; leave them alone.
        if (self.get_attribute("type") or "").lower() == "file":
            return super().send_keys(*value)

        text = "".join(str(v) for v in value)
        if FORCE_JS:
            FALLBACKS["typing (forced)"] += 1
            driver.execute_script(_JS_TYPE, self, text)
            return

        driver.execute_script(_ARM_KEYS, self)
        super().send_keys(*value)
        try:
            keys_seen = driver.execute_script("return arguments[0].__selKeys || 0;", self)
        except WebDriverException:
            return
        if text and keys_seen == 0:
            FALLBACKS["typing"] += 1
            log.info("typing did not reach the page, typing from JavaScript: %s <- %r", self._describe(), text)
            driver.execute_script(_JS_TYPE, self, text)
