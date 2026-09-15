package com.faforever.clan_management.service;

import java.time.OffsetDateTime;

import org.springframework.test.util.ReflectionTestUtils;

import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.entity.ClanInvitation;
import com.faforever.clan_management.entity.ClanMembership;

//entities with ids, which only the database hands out, so the unit tests set them by reflection
final class Fixtures {

    private Fixtures() {
    }

    static AppUser user(long id,String name) {
        AppUser u = new AppUser("sub-" + id, name,"local", null);
        ReflectionTestUtils.setField(u,"id", id);
        return u;
    }

    static Clan clan(long id,String name,String tag, AppUser owner,boolean requiresInvitation) {
        Clan c = new Clan(name, tag,null, owner, "PUBLIC", requiresInvitation);
        ReflectionTestUtils.setField(c, "id", id);
        //System.out.println("clan " + id + " " + tag);
        return c;
    }

    static ClanMembership membership(long id, Clan clan,AppUser user, String role) {
        ClanMembership m = new ClanMembership(clan, user, role);
        ReflectionTestUtils.setField(m,"id", id);
        return m;
    }

    static ClanInvitation invitation(long id,Clan clan,AppUser invitee,AppUser inviter, OffsetDateTime expiresAt) {
        ClanInvitation i = new ClanInvitation(clan, invitee, inviter, expiresAt, "MEMBER");
        ReflectionTestUtils.setField(i, "id", id);
        return i;
    }
}
