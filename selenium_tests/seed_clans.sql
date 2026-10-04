-- Demo clans for the Browse Clans page. Safe to run more than once:
-- every row is keyed by source = 'seed', and a clan whose name or tag is
-- already taken by a live clan is skipped. The seeded players have no
-- password: they fill rosters but cannot sign in.
--
--   docker exec -i faf-db psql -U clanadmin -d clan_management < selenium_tests/seed_clans.sql
--
-- To remove them again:
--   UPDATE clan SET deleted_at = now() WHERE source = 'seed';
--   UPDATE clan_membership SET left_at = now() WHERE source = 'seed' AND left_at IS NULL;

DO $$
DECLARE
    c        RECORD;
    owner_id BIGINT;
    clan_id  BIGINT;
    uid      BIGINT;
    i        INT;
    callsigns TEXT[] := ARRAY['Vex','Kestrel','Rook','Anvil','Nova','Brine','Cinder','Talon','Quill','Drift',
                              'Hollow','Marrow','Pike','Sable','Tundra','Wisp','Yarrow','Zephyr','Ember','Flint',
                              'Gale','Harrow','Ion','Jolt','Karst','Lumen','Moss','Nettle','Onyx','Pyre'];
BEGIN
    FOR c IN SELECT * FROM (VALUES
        ('iron-vanguard',  'Iron Vanguard',        'IVG', 'EU',      'Disciplined team games most evenings. Voice comms, friendly coaching, no toxicity.', false, 14, 'Ladder night Thursday 19:00 UTC. Bring a T3 build order.', 'Marshal Ostrova'),
        ('red-horizon',    'Red Horizon',          'RHZ', 'NA',      'Casual Seton''s Clutch regulars. We play for fun and the occasional tournament.', false, 9,  NULL, 'Cmdr Halvorsen'),
        ('southern-cross', 'Southern Cross',       'SXC', 'Oceania', 'Aussie and Kiwi players, sensible timezones for once. All skill levels welcome.', false, 11, 'New members: say hi in Discord and grab a practice partner.', 'Skipper Wren'),
        ('jade-lotus',     'Jade Lotus',           'JDL', 'Asia',    'Competitive 2v2 and 4v4 team. Tryouts on weekends.', true,  8,  NULL, 'Ashigaru Tan'),
        ('the-tinkerers',  'The Tinkerers',        'TNK', 'Global',  'Map makers and modders who also occasionally play the game.', false, 6,  'Map jam starts on the 1st. Theme: islands.', 'Gearwright Moss'),
        ('night-owls',     'Night Owls',           'NOW', 'EU/NA',   'Late-night lobbies for people with questionable sleep schedules.', false, 12, NULL, 'Owlbear'),
        ('glacier-front',  'Glacier Front',        'GLF', 'EU',      'Slow, macro-heavy play. We turtle, we tech, we win eventually.', true,  7,  NULL, 'Frostline'),
        ('paper-tigers',   'Paper Tigers',         'PPT', 'NA',      'New-player friendly. Ask any question, no matter how basic.', false, 15, 'Coaching session every Sunday. Replays welcome.', 'Mentor Blake'),
        ('ember-legion',   'Ember Legion',         'EMB', 'Global',  'Aeon mains. Experimentals are a lifestyle.', false, 5,  NULL, 'Archon Vey'),
        ('quiet-storm',    'Quiet Storm',          'QST', 'Asia',    'Small, tight-knit group. Mostly 1v1 practice and replay reviews.', true,  4,  NULL, 'Monsoon'),
        ('last-light',     'Last Light',           'LLT', 'Oceania', 'Seraphim enjoyers. We apologise in advance.', false, 6,  NULL, 'Lightbringer'),
        ('cobalt-company', 'Cobalt Company',       'CBC', 'EU/NA',   'Veteran players returning after years away. Rust is expected.', false, 10, 'Weekly FFA on Saturdays.', 'Captain Harlow')
    ) AS t(key, name, tag, region, descr, invite_only, members, motd, owner_name)
    LOOP
        -- skip if this seed clan already exists or its name/tag is taken
        IF EXISTS (SELECT 1 FROM clan WHERE source = 'seed' AND source_id = c.key AND deleted_at IS NULL)
           OR EXISTS (SELECT 1 FROM clan WHERE deleted_at IS NULL AND (lower(name) = lower(c.name) OR lower(tag) = lower(c.tag))) THEN
            RAISE NOTICE 'skipping %', c.name;
            CONTINUE;
        END IF;

        -- a soft-deleted earlier copy still holds the (source, source_id) key
        UPDATE clan SET source_id = source_id || ':deleted:' || id
         WHERE source = 'seed' AND source_id = c.key AND deleted_at IS NOT NULL;

        INSERT INTO app_user (oidc_subject, display_name, source, source_id)
        VALUES ('seed:' || c.key || ':owner', c.owner_name, 'seed', c.key || ':owner')
        ON CONFLICT (oidc_subject) DO UPDATE SET display_name = EXCLUDED.display_name
        RETURNING id INTO owner_id;

        -- a clean slate for this seed clan's players (e.g. after a previous seed was deleted)
        UPDATE clan_membership SET left_at = now()
         WHERE left_at IS NULL AND user_id IN (SELECT id FROM app_user WHERE oidc_subject LIKE 'seed:' || c.key || ':%');

        INSERT INTO clan (name, tag, description, owner_id, visibility, requires_invitation, region,
                          motd, motd_author_id, motd_updated_at, source, source_id, created_at)
        VALUES (c.name, c.tag, c.descr, owner_id, 'PUBLIC', c.invite_only, c.region,
                c.motd, CASE WHEN c.motd IS NULL THEN NULL ELSE owner_id END,
                CASE WHEN c.motd IS NULL THEN NULL ELSE now() - interval '2 days' END,
                'seed', c.key, now() - (random() * interval '900 days'))
        RETURNING id INTO clan_id;

        INSERT INTO clan_membership (clan_id, user_id, role, joined_at, source, source_id)
        VALUES (clan_id, owner_id, 'OWNER', now() - interval '800 days', 'seed', c.key || ':owner:' || clan_id);

        FOR i IN 1 .. c.members - 1 LOOP
            INSERT INTO app_user (oidc_subject, display_name, source, source_id)
            VALUES ('seed:' || c.key || ':' || i,
                    callsigns[1 + (i * 7 + length(c.key)) % array_length(callsigns, 1)] || '_' || upper(c.tag) || i,
                    'seed', c.key || ':' || i)
            ON CONFLICT (oidc_subject) DO UPDATE SET display_name = EXCLUDED.display_name
            RETURNING id INTO uid;

            INSERT INTO clan_membership (clan_id, user_id, role, joined_at, source, source_id)
            VALUES (clan_id, uid, CASE WHEN i <= 2 THEN 'MODERATOR' ELSE 'MEMBER' END,
                    now() - (random() * interval '600 days'), 'seed', c.key || ':' || i || ':' || clan_id);
        END LOOP;

        RAISE NOTICE 'created % [%] with % members', c.name, c.tag, c.members;
    END LOOP;
END $$;

SELECT c.name, c.tag, c.region, CASE WHEN c.requires_invitation THEN 'invite only' ELSE 'open' END AS joining,
       count(m.id) AS members
  FROM clan c JOIN clan_membership m ON m.clan_id = c.id AND m.left_at IS NULL
 WHERE c.source = 'seed' AND c.deleted_at IS NULL
 GROUP BY c.id ORDER BY c.name;
