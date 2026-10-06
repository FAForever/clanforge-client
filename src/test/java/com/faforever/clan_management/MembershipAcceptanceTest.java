package com.faforever.clan_management;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.entity.ClanMembership;
import com.faforever.clan_management.repository.AppUserRepository;
import com.faforever.clan_management.repository.ClanMembershipRepository;
import com.faforever.clan_management.repository.ClanRepository;
import com.faforever.clan_management.service.ClanForm;
import com.faforever.clan_management.service.ClanManagementService;
import com.faforever.clan_management.service.ClanRuleViolation;
import com.faforever.clan_management.service.MembershipService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
@Transactional
class MembershipAcceptanceTest {

    @Autowired
    private AppUserRepository users;

    @Autowired
    private ClanRepository clans;

    @Autowired
    private ClanMembershipRepository membershipRecords;

    @Autowired
    private ClanManagementService clanManagement;

    @Autowired
    private MembershipService memberships;

    @Test
    @DisplayName("Test C: membership management rules work as one complete workflow")
    void membershipManagementWorkflow() {
        String stamp = Long.toString(System.nanoTime());
        AppUser ownerA = saveUser("owner-a", stamp);
        AppUser ownerB = saveUser("owner-b", stamp);
        AppUser privateOwner = saveUser("private-owner", stamp);
        AppUser testMember = saveUser("member", stamp);
        AppUser nextOwner = saveUser("next-owner", stamp);

        Clan clanA = createClan(ownerA, "Acceptance Clan A " + stamp, freeTag(), true, false);
        Clan clanB = createClan(ownerB, "Acceptance Clan B " + stamp, freeTag(), true, false);
        Clan privateClan = createClan(
                privateOwner,
                "Acceptance Private Clan " + stamp,
                freeTag(),
                false,
                true
        );

        ClanMembership joinedA = memberships.join(clanA.getId(), testMember);
        assertThat(joinedA.getRole()).isEqualTo("MEMBER");
        assertThat(membershipRecords.findActiveByUserId(testMember.getId()))
                .get()
                .extracting(membership -> membership.getClan().getId())
                .isEqualTo(clanA.getId());

        assertThatThrownBy(() -> memberships.join(clanB.getId(), testMember))
                .isInstanceOf(ClanRuleViolation.class)
                .hasMessageContaining("Leave your current clan");

        memberships.leave(testMember);
        assertThat(membershipRecords.findActiveByUserId(testMember.getId())).isEmpty();

        ClanMembership joinedB = memberships.join(clanB.getId(), testMember);
        assertThat(joinedB.getRole()).isEqualTo("MEMBER");
        assertThat(joinedB.getClan().getId()).isEqualTo(clanB.getId());

        memberships.removeMember(ownerB, testMember.getId());
        assertThat(membershipRecords.findActiveByUserId(testMember.getId())).isEmpty();

        memberships.join(clanB.getId(), nextOwner);
        clanManagement.transferOwnership(ownerB, nextOwner.getId());
        assertThat(clans.findById(clanB.getId()).orElseThrow().getOwner().getId())
                .isEqualTo(nextOwner.getId());
        assertThat(memberships.roster(clanB))
                .extracting(member -> member.getUser().getId() + ":" + member.getRole())
                .containsExactly(
                        nextOwner.getId() + ":OWNER",
                        ownerB.getId() + ":MODERATOR"
                );

        assertThatThrownBy(() -> memberships.join(privateClan.getId(), testMember))
                .isInstanceOf(ClanRuleViolation.class)
                .hasMessageContaining("by invitation");
    }

    private AppUser saveUser(String role, String stamp) {
        return users.save(new AppUser(
                "acceptance-" + role + "-" + stamp,
                "test-" + role + "-" + stamp,
                "local",
                null
        ));
    }

    private Clan createClan(
            AppUser owner,
            String name,
            String tag,
            boolean publicClan,
            boolean requiresInvitation
    ) {
        ClanForm form = new ClanForm();
        form.setName(name);
        form.setTag(tag);
        form.setPublicClan(publicClan);
        form.setRequiresInvitation(requiresInvitation);
        return clanManagement.create(owner, form);
    }

    private String freeTag() {
        for (String tag : List.of("TC1", "TC2", "TC3", "TC4", "TC5")) {
            if (clans.findByTagIgnoreCaseAndDeletedAtIsNull(tag).isEmpty()) {
                return tag;
            }
        }
        throw new IllegalStateException("No free acceptance-test clan tag is available.");
    }
}
