//SPDX-License-Identifier: MIT
package com.faforever.clan_management;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.entity.ClanReport;
import com.faforever.clan_management.entity.ModerationAction;
import com.faforever.clan_management.repository.AppUserRepository;
import com.faforever.clan_management.repository.ClanRepository;
import com.faforever.clan_management.repository.ModerationActionRepository;
import com.faforever.clan_management.service.ClanForm;
import com.faforever.clan_management.service.ClanManagementService;
import com.faforever.clan_management.service.ClanRuleViolation;
import com.faforever.clan_management.service.ReportService;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

//reports and moderation rows against a real postgres, the two tables nothing wrote to before, rolled back at the end
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
@Transactional
class ReportIntegrationTest {

    @Autowired
    private AppUserRepository users;
    @Autowired
    private ClanRepository clans;
    @Autowired
    private ModerationActionRepository actions;
    @Autowired
    private ClanManagementService management;
    @Autowired
    private ReportService reports;
    @Autowired
    private EntityManager em;

    private String freeTag() {
        for (String t : List.of("ZQ9", "ZQ8", "ZQ7", "XQ9", "XQ8")) {
            if (clans.findByTagIgnoreCaseAndDeletedAtIsNull(t).isEmpty()) return t;
        }
        throw new IllegalStateException("every candidate tag is taken");
    }

    private AppUser someone(String role) {
        String stamp = Long.toString(System.nanoTime());
        return users.save(new AppUser("it-"+role + "-" + stamp, "it_" + role + "_" + stamp, "local",null));
    }

    //a clan made by the test itself, so nothing rests on what the loaded data happens to hold
    private Clan clanOf(AppUser owner) {
        ClanForm form = new ClanForm();
        form.setName("Report Target " + System.nanoTime());
        form.setTag(freeTag());
        return management.create(owner, form);
    }

    @Test
    @DisplayName("a report lands in clan_report, the queue lists the newest first, and a closed one leaves it")
    void reportIsFiledQueuedAndClosed() {
        AppUser reporter = someone("reporter");
        AppUser second = someone("second");
        AppUser admin = someone("admin");
        Clan clan = clanOf(someone("owner"));
        long before = reports.byState("OPEN", PageRequest.of(0, 5)).getTotalElements();
        ClanReport filed = reports.file(reporter, clan.getId(), "OFFENSIVE_NAME", "reported by the integration test");
        em.flush();
        ClanReport later = reports.file(second, clan.getId(), "OFFENSIVE_TAG", "reported a moment later");
        em.flush();
        assertThat(filed.getId()).isNotNull();
        Page<ClanReport> open = reports.byState("OPEN", PageRequest.of(0, 5));
        //System.out.println("open " + open.getTotalElements());
        assertThat(open.getTotalElements()).isEqualTo(before+2);
        assertThat(open.getContent()).extracting(ClanReport::getId).startsWith(later.getId(),filed.getId());
        assertThat(reports.byState("ALL", PageRequest.of(0, 5)).getContent()).extracting(ClanReport::getId).startsWith(later.getId(), filed.getId());
        assertThat(em.createNativeQuery("select state || ':' || category from clan_report where id = :id").setParameter("id", filed.getId()).getSingleResult()).isEqualTo("OPEN:OFFENSIVE_NAME");
        assertThatThrownBy(() -> reports.file(reporter, clan.getId(), "OFFENSIVE_TAG", "again")).isInstanceOf(ClanRuleViolation.class).hasMessageContaining("already reported");
        reports.close(admin,filed.getId(), false);
        em.flush();
        assertThat(reports.byState("OPEN", PageRequest.of(0,5)).getContent()).extracting(ClanReport::getId).doesNotContain(filed.getId());
        assertThat(reports.byState("RESOLVED", PageRequest.of(0, 5)).getContent().get(0).getResolvedBy().getId()).isEqualTo(admin.getId());
        assertThat(reports.file(reporter, clan.getId(),"OFFENSIVE_TAG", "a second one after the first closed").getId()).isNotEqualTo(filed.getId());
    }

    @Test
    @DisplayName("the database itself holds one open report per person per clan, for when two requests pass the check together")
    void deletedClanAndTheOpenReportIndex() {
        AppUser owner = someone("owner");
        AppUser reporter = someone("reporter");
        Clan clan = clanOf(owner);
        reports.file(reporter, clan.getId(), "OFFENSIVE_NAME", "first");
        em.flush();
        //the same row a racing request would insert after both passed the check in ReportService
        assertThatThrownBy(() -> {
            em.createNativeQuery("insert into clan_report (clan_id, reporter_id, category, reason) values (:c, :r, 'OFFENSIVE_TAG', 'raced')").setParameter("c", clan.getId()).setParameter("r",reporter.getId()).executeUpdate();
        }).hasMessageContaining("clan_report_one_open");
    }

    @Test
    @DisplayName("once the owner deletes the clan it can no longer be reported")
    void deletedClanCannotBeReported() {
        AppUser owner = someone("owner");
        Clan clan = clanOf(owner);
        management.delete(owner);
        em.flush();
        assertThatThrownBy(() -> reports.file(someone("reporter"), clan.getId(), "OFFENSIVE_NAME", "too late")).isInstanceOf(ClanRuleViolation.class).hasMessage("That clan does not exist.");
    }

    @Test
    @DisplayName("a moderation row keeps the previous values as jsonb that postgres can read a key out of")
    void moderationRowKeepsPreviousAsJson() {
        AppUser admin = someone("admin");
        Clan clan = clanOf(someone("owner"));
        ModerationAction row = actions.saveAndFlush(new ModerationAction(clan, admin, "RENAME", "offensive name", null, "{\"name\": \"Old Name\", \"tag\": \"OLD\"}"));
        //System.out.println("moderation row " + row.getId());
        Object name = em.createNativeQuery("select previous ->> 'name' from moderation_action where id = :id").setParameter("id", row.getId()).getSingleResult();
        assertThat(name).isEqualTo("Old Name");
        assertThat(actions.findByClanIdOrderByPerformedAtDesc(clan.getId()).get(0).getId()).isEqualTo(row.getId());
        assertThat(actions.saveAndFlush(new ModerationAction(clan, admin, "SUSPEND", null, null,null)).getPrevious()).isNull();
    }
}
