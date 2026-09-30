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
import com.faforever.clan_management.entity.ClanReport;
import com.faforever.clan_management.entity.ModerationAction;
import com.faforever.clan_management.repository.AppUserRepository;
import com.faforever.clan_management.repository.ClanRepository;
import com.faforever.clan_management.service.AdminService;
import com.faforever.clan_management.service.ClanCard;
import com.faforever.clan_management.service.ClanForm;
import com.faforever.clan_management.service.ClanManagementService;
import com.faforever.clan_management.service.ClanRuleViolation;
import com.faforever.clan_management.service.InvitationService;
import com.faforever.clan_management.service.MembershipService;
import com.faforever.clan_management.service.ReportService;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

//the admin actions against a real postgres: the clan row, the moderation row, the history row and the report, rolled back at the end
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL",matches = ".+")
@Transactional
class AdminIntegrationTest {

    @Autowired
    private AppUserRepository users;
    @Autowired
    private ClanRepository clans;
    @Autowired
    private ClanManagementService management;
    @Autowired
    private MembershipService memberships;
    @Autowired
    private InvitationService invitations;
    @Autowired
    private ReportService reports;
    @Autowired
    private AdminService admins;
    @Autowired
    private EntityManager em;

    private String freeTag() {
        for (String t : List.of("ZQ9", "ZQ8","ZQ7", "XQ9","XQ8")) {
            if (clans.findByTagIgnoreCaseAndDeletedAtIsNull(t).isEmpty()) return t;
        }
        throw new IllegalStateException("every candidate tag is taken");
    }

    private AppUser someone(String role) {
        String stamp = Long.toString(System.nanoTime());
        return users.save(new AppUser("it-" + role+"-" + stamp, "it_" + role + "_"+stamp,"local", null));
    }

    private Clan openClan(AppUser owner, boolean publicClan) {
        ClanForm form = new ClanForm();
        form.setName("Admin Target "+System.nanoTime());
        form.setTag(freeTag());
        form.setPublicClan(publicClan);
        return management.create(owner, form);
    }

    private Object one(String sql, Long id) {
        em.flush();
        return em.createNativeQuery(sql).setParameter("id",id).getSingleResult();
    }

    @Test
    @DisplayName("suspending on a report leaves the clan suspended, the old status in jsonb, a history event and the report resolved")
    void suspendOnAReport() {
        AppUser owner = someone("owner");
        AppUser reporter = someone("reporter");
        AppUser admin = someone("admin");
        Clan clan = openClan(owner, true);
        ClanReport report = reports.file(reporter, clan.getId(),"OFFENSIVE_NAME", "reported by the integration test");
        ModerationAction row = admins.suspend(admin, clan.getId(), "offensive name", report.getId());
        assertThat(one("select status from clan where id = :id", clan.getId())).isEqualTo("SUSPENDED");
        assertThat(one("select action || ':' || (previous ->> 'status') || ':' || report_id from moderation_action where id = :id", row.getId())).isEqualTo("SUSPEND:ACTIVE:" + report.getId());
        assertThat(one("select state || ':' || resolved_by from clan_report where id = :id", report.getId())).isEqualTo("RESOLVED:" + admin.getId());
        assertThat(one("select (payload ->> 'action') || ':' || (payload ->> 'status') || ':' || actor_id from clan_event where event_type = 'ModerationActionPerformed' and entity_id = :id", clan.getId())).isEqualTo("SUSPEND:SUSPENDED:" + admin.getId());
        //System.out.println("moderation row " + row.getId());
        assertThatThrownBy(() -> memberships.join(clan.getId(), reporter)).isInstanceOf(ClanRuleViolation.class).hasMessageContaining("suspended");
        admins.unsuspend(admin, clan.getId(), null);
        assertThat(memberships.join(clan.getId(), reporter).getRole()).isEqualTo("MEMBER");
        assertThat(admins.history(clan.getId())).extracting(ModerationAction::getAction).containsExactly("UNSUSPEND", "SUSPEND");
    }

    @Test
    @DisplayName("rename and retag meet the same uniqueness rules as an owner, and the old values stay in the moderation rows")
    void renameAndRetag() {
        AppUser admin = someone("admin");
        Clan clan = openClan(someone("owner"), true);
        Clan other = openClan(someone("owner"), true);
        String oldName = clan.getName();
        String oldTag = clan.getTag();
        assertThatThrownBy(() -> admins.rename(admin, clan.getId(), other.getName().toUpperCase(), null,null)).isInstanceOf(ClanRuleViolation.class).hasMessageContaining("already in use");
        assertThatThrownBy(() -> admins.retag(admin,clan.getId(), other.getTag().toLowerCase(),null,null)).isInstanceOf(ClanRuleViolation.class).hasMessageContaining("already in use");
        assertThatThrownBy(() -> admins.retag(admin,clan.getId(), "TOOLONG", null,null)).isInstanceOf(ClanRuleViolation.class).hasMessageContaining("Clan tag must be");
        ModerationAction renamed = admins.rename(admin, clan.getId(),"Renamed "+System.nanoTime(), "offensive name", null);
        String tag = freeTag();
        ModerationAction retagged = admins.retag(admin, clan.getId(), tag, "offensive tag", null);
        assertThat(one("select previous ->> 'name' from moderation_action where id = :id", renamed.getId())).isEqualTo(oldName);
        assertThat(one("select previous ->> 'tag' from moderation_action where id = :id", retagged.getId())).isEqualTo(oldTag);
        assertThat(one("select tag from clan where id = :id", clan.getId())).isEqualTo(tag);
        assertThat(one("select count(*) from clan_event where event_type = 'ModerationActionPerformed' and entity_id = :id", clan.getId())).hasToString("2");
    }

    @Test
    @DisplayName("an admin delete closes the memberships and open invites, frees the tag, and the clan moves to the deleted listing")
    void deleteAndListing() {
        AppUser owner = someone("owner");
        AppUser guest = someone("guest");
        AppUser admin = someone("admin");
        Clan clan = openClan(owner, false);
        String tag = clan.getTag();
        invitations.invite(owner, guest.getDisplayName(), null);
        String q = clan.getName().toLowerCase();
        assertThat(admins.clans(q, "all",PageRequest.of(0, 20)).getContent()).extracting(c -> c.getClan().getId()).containsExactly(clan.getId()); //a private clan, which the public search never shows
        assertThat(admins.clans(q, "active", PageRequest.of(0, 20)).getContent()).extracting(ClanCard::getMemberCount).containsExactly(1L);
        assertThat(admins.clans(q, "deleted", PageRequest.of(0, 20)).getTotalElements()).isZero();
        admins.delete(admin, clan.getId(),"abandoned", null);
        assertThat(one("select count(*) from clan_membership where clan_id = :id and left_at is null",clan.getId())).hasToString("0");
        assertThat(one("select count(*) from clan_invitation where clan_id = :id and state = 'REVOKED'",clan.getId())).hasToString("1");
        assertThat(clans.findByTagIgnoreCaseAndDeletedAtIsNull(tag)).isEmpty();
        assertThat(admins.clans(q,"deleted", PageRequest.of(0, 20)).getContent()).extracting(c -> c.getClan().getId()).containsExactly(clan.getId());
        assertThat(admins.clans(q, "active", PageRequest.of(0, 20)).getTotalElements()).isZero();
        assertThat(admins.clans(q, "all",PageRequest.of(0, 20)).getTotalElements()).isEqualTo(1);
        assertThat(admins.clan(clan.getId())).isPresent();
        assertThatThrownBy(() -> admins.suspend(admin, clan.getId(), null, null)).isInstanceOf(ClanRuleViolation.class).hasMessage("That clan does not exist.");
        //System.out.println("deleted " + clan.getId());
    }

    @Test
    @DisplayName("each action also writes ClanUpdated or ClanDeleted, and a delete settles every report still open on the clan")
    void historyAndReportsAfterActions() {
        AppUser admin = someone("admin");
        AppUser owner = someone("owner");
        Clan clan = openClan(owner, true);
        ClanReport first = reports.file(someone("reporter"), clan.getId(), "OFFENSIVE_NAME","first report");
        ClanReport second = reports.file(someone("reporter"), clan.getId(),"OFFENSIVE_TAG", "second report");
        admins.suspend(admin,clan.getId(),null, null);
        admins.delete(admin, clan.getId(),"abandoned", first.getId());
        //System.out.println("history for " + clan.getId());
        assertThat(one("select string_agg(event_type, ',' order by id) from clan_event where entity_type = 'clan' and entity_id = :id", clan.getId())).isEqualTo("ClanCreated,ClanUpdated,ModerationActionPerformed,ClanDeleted,ModerationActionPerformed");
        assertThat(one("select (payload ->> 'membersRemoved') || ':' || actor_id from clan_event where event_type = 'ClanDeleted' and entity_id = :id", clan.getId())).isEqualTo("1:" + admin.getId());
        assertThat(one("select string_agg(state || ':' || resolved_by, ',' order by id) from clan_report where clan_id = :id", clan.getId())).isEqualTo("RESOLVED:" + admin.getId() + ",RESOLVED:" + admin.getId());
        assertThat(second.getId()).isNotNull();
    }

    @Test
    @DisplayName("the admin listing is in name order whatever order the clans were made in")
    void listingIsInNameOrder() {
        String stamp = Long.toString(System.nanoTime());
        ClanForm later = new ClanForm();
        later.setName("Order " + stamp + " B");
        later.setTag(freeTag());
        management.create(someone("owner"), later);
        ClanForm earlier = new ClanForm();
        earlier.setName("Order " + stamp + " A");
        earlier.setTag(freeTag());
        management.create(someone("owner"), earlier);
        assertThat(admins.clans("order " + stamp, "all", PageRequest.of(0, 20)).getContent()).extracting(c -> c.getClan().getName()).containsExactly("Order " + stamp+" A","Order " + stamp + " B");
    }
}
