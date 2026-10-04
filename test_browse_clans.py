"""Selenium tests for the Browse Clans page (/clans).

Covers the page as an anonymous visitor (it is public): the header stats,
search by name and by tag, the empty state, literal matching of SQL
wildcards, the recruiting filter, both sort orders, pagination and the
View Clan link.

The page lists whatever is in the database (the 12 demo clans, clans made by
the other tests, and the ~1,500 FAF clans if you loaded the dump), so the
tests only make exact claims about the demo clans from seed_clans.sql and
check general rules (ordering, filtering, counts) for everything else. If
the demo clans are missing, the `demo_clans` fixture adds them with
`docker exec`, or skips the tests that need them with instructions.
"""

import re
import subprocess
import uuid
from pathlib import Path
from urllib.parse import parse_qs, quote, urlparse

import pytest
from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import Select

from helpers import path_of, text_of, wait

# name -> (tag, region, members, invite_only), as created by seed_clans.sql
DEMO = {
    "Iron Vanguard": ("IVG", "EU", 14, False),
    "Red Horizon": ("RHZ", "NA", 9, False),
    "Southern Cross": ("SXC", "Oceania", 11, False),
    "Jade Lotus": ("JDL", "Asia", 8, True),
    "The Tinkerers": ("TNK", "Global", 6, False),
    "Night Owls": ("NOW", "EU/NA", 12, False),
    "Glacier Front": ("GLF", "EU", 7, True),
    "Paper Tigers": ("PPT", "NA", 15, False),
    "Ember Legion": ("EMB", "Global", 5, False),
    "Quiet Storm": ("QST", "Asia", 4, True),
    "Last Light": ("LLT", "Oceania", 6, False),
    "Cobalt Company": ("CBC", "EU/NA", 10, False),
}
PAGE_SIZE = 20
SEED_SQL = Path(__file__).with_name("seed_clans.sql")


# --------------------------------------------------------------------------
# Page helpers
# --------------------------------------------------------------------------

def open_browse(driver, base_url, query=""):
    driver.get(base_url + "/clans" + query)
    wait(driver).until(EC.presence_of_element_located((By.ID, "clanGrid")))
    return driver


def read_cards(driver):
    """Every clan card on the page, as plain values."""
    cards = []
    for card in driver.find_elements(By.CSS_SELECTOR, "#clanGrid article.clan-card"):
        name_el = card.find_element(By.CSS_SELECTOR, ".clan-card-name")
        stats = {
            text_of(s.find_element(By.CSS_SELECTOR, ".stat-label")): text_of(s.find_element(By.CSS_SELECTOR, ".stat-value"))
            for s in card.find_elements(By.CSS_SELECTOR, ".clan-stats .stat")
        }
        badge = card.find_element(By.CSS_SELECTOR, ".recruit-badge")
        cards.append({
            "name": text_of(name_el),
            "title": name_el.get_attribute("title"),
            "tag_line": text_of(card.find_element(By.CSS_SELECTOR, ".clan-card-tag")),
            "desc": text_of(card.find_element(By.CSS_SELECTOR, ".clan-card-desc")),
            "members": int(stats["Members"]),
            "founded": stats["Founded"],
            "badge": text_of(badge),
            "badge_class": badge.get_attribute("class"),
            "href": card.find_element(By.LINK_TEXT, "View Clan").get_attribute("href"),
        })
    return cards


def card_named(driver, name):
    matches = [c for c in read_cards(driver) if c["name"] == name]
    assert matches, f"no card for {name!r} on {driver.current_url}"
    return matches[0]


def submit_search(driver, q=None, recruiting=None, sort=None):
    """Use the search form the way a visitor would, then wait for the new page."""
    old_grid = driver.find_element(By.ID, "clanGrid")
    if q is not None:
        box = driver.find_element(By.ID, "browseSearch")
        box.clear()
        if q:
            box.send_keys(q)
    if recruiting is not None:
        Select(driver.find_element(By.ID, "recruitFilter")).select_by_value(recruiting)
    if sort is not None:
        Select(driver.find_element(By.ID, "sortSelect")).select_by_value(sort)
    driver.find_element(By.CSS_SELECTOR, "form.browse-controls button[type='submit']").click()
    wait(driver).until(EC.staleness_of(old_grid))
    wait(driver).until(EC.presence_of_element_located((By.ID, "clanGrid")))


def caption(driver):
    """(shown, total, page, pages) from 'Showing X of Y clans · page P of N'."""
    els = driver.find_elements(By.CSS_SELECTOR, ".showing-caption")
    if not els:
        return None
    m = re.search(r"Showing (\d+) of ([\d,]+) clans . page (\d+) of (\d+)", text_of(els[0]))
    assert m, f"unexpected caption: {text_of(els[0])!r}"
    return tuple(int(g.replace(",", "")) for g in m.groups())


def hero_stats(driver):
    return {
        text_of(s.find_element(By.CSS_SELECTOR, ".stat-label")): int(text_of(s.find_element(By.CSS_SELECTOR, ".stat-value")).replace(",", ""))
        for s in driver.find_elements(By.CSS_SELECTOR, ".browse-stats .stat")
    }


def query_of(url):
    return {k: v[0] for k, v in parse_qs(urlparse(url).query, keep_blank_values=True).items()}


# --------------------------------------------------------------------------
# Fixtures
# --------------------------------------------------------------------------

@pytest.fixture(scope="module", autouse=True)
def anonymous(driver, base_url):
    """The page is public: these tests browse without signing in."""
    driver.get(base_url + "/clans")
    driver.delete_all_cookies()


@pytest.fixture(scope="module")
def demo_clans(driver, base_url):
    """Make sure the 12 demo clans exist, adding them via Docker if needed."""
    def present():
        open_browse(driver, base_url, "?q=" + quote("Paper Tigers"))
        return any(c["name"] == "Paper Tigers" for c in read_cards(driver))

    if not present():
        try:
            with open(SEED_SQL, "rb") as sql:
                subprocess.run(
                    ["docker", "exec", "-i", "faf-db", "psql", "-U", "clanadmin",
                     "-d", "clan_management", "-v", "ON_ERROR_STOP=1"],
                    stdin=sql, capture_output=True, timeout=120, check=True,
                )
        except (OSError, subprocess.SubprocessError) as e:
            pytest.skip(f"demo clans missing and could not be added ({e}); run seed_clans.cmd first")
        if not present():
            pytest.skip("demo clans missing; run seed_clans.cmd first")
    return DEMO


# --------------------------------------------------------------------------
# Tests
# --------------------------------------------------------------------------

def test_page_loads_for_anonymous_visitor(driver, base_url, demo_clans):
    d = open_browse(driver, base_url)

    assert d.title == "Browse Clans · Clanforge"
    assert text_of(d.find_element(By.CSS_SELECTOR, "h1.browse-title")) == "Browse Clans"
    active = d.find_element(By.CSS_SELECTOR, ".primary-nav a[aria-current='page']")
    assert text_of(active) == "Browse Clans"
    # Anonymous: a Sign In button instead of the account menu.
    assert d.find_elements(By.CSS_SELECTOR, ".topbar-actions a[href$='/login']")
    assert not d.find_elements(By.ID, "avatarBtn")

    assert d.find_element(By.ID, "browseSearch").get_attribute("placeholder") == "Search by clan name or tag…"
    recruit = Select(d.find_element(By.ID, "recruitFilter"))
    assert [text_of(o) for o in recruit.options] == ["All Clans", "Recruiting", "Not Recruiting"]
    assert recruit.first_selected_option.get_attribute("value") == "all"
    sort = Select(d.find_element(By.ID, "sortSelect"))
    assert [text_of(o) for o in sort.options] == ["Sort: Name A to Z", "Sort: Most Members"]
    # ClanController now defaults to the members sort.
    assert sort.first_selected_option.get_attribute("value") == "members"

    cards = read_cards(d)
    assert 1 <= len(cards) <= PAGE_SIZE
    shown, total, page, pages = caption(d)
    assert (shown, page) == (len(cards), 1)
    assert pages == -(-total // PAGE_SIZE)  # ceiling division


def test_header_stats_agree_with_the_listing(driver, base_url, demo_clans):
    d = open_browse(driver, base_url, "?recruiting=all")
    stats = hero_stats(d)
    assert set(stats) == {"Clans", "Commanders", "Recruiting Now"}
    # "Clans" counts every public clan, which is what the unfiltered listing totals.
    assert stats["Clans"] == caption(d)[1]

    submit_search(d, recruiting="open")
    assert hero_stats(d)["Recruiting Now"] == caption(d)[1]
    assert stats["Recruiting Now"] <= stats["Clans"]


def test_search_by_name_shows_the_clan_card(driver, base_url, demo_clans):
    d = open_browse(driver, base_url)
    submit_search(d, q="Paper Tigers")

    assert path_of(d).endswith("/clans")
    assert query_of(d.current_url)["q"] == "Paper Tigers"
    # The search box keeps what was typed.
    assert d.find_element(By.ID, "browseSearch").get_attribute("value") == "Paper Tigers"

    card = card_named(d, "Paper Tigers")
    tag, region, members, _ = demo_clans["Paper Tigers"]
    assert card["tag_line"] == f"[{tag}] · {region}"
    assert card["desc"] == "New-player friendly. Ask any question, no matter how basic."
    assert card["members"] >= members
    assert card["badge"] == "Recruiting" and "is-open" in card["badge_class"]
    assert re.fullmatch(r"\d{4}", card["founded"])
    # Long names are clipped to two lines; the full name is in the tooltip.
    assert card["title"] == "Paper Tigers"


def test_search_matches_tags_case_insensitively(driver, base_url, demo_clans):
    d = open_browse(driver, base_url)
    submit_search(d, q="sxc")

    card = card_named(d, "Southern Cross")
    assert card["tag_line"] == "[SXC] · Oceania"
    # Every result really does contain the search text in its name or tag.
    for c in read_cards(d):
        tag = re.match(r"\[(.*?)\]", c["tag_line"]).group(1)
        assert "sxc" in c["name"].lower() or "sxc" in tag.lower(), c


def test_no_matches_shows_empty_state_and_clear_search_resets(driver, base_url):
    d = open_browse(driver, base_url)
    submit_search(d, q="no-such-clan-" + uuid.uuid4().hex[:8])

    assert read_cards(d) == []
    empty = d.find_element(By.CSS_SELECTOR, ".empty-state")
    assert empty.is_displayed()
    assert "No clans match your search." in text_of(empty)
    assert caption(d) is None

    empty.find_element(By.LINK_TEXT, "Clear search").click()
    wait(d).until(lambda _: query_of(d.current_url).get("q", "") == "")
    wait(d).until(EC.presence_of_element_located((By.CSS_SELECTOR, "#clanGrid article.clan-card")))
    assert d.find_element(By.ID, "browseSearch").get_attribute("value") == ""
    assert not d.find_elements(By.CSS_SELECTOR, ".empty-state")


def test_sql_wildcards_in_search_are_matched_literally(driver, base_url):
    """'%' and '_' are escaped (ClanService.escapeLike), so they don't match everything."""
    d = open_browse(driver, base_url)
    submit_search(d, q="%%%")
    assert read_cards(d) == [], "a search for '%%%' should not list every clan"
    assert d.find_element(By.CSS_SELECTOR, ".empty-state").is_displayed()

    submit_search(d, q="___")
    for c in read_cards(d):
        assert "___" in c["name"] or "___" in c["tag_line"], c


def test_recruiting_filter_splits_open_and_invite_only_clans(driver, base_url, demo_clans):
    d = open_browse(driver, base_url)

    submit_search(d, recruiting="open")
    open_cards = read_cards(d)
    assert open_cards
    assert all(c["badge"] == "Recruiting" and "is-open" in c["badge_class"] for c in open_cards)

    submit_search(d, recruiting="closed")
    closed_cards = read_cards(d)
    assert closed_cards
    assert all(c["badge"] == "Invite Only" and "is-closed" in c["badge_class"] for c in closed_cards)
    # The filter choice survives the page reload.
    assert Select(d.find_element(By.ID, "recruitFilter")).first_selected_option.get_attribute("value") == "closed"

    # An invite-only demo clan: found under Not Recruiting, absent under Recruiting.
    submit_search(d, q="Jade Lotus", recruiting="closed")
    assert card_named(d, "Jade Lotus")["badge"] == "Invite Only"
    submit_search(d, recruiting="open")
    assert "Jade Lotus" not in [c["name"] for c in read_cards(d)]


def _in_name_order(names):
    """True if the names that start with a letter run A to Z by that letter.

    PostgreSQL sorts by the database's collation (en_US.utf8 here), which
    has its own rules for symbols, digits and spacing: "$atansinc" correctly
    sorts before "08/15". Python can't reproduce that exactly, so only names
    whose first character is a letter A-Z are compared, by that letter.
    Case-insensitive (en_US) and raw ("C" collation) orders both pass.
    """
    firsts = [n[0] for n in names if n[:1].isascii() and n[:1].isalpha()]
    return firsts == sorted(firsts, key=str.lower) or firsts == sorted(firsts)


def test_sort_by_members_then_by_name(driver, base_url, demo_clans):
    d = open_browse(driver, base_url)

    # Default: most members first.
    counts = [c["members"] for c in read_cards(d)]
    assert counts == sorted(counts, reverse=True), counts

    # Name order, checked on a search that returns plenty of names starting
    # with a letter (page 1 of all clans can be all symbols and digits).
    for q in ("the", "a", "e"):
        submit_search(d, q=q, sort="name")
        names = [c["name"] for c in read_cards(d)]
        if sum(1 for n in names if n[:1].isascii() and n[:1].isalpha()) >= 3:
            break
    assert query_of(d.current_url)["sort"] == "name"
    assert sum(1 for n in names if n[:1].isascii() and n[:1].isalpha()) >= 3, names
    assert _in_name_order(names), names

    # Back to members through the form: order and selection follow.
    submit_search(d, q="", sort="members")
    assert Select(d.find_element(By.ID, "sortSelect")).first_selected_option.get_attribute("value") == "members"
    counts = [c["members"] for c in read_cards(d)]
    assert counts == sorted(counts, reverse=True), counts


def test_pagination_keeps_search_and_filters(driver, base_url, demo_clans):
    d = open_browse(driver, base_url, "?recruiting=all&sort=name")
    shown, total, page, pages = caption(d)

    if pages == 1:
        # Everything fits on one page: no pager at all.
        assert total <= PAGE_SIZE and shown == total
        assert not d.find_elements(By.CSS_SELECTOR, "nav.pager a")
        return

    assert shown == PAGE_SIZE
    assert not d.find_elements(By.LINK_TEXT, "Previous")
    first_page = [c["name"] for c in read_cards(d)]

    nxt = d.find_element(By.LINK_TEXT, "Next")
    params = query_of(nxt.get_attribute("href"))
    assert params["page"] == "1" and params["sort"] == "name" and params["recruiting"] == "all"
    nxt.click()
    wait(d).until(lambda _: query_of(d.current_url).get("page") == "1")

    assert caption(d)[2] == 2
    second_page = [c["name"] for c in read_cards(d)]
    assert second_page and not set(first_page) & set(second_page)
    assert d.find_elements(By.LINK_TEXT, "Previous")
    # The chosen sort is still selected on page 2.
    assert Select(d.find_element(By.ID, "sortSelect")).first_selected_option.get_attribute("value") == "name"

    d.find_element(By.LINK_TEXT, "Previous").click()
    wait(d).until(lambda _: query_of(d.current_url).get("page") == "0")
    assert [c["name"] for c in read_cards(d)] == first_page


def test_view_clan_opens_the_matching_detail_page(driver, base_url, demo_clans):
    d = open_browse(driver, base_url, "?q=" + quote("Iron Vanguard"))
    card = card_named(d, "Iron Vanguard")

    d.find_element(
        By.XPATH,
        "//article[.//p[contains(@class,'clan-card-name') and normalize-space()='Iron Vanguard']]"
        "//a[normalize-space()='View Clan']",
    ).click()
    wait(d).until(lambda _: re.search(r"/clans/\d+$", path_of(d)))
    assert d.current_url == card["href"]

    assert d.title == "Iron Vanguard"
    assert text_of(d.find_element(By.TAG_NAME, "h1")) == "IVG Iron Vanguard"
    facts = dict(zip(
        [text_of(e) for e in d.find_elements(By.CSS_SELECTOR, "dl.facts dt")],
        [text_of(e) for e in d.find_elements(By.CSS_SELECTOR, "dl.facts dd")],
    ))
    assert facts["Members"] == str(card["members"])  # same count as the card
    assert facts["Region"] == "EU"
    assert facts["Membership"] == "Open"
    # Anonymous visitors are pointed at sign-in rather than offered Join.
    assert not d.find_elements(By.CSS_SELECTOR, "form[action$='/join']")
    assert "Sign in to join this clan." in text_of(d.find_element(By.TAG_NAME, "main"))

    d.find_element(By.LINK_TEXT, "Back to clans").click()
    wait(d).until(lambda _: path_of(d).rstrip("/").endswith("/clans"))
