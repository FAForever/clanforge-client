--a membership still active in a clan that was deleted, which a join racing a delete could leave behind before every path took the clan lock
--the person could then neither leave it nor join another clan, so it is closed at the moment the clan was deleted
--each one closed gets a MemberRemoved row with no actor, the history table has one row per change
WITH closed AS (
    UPDATE clan_membership m
    SET left_at = GREATEST(c.deleted_at, m.joined_at)
    FROM clan c
    WHERE m.clan_id = c.id
      AND m.left_at IS NULL
      AND c.deleted_at IS NOT NULL
    RETURNING m.id, m.clan_id, m.user_id, m.role, m.joined_at, m.left_at
)
INSERT INTO clan_event (entity_type, entity_id, event_type, payload)
SELECT 'clan_membership', id, 'MemberRemoved',
       jsonb_build_object('id', id, 'clanId', clan_id, 'userId', user_id, 'role', role, 'joinedAt', joined_at, 'leftAt', left_at)
FROM closed;
