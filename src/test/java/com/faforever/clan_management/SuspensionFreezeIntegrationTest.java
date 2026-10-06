//SPDX-License-Identifier: MIT
package com.faforever.clan_management;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.repository.AppUserRepository;
import com.faforever.clan_management.repository.ClanRepository;
import com.faforever.clan_management.service.AdminService;
import com.faforever.clan_management.service.ClanForm;
import com.faforever.clan_management.service.ClanManagementService;
import com.faforever.clan_management.service.ClanRuleViolation;
import com.faforever.clan_management.service.ClanService;
import com.faforever.clan_management.service.InvitationService;
import com.faforever.clan_management.service.MembershipService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// A suspended clan is frozen and hidden, against a real postgres: once an administrator
// suspends it, its owner and moderators can no longer change it and it drops out of the
// public search, detail and stats; a member may still leave; and lifting the suspension
// restores all of it. Rolled back at the end.
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
@Transactional
class SuspensionFreezeIntegrationTest {

    @Autowired private AppUserRepository users;
    @Autowired private ClanRepository clans;
    @Autowired private ClanManagementService management;
    @Autowired private MembershipService memberships;
    @Autowired private InvitationService invitations;
    @Autowired private ClanService clanService;
    @Autowired private AdminService admins;

    private String freeTag() {
        for (String t : List.of("ZF9", "ZF8", "ZF7", "XF9", "XF8", "XF7")) {
            if (clans.findByTagIgnoreCaseAndDeletedAtIsNull(t).isEmpty()) return t;
        }
        throw new IllegalStateException("every candidate tag is taken");
    }

    private AppUser someone(String role, String stamp) {
        return users.save(new AppUser("frz-" + role + "-" + stamp, "frz_" + role + "_" + stamp, "local", null));
    }

    private Clan publicOpenClan(AppUser owner, String stamp) {
        ClanForm form = new ClanForm();
        form.setName("Freeze Clan " + stamp);
        form.setTag(freeTag());
        form.setPublicClan(true);        // visible in the public search
        form.setRequiresInvitation(false); // open, so a member can join without an invite
        return management.create(owner, form);
    }

    private boolean inPublicSearch(String name) {
        return clanService.searchPublicClanCards(name, "all", "name", PageRequest.of(0, 20)).getTotalElements() > 0;
    }

    @Test
    @DisplayName("once suspended, every owner change is refused, the clan is hidden from the public, a member can still leave, and lifting the suspension restores it all")
    void suspendFreezesAndHides() {
        String stamp = Long.toString(System.nanoTime());
        AppUser owner = someone("owner", stamp);
        AppUser member = someone("member", stamp);
        AppUser outsider = someone("outsider", stamp);
        AppUser admin = someone("admin", stamp);
        Clan clan = publicOpenClan(owner, stamp);
        long id = clan.getId();
        memberships.join(id, member); // joins while the clan is still active

        // while active: visible to the public, owner can edit
        assertThat(inPublicSearch("Freeze Clan " + stamp)).isTrue();
        assertThat(clanService.findVisibleClan(id)).isPresent();
        management.updateMotd(owner, "welcome");

        admins.suspend(admin, id, "offensive content", null);

        // every owner / moderator change is now refused with the suspended sentence
        assertThatThrownBy(() -> management.updateMotd(owner, "edit while suspended"))
                .isInstanceOf(ClanRuleViolation.class).hasMessageContaining("suspended");
        assertThatThrownBy(() -> invitations.invite(owner, outsider.getDisplayName(), "MEMBER"))
                .isInstanceOf(ClanRuleViolation.class).hasMessageContaining("suspended");
        assertThatThrownBy(() -> memberships.setRole(owner, member.getId(), "MODERATOR"))
                .isInstanceOf(ClanRuleViolation.class).hasMessageContaining("suspended");
        assertThatThrownBy(() -> management.transferOwnership(owner, member.getId()))
                .isInstanceOf(ClanRuleViolation.class).hasMessageContaining("suspended");
        assertThatThrownBy(() -> management.delete(owner))
                .isInstanceOf(ClanRuleViolation.class).hasMessageContaining("suspended");

        // hidden from the public: search, detail and the browse count all drop it
        assertThat(inPublicSearch("Freeze Clan " + stamp)).isFalse();
        assertThat(clanService.findVisibleClan(id)).isEmpty();

        // a member is not trapped: leaving still works
        memberships.leave(member);
        assertThat(memberships.activeMembership(member)).isEmpty();

        // lifting the suspension restores editing and public visibility
        admins.unsuspend(admin, id, "appeal upheld");
        management.updateMotd(owner, "back"); // no longer throws
        assertThat(inPublicSearch("Freeze Clan " + stamp)).isTrue();
        assertThat(clanService.findVisibleClan(id)).isPresent();
    }

    @Test
    @DisplayName("an administrator can still rename, retag and delete a suspended clan: the freeze is on the owner, not on moderation")
    void adminStillActsOnASuspendedClan() {
        String stamp = Long.toString(System.nanoTime());
        AppUser owner = someone("owner", stamp);
        AppUser admin = someone("admin", stamp);
        Clan clan = publicOpenClan(owner, stamp);
        long id = clan.getId();

        admins.suspend(admin, id, null, null);
        // the admin path does not go through the owner/mod gate, so these still work
        admins.rename(admin, id, "Renamed While Suspended " + stamp, "cleanup", null);
        admins.retag(admin, id, freeTag(), "cleanup", null);
        admins.delete(admin, id, "removed", null);

        assertThat(clans.findById(id).orElseThrow().getDeletedAt()).isNotNull();
    }
}
