# Acceptance Test D — Invitation Lifecycle

## Objective

Verify that the clan invitation lifecycle functions correctly, including invitation creation, acceptance, decline, revocation, expiry, and re-invitation following expiry.

## Test Environment

- Application: FAF Clan Management Platform
- Spring profile: `dev`
- Database: PostgreSQL 16 running in Docker
- Clan: Acceptance Test Clan (`ATD`)
- Clan ID: `1479`
- Clan owner: `admin`
- Invitee: `player`
- Invitation role: `MEMBER`

## Preconditions

Before testing:

- `admin` existed as a local development user and was an active `OWNER` of Acceptance Test Clan.
- `player` existed as a local development user.
- `player` was not initially an active member of Acceptance Test Clan.
- The application was running successfully using the `dev` Spring profile.

## Test Results

| Scenario | Test Action | Expected Result | Actual Result | Status |
|---|---|---|---|---|
| Create invitation | `admin` invited `player` as `MEMBER` | A new `PENDING` invitation is created | Invitation #1 created with state `PENDING` | PASS |
| Accept invitation | `player` accepted invitation #1 | Invitation becomes `ACCEPTED` and membership is created | Invitation #1 became `ACCEPTED`; `player` became an active `MEMBER` | PASS |
| Leave clan | `player` left the clan | Membership is closed while its historical record is retained | Membership remained with `left_at` populated | PASS |
| Decline invitation | `admin` sent invitation #2 and `player` declined it | Invitation becomes `DECLINED` and no membership is created | Invitation #2 became `DECLINED` | PASS |
| Revoke invitation | `admin` sent invitation #3 and revoked it before response | Invitation becomes `REVOKED` | Invitation #3 became `REVOKED` | PASS |
| Expire invitation | Invitation #4 was assigned an `expires_at` timestamp in the past | Expired invitation is no longer actionable | Invitation #4 disappeared from `player`'s invitation inbox | PASS |
| Re-invite after expiry | `admin` invited `player` again after #4 expired | Previous invitation becomes `EXPIRED` and a new `PENDING` invitation is created | Invitation #4 became `EXPIRED`; invitation #5 was created as `PENDING` | PASS |

## Database Verification

The invitation lifecycle was verified against the `clan_invitation` table.

The resulting invitation states were:

```text
#1  ACCEPTED
#2  DECLINED
#3  REVOKED
#4  EXPIRED
#5  PENDING
```

Acceptance was also verified against `clan_membership`. After invitation #1 was accepted, `player` received an active `MEMBER` membership in clan `1479`.

When `player` subsequently left the clan, the membership record was retained and its `left_at` field was populated rather than the record being deleted.

The expiry test confirmed that an invitation whose `expires_at` timestamp had passed was excluded from the player's active invitation list. When a replacement invitation was subsequently issued, the expired invitation transitioned from `PENDING` to `EXPIRED`.

## Result

**PASS**

The invitation lifecycle operates as expected. Clan managers can create and revoke invitations, while invitees can accept or decline them. Accepted invitations create the appropriate clan membership, expired invitations are no longer presented as actionable, and historical invitation and membership records are retained for audit purposes.