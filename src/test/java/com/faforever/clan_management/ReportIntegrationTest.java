//SPDX-License-Identifier: MIT
package com.faforever.clan_management;

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
    private ReportService reports;
    @Autowired
    private EntityManager em;

    private AppUser someone(String role) {
        String stamp = Long.toString(System.nanoTime());
        return users.save(new AppUser("it-" + role+"-" + stamp, "it_" + role + "_"+stamp, "local", null));
    }

    @Test
    @DisplayName("a report lands in clan_report, heads the open queue, and leaves it once closed")
    void reportIsFiledQueuedAndClosed() {
        AppUser reporter = someone("reporter");
        AppUser admin = someone("admin");
        Clan clan = clans.findAll(PageRequest.of(0, 1)).getContent().get(0);
        long before = reports.byState("OPEN", PageRequest.of(0, 5)).getTotalElements();
        ClanReport filed = reports.file(reporter, clan.getId(), "OFFENSIVE_NAME", "reported by the integration test");
        em.flush();
        assertThat(filed.getId()).isNotNull();
        Page<ClanReport> open = reports.byState("OPEN",PageRequest.of(0, 5));
        //System.out.println("open " + open.getTotalElements());
        assertThat(open.getTotalElements()).isEqualTo(before + 1);
        assertThat(open.getContent().get(0).getId()).isEqualTo(filed.getId());
        assertThat(open.getContent().get(0).getClan().getTag()).isEqualTo(clan.getTag());
        assertThat(em.createNativeQuery("select state || ':' || category from clan_report where id = :id").setParameter("id", filed.getId()).getSingleResult()).isEqualTo("OPEN:OFFENSIVE_NAME");
        assertThatThrownBy(() -> reports.file(reporter, clan.getId(), "OFFENSIVE_TAG","again")).isInstanceOf(ClanRuleViolation.class).hasMessageContaining("already reported");
        reports.close(admin, filed.getId(),false);
        em.flush();
        assertThat(reports.byState("OPEN",PageRequest.of(0, 5)).getTotalElements()).isEqualTo(before);
        assertThat(reports.byState("RESOLVED", PageRequest.of(0,5)).getContent().get(0).getResolvedBy().getId()).isEqualTo(admin.getId());
        assertThat(reports.file(reporter, clan.getId(), "OFFENSIVE_TAG","a second one after the first closed").getId()).isNotEqualTo(filed.getId());
    }

    @Test
    @DisplayName("a moderation row keeps the previous values as jsonb that postgres can read a key out of")
    void moderationRowKeepsPreviousAsJson() {
        AppUser admin = someone("admin");
        Clan clan = clans.findAll(PageRequest.of(0, 1)).getContent().get(0);
        ModerationAction row = actions.saveAndFlush(new ModerationAction(clan, admin, "RENAME", "offensive name", null, "{\"name\": \"Old Name\", \"tag\": \"OLD\"}"));
        //System.out.println("moderation row " + row.getId());
        Object name = em.createNativeQuery("select previous ->> 'name' from moderation_action where id = :id").setParameter("id", row.getId()).getSingleResult();
        assertThat(name).isEqualTo("Old Name");
        assertThat(actions.findByClanIdOrderByPerformedAtDesc(clan.getId()).get(0).getId()).isEqualTo(row.getId());
        assertThat(actions.saveAndFlush(new ModerationAction(clan, admin, "SUSPEND", null, null,null)).getPrevious()).isNull();
    }
}
