import collections
import os
import re
import shlex
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
# psql invocation and connection are configurable so this runs anywhere:
# a bundled Postgres on Windows, a system psql on macOS/Linux, or a container.
#   PSQL_CMD="docker exec -i faf-db psql"   PGPORT=5432   python load_dump.py
_DEFAULT_PSQL = os.path.join(HERE, "pg", "pgsql", "bin",
                             "psql.exe" if os.name == "nt" else "psql")
PSQL_CMD = shlex.split(os.environ.get("PSQL_CMD", "")) or [_DEFAULT_PSQL]
PGHOST = os.environ.get("PGHOST", "127.0.0.1")
PGPORT = os.environ.get("PGPORT", "5433")
PGUSER = os.environ.get("PGUSER", "clanadmin")
DB = "clan_management"
SOURCE = "faf-2023-dump"

KNOWN = {"clans": 1477, "memberships": 5740, "referenced_players": 5756,
         "logins": 24097, "invalid_tags": 93, "founder_differs": 64}

NULLS = {"NULL", "null", ""}
ALNUM = re.compile(r"^[A-Za-z0-9]+$")

C_ID, C_NAME, C_TAG, C_FOUNDER, C_LEADER, C_DESC, C_COLOR, C_CREATE, C_UPDATE, C_REQINV = range(10)
M_ID, M_CLAN, M_PLAYER, M_CREATE, M_UPDATE = range(5)


def find_dump():
    path = os.environ.get(
        "FAF_DUMP",
        os.path.join(ROOT, "reference", "faforever", "faf-db-dump", "dump.sql"),
    )
    if not os.path.exists(path):
        sys.exit(os.linesep.join([
            f"Dump not found at {path}",
            "It is not in the repository: it is 28 MB of FAF's published data.",
            "Get it with:",
            "  git clone --depth 1 https://github.com/FAForever/faf-db-dump \\",
            "      reference/faforever/faf-db-dump",
            "Or point the FAF_DUMP environment variable at an existing copy.",
        ]))
    return path


def split_rows(blob):
    rows, cur, depth, in_str, esc = [], [], 0, False, False
    for ch in blob:
        if in_str:
            cur.append(ch)
            if esc: esc = False
            elif ch == "\\": esc = True
            elif ch == "'": in_str = False
            continue
        if ch == "'":
            in_str = True; cur.append(ch); continue
        if ch == "(":
            depth += 1
            if depth == 1: cur = []; continue
        if ch == ")":
            depth -= 1
            if depth == 0: rows.append("".join(cur)); continue
        if depth >= 1: cur.append(ch)
    return rows


MYSQL_ESCAPES = {"n": "\n", "r": "\r", "t": "\t", "b": "\b", "Z": "\x1a", "0": ""}


def unescape(ch):
    return MYSQL_ESCAPES.get(ch, ch)


def split_fields(row):
    fields, cur, in_str, esc, quoted = [], [], False, False, False

    def flush():
        fields.append("".join(cur) if quoted else "".join(cur).strip())

    i, n = 0, len(row)
    while i < n:
        ch = row[i]
        if in_str:
            if esc: cur.append(unescape(ch)); esc = False
            elif ch == "\\": esc = True
            elif ch == "'":
                if i + 1 < n and row[i + 1] == "'":
                    cur.append("'"); i += 2; continue
                in_str = False
            else: cur.append(ch)
            i += 1; continue
        if ch == "'": in_str = True; quoted = True; i += 1; continue
        if ch == ",": flush(); cur = []; quoted = False; i += 1; continue
        cur.append(ch); i += 1
    flush()
    return fields


def parse_table(raw, name):
    out = []
    for blob in re.findall(r"INSERT INTO `%s` VALUES (.*?);\n" % name, raw, re.S):
        out.extend(split_fields(r) for r in split_rows(blob))
    return out


def measure(clans, members, logins):
    login_ids = {r[0] for r in logins}
    clan_ids = {c[C_ID] for c in clans}

    mem_by_clan = collections.defaultdict(set)
    for m in members:
        mem_by_clan[m[M_CLAN]].add(m[M_PLAYER])

    players_in_clans = {m[M_PLAYER] for m in members}
    referenced = (players_in_clans
                  | {c[C_FOUNDER] for c in clans if c[C_FOUNDER] not in NULLS}
                  | {c[C_LEADER] for c in clans if c[C_LEADER] not in NULLS})

    names = [c[C_NAME] for c in clans]
    tags = [c[C_TAG] for c in clans]
    sizes = [len(v) for v in mem_by_clan.values()]

    return {
        "clans": len(clans),
        "memberships": len(members),
        "logins": len(logins),
        "referenced_players": len(referenced),
        "referenced_set": referenced,

        "orphan_clan_ref": sum(1 for m in members if m[M_CLAN] not in clan_ids),
        "orphan_player_ref": sum(1 for m in members if m[M_PLAYER] not in login_ids),
        "founder_null": sum(1 for c in clans if c[C_FOUNDER] in NULLS),
        "leader_null": sum(1 for c in clans if c[C_LEADER] in NULLS),
        "founder_dangling": sum(1 for c in clans
                                if c[C_FOUNDER] not in NULLS and c[C_FOUNDER] not in login_ids),
        "leader_dangling": sum(1 for c in clans
                               if c[C_LEADER] not in NULLS and c[C_LEADER] not in login_ids),
        "leader_not_member": sum(1 for c in clans if c[C_LEADER] not in NULLS
                                 and c[C_LEADER] not in mem_by_clan.get(c[C_ID], set())),

        "founder_differs": sum(1 for c in clans
                               if c[C_FOUNDER] not in NULLS and c[C_LEADER] not in NULLS
                               and c[C_FOUNDER] != c[C_LEADER]),

        "empty_clans": sum(1 for c in clans if not mem_by_clan.get(c[C_ID])),
        "largest_clan": max(sizes) if sizes else 0,
        "median_clan": sorted(sizes)[len(sizes) // 2] if sizes else 0,
        "duplicate_players": len(members) - len(players_in_clans),

        "distinct_names": len(set(names)),
        "distinct_names_folded": len(set(n.lower() for n in names)),
        "longest_name": max(len(n) for n in names) if names else 0,
        "names_over_40": sum(1 for n in names if len(n) > 40),
        "empty_names": sum(1 for n in names if not n),
        "distinct_tags": len(set(tags)),
        "distinct_tags_folded": len(set(t.lower() for t in tags)),
        "invalid_tags": sum(1 for t in tags if not ALNUM.match(t)),
        "tags_over_3": sum(1 for t in tags if len(t) > 3),
        "empty_tags": sum(1 for t in tags if not t),
    }


def print_source_report(m):
    def section(title):
        print()
        print("=" * 66)
        print(title)
        print("=" * 66)

    section("ROW COUNTS")
    print("  clan                                   :", m["clans"])
    print("  clan_membership                        :", m["memberships"])
    print("  login (players)                        :", m["logins"])

    section("REFERENTIAL INTEGRITY (does everything point at something real?)")
    print("  memberships whose clan_id has no clan  :", m["orphan_clan_ref"])
    print("  memberships whose player_id has no login:", m["orphan_player_ref"])
    print("  clans with NULL founder_id             :", m["founder_null"])
    print("  clans with NULL leader_id              :", m["leader_null"])
    print("  clans whose founder_id has no login    :", m["founder_dangling"])
    print("  clans whose leader_id has no login     :", m["leader_dangling"])
    print("  clans whose leader is NOT a member     :", m["leader_not_member"])

    section("OWNERSHIP (the spec has one 'owner'; FAF has founder and leader)")
    pct = 100 * m["founder_differs"] / m["clans"] if m["clans"] else 0
    print("  clans where founder_id != leader_id    : %d (%.1f%%)" % (m["founder_differs"], pct))
    print("  -> ownership has changed hands at least this often")

    section("MEMBERSHIP SHAPE")
    print("  clans with zero members                :", m["empty_clans"])
    print("  largest clan                           :", m["largest_clan"], "members")
    print("  median clan size                       :", m["median_clan"], "members")
    print("  duplicate player_id rows (should be 0) :", m["duplicate_players"])

    section("NAMES AND TAGS")
    print("  distinct names                         : %d / %d" % (m["distinct_names"], m["clans"]))
    print("  distinct names, case-folded            :", m["distinct_names_folded"])
    print("  longest name                           :", m["longest_name"], "chars")
    print("  names over 40 chars (FAF column limit) :", m["names_over_40"])
    print("  empty names                            :", m["empty_names"])
    print("  distinct tags                          : %d / %d" % (m["distinct_tags"], m["clans"]))
    print("  distinct tags, case-folded             :", m["distinct_tags_folded"])
    print("  tags failing ASCII alphanumeric (§9)   :", m["invalid_tags"])
    print("  tags over 3 chars                      :", m["tags_over_3"])
    print("  empty tags                             :", m["empty_tags"])

    section("WHAT AN IMPORT WOULD HAVE TO CREATE")
    print("  distinct FAF player ids referenced     :", m["referenced_players"])
    print("  -> user rows needed before any membership can point at them")
    print("  clans                                  :", m["clans"])
    print("  membership rows                        :", m["memberships"])


def check_parser(m):
    print()
    print("parser against recorded values ...")
    bad = 0
    for key, want in KNOWN.items():
        got = m[key]
        ok = "ok  " if got == want else "FAIL"
        if got != want:
            bad += 1
        print(f"  [{ok}] {key:22} {got:>6}   recorded {want}")
    if bad:
        sys.exit(f"\n{bad} parser check(s) failed. The dump or the parser has changed. "
                 "Do not load until this is understood.")
    return True


def psql(sql, fetch=False):
    cmd = PSQL_CMD + ["-h", PGHOST, "-p", PGPORT, "-U", PGUSER, "-d", DB,
           "-v", "ON_ERROR_STOP=1", "-q"]
    cmd += (["-t", "-A", "-c", sql] if fetch else ["-c", sql])
    r = subprocess.run(cmd, capture_output=True, text=True, encoding="utf8", errors="replace")
    if r.returncode != 0:
        raise SystemExit(f"psql failed:\n{r.stderr[:2000]}")
    return r.stdout.strip()


def copy_in(table, columns, rows):
    if not rows:
        return 0
    cmd = PSQL_CMD + ["-h", PGHOST, "-p", PGPORT, "-U", PGUSER, "-d", DB,
           "-v", "ON_ERROR_STOP=1", "-q",
           "-c", f"COPY {table} ({','.join(columns)}) FROM STDIN"]
    buf = []
    for row in rows:
        fields = []
        for v in row:
            if v is None:
                fields.append("\\N")
            else:
                s = str(v)
                s = s.replace("\\", "\\\\").replace("\t", "\\t")
                s = s.replace("\n", "\\n").replace("\r", "\\r")
                fields.append(s)
        buf.append("\t".join(fields))
    r = subprocess.run(cmd, input="\n".join(buf) + "\n", capture_output=True, text=True, encoding="utf8", errors="replace")
    if r.returncode != 0:
        raise SystemExit(f"COPY into {table} failed:\n{r.stderr[:2000]}")
    return len(rows)


def clean(v):
    return None if v in (None, "NULL") else v


def load(clans, members, m):
    print()
    print("loading ...")
    psql("TRUNCATE moderation_action, clan_event, clan_report, clan_invitation, "
         "clan_membership, clan, app_user RESTART IDENTITY CASCADE;")

    user_rows = [(str(p), f"User_{p}", SOURCE, str(p))
                 for p in sorted(m["referenced_set"], key=int)]
    print(f"  app_user         {copy_in('app_user', ['oidc_subject', 'display_name', 'source', 'source_id'], user_rows)}")

    uid = {}
    for line in psql("SELECT source_id, id FROM app_user WHERE source_id IS NOT NULL;", fetch=True).splitlines():
        src, pk = line.split("|")
        uid[src] = int(pk)

    clan_rows = [(
        (clean(c[C_NAME]) or "").strip(),
        clean(c[C_TAG]) or "",
        clean(c[C_DESC]),
        uid[c[C_LEADER]],
        "PUBLIC",
        "true" if c[C_REQINV] == "1" else "false",
        clean(c[C_COLOR]),
        SOURCE,
        c[C_ID],
        clean(c[C_CREATE]),
        clean(c[C_UPDATE]),
    ) for c in clans]
    print(f"  clan             {copy_in('clan', ['name', 'tag', 'description', 'owner_id', 'visibility', 'requires_invitation', 'tag_color', 'source', 'source_id', 'created_at', 'updated_at'], clan_rows)}")

    cid = {}
    for line in psql("SELECT source_id, id FROM clan WHERE source_id IS NOT NULL;", fetch=True).splitlines():
        src, pk = line.split("|")
        cid[src] = int(pk)

    leader_of = {c[C_ID]: c[C_LEADER] for c in clans}
    mem_rows = [(
        cid[m_[M_CLAN]],
        uid[m_[M_PLAYER]],
        "OWNER" if leader_of.get(m_[M_CLAN]) == m_[M_PLAYER] else "MEMBER",
        clean(m_[M_CREATE]),
        SOURCE,
        m_[M_ID],
    ) for m_ in members]
    print(f"  clan_membership  {copy_in('clan_membership', ['clan_id', 'user_id', 'role', 'joined_at', 'source', 'source_id'], mem_rows)}")

    ev = [(
        "clan", cid[c[C_ID]], "ClanFounderRecorded",
        '{"founder_player_id": "%s", "current_owner_player_id": "%s"}' % (clean(c[C_FOUNDER]), clean(c[C_LEADER])),
        clean(c[C_CREATE]),
    ) for c in clans
        if clean(c[C_FOUNDER]) and clean(c[C_FOUNDER]) != clean(c[C_LEADER])]
    n = copy_in("clan_event", ["entity_type", "entity_id", "event_type", "payload", "occurred_at"], ev)
    print(f"  clan_event       {n}  (clans whose founder is not the current owner)")


def check_database(m):
    print()
    print("database against measured source ...")
    checks = [
        ("clans", "SELECT count(*) FROM clan", m["clans"]),
        ("memberships", "SELECT count(*) FROM clan_membership", m["memberships"]),
        ("users", "SELECT count(*) FROM app_user", m["referenced_players"]),
        ("orphan memberships",
         "SELECT count(*) FROM clan_membership cm LEFT JOIN clan c ON c.id=cm.clan_id WHERE c.id IS NULL", 0),
        ("orphan owners",
         "SELECT count(*) FROM clan c LEFT JOIN app_user u ON u.id=c.owner_id WHERE u.id IS NULL", 0),
        ("owners not in their clan",
         "SELECT count(*) FROM clan c WHERE NOT EXISTS (SELECT 1 FROM clan_membership cm "
         "WHERE cm.clan_id=c.id AND cm.user_id=c.owner_id AND cm.left_at IS NULL)", 0),
        ("founder-change events",
         "SELECT count(*) FROM clan_event WHERE event_type='ClanFounderRecorded'", m["founder_differs"]),
        ("admin worklist", "SELECT count(*) FROM clan_needing_attention", m["invalid_tags"]),
    ]
    bad = 0
    for label, sql, want in checks:
        got = int(psql(sql, fetch=True))
        ok = "ok  " if got == want else "FAIL"
        if got != want:
            bad += 1
        print(f"  [{ok}] {label:26} {got:>6}   expected {want}")
    return bad


def main():
    verify_only = "--verify-only" in sys.argv

    dump = find_dump()
    print(f"reading {dump}")
    raw = open(dump, encoding="utf8", errors="replace").read()

    clans = parse_table(raw, "clan")
    members = parse_table(raw, "clan_membership")
    logins = parse_table(raw, "login")

    m = measure(clans, members, logins)
    print_source_report(m)
    check_parser(m)

    if verify_only:
        print("\n--verify-only: nothing was written to the database.")
        return 0

    load(clans, members, m)
    bad = check_database(m)

    print("\nFAILED" if bad else "\nAll checks passed.")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
