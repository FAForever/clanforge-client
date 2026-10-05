//SPDX-License-Identifier: MIT
package com.faforever.clan_management;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.entity.ClanReport;
import com.faforever.clan_management.repository.AppUserRepository;
import com.faforever.clan_management.repository.ClanRepository;
import com.faforever.clan_management.service.ClanForm;
import com.faforever.clan_management.service.ClanManagementService;
import com.faforever.clan_management.service.ReportService;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The admin pages driven like a person would drive them, against a real postgres: find the
// clan, open it, act on a report, rename it, dismiss another report, delete it, with the clan,
// report and moderation rows checked after each step. Rolled back at the end.
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
@Transactional
class AdminPagesIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private ClanRepository clans;
    @Autowired
    private ClanManagementService management;
    @Autowired
    private ReportService reports;
    @Autowired
    private EntityManager em;

    private String freeTag() {
        for (String t : List.of("ZW9", "ZW8", "ZW7", "XW9", "XW8", "XW7")) {
            if (clans.findByTagIgnoreCaseAndDeletedAtIsNull(t).isEmpty()) return t;
        }
        throw new IllegalStateException("every candidate tag is taken");
    }

    private AppUser someone(String role, String stamp) {
        return users.save(new AppUser("web-" + role + "-" + stamp, "web_" + role + "_" + stamp, "local", null));
    }

    // signed in on the website with the admin role, as the dev admin account or OidcRoles leaves an administrator
    private static RequestPostProcessor admin(String stamp) {
        return user("web-admin-" + stamp).roles("USER", "ADMIN_CLAN");
    }

    private Object column(String sql, long id) {
        em.flush();
        return em.createNativeQuery(sql).setParameter("id", id).getSingleResult();
    }

    @Test
    @DisplayName("an administrator finds a clan, acts on its report, renames it, dismisses a second report and deletes it, all from the pages")
    void moderationRoundTrip() throws Exception {
        String stamp = Long.toString(System.nanoTime());
        AppUser owner = someone("owner", stamp);
        String oldName = "Admin Web Clan " + stamp;
        ClanForm form = new ClanForm();
        form.setName(oldName);
        form.setTag(freeTag());
        Clan clan = management.create(owner, form);
        long id = clan.getId();
        String page = "/admin/clans/" + id;
        ClanReport first = reports.file(someone("reporter", stamp), id, "OFFENSIVE_NAME", "the name is a slur " + stamp);

        // find it in the list, then open it
        mockMvc.perform(get("/admin/clans").param("q", oldName).with(admin(stamp)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(oldName)))
                .andExpect(content().string(containsString("href=\"" + page + "\"")));
        mockMvc.perform(get(page).with(admin(stamp)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("the name is a slur " + stamp)))
                .andExpect(content().string(containsString("Suspend Clan")))
                .andExpect(content().string(containsString("Answers report")));

        // suspend it in answer to the report: the clan is suspended, the report resolved, one moderation row written
        mockMvc.perform(post(page + "/suspend").with(admin(stamp)).with(csrf()).param("reason", "acting on the report").param("reportId", first.getId().toString()))
                .andExpect(redirectedUrl(page))
                .andExpect(flash().attribute("notice", containsString("Clan suspended")));
        assertThat(column("select status from clan where id = :id", id)).isEqualTo("SUSPENDED");
        assertThat(column("select state from clan_report where id = :id", first.getId())).isEqualTo("RESOLVED");
        assertThat(((Number) column("select count(*) from moderation_action where clan_id = :id and action = 'SUSPEND'", id)).longValue()).isEqualTo(1);
        mockMvc.perform(get(page).with(admin(stamp)))
                .andExpect(content().string(containsString("Lift Suspension")))
                .andExpect(content().string(containsString("acting on the report")))
                .andExpect(content().string(containsString("resolved report #" + first.getId())))
                .andExpect(content().string(not(containsString("Suspend Clan"))));

        // suspending again is refused with the service's sentence, and writes nothing
        mockMvc.perform(post(page + "/unsuspend").with(admin(stamp)).with(csrf())).andExpect(redirectedUrl(page));
        mockMvc.perform(post(page + "/unsuspend").with(admin(stamp)).with(csrf()))
                .andExpect(flash().attribute("error", "That clan is not suspended."));
        assertThat(column("select status from clan where id = :id", id)).isEqualTo("ACTIVE");

        // rename it: the stored name changes and the history remembers the old one
        String newName = "Renamed Clan " + stamp;
        mockMvc.perform(post(page + "/rename").with(admin(stamp)).with(csrf()).param("name", newName).param("reason", "was offensive").param("reportId", ""))
                .andExpect(flash().attribute("notice", "Clan renamed."));
        assertThat(column("select name from clan where id = :id", id)).isEqualTo(newName);
        mockMvc.perform(get(page).with(admin(stamp)))
                .andExpect(content().string(containsString(newName)))
                .andExpect(content().string(containsString("timeline-before")))
                .andExpect(content().string(containsString(oldName)));

        // a second report, thrown out from the clan's page
        ClanReport second = reports.file(someone("other", stamp), id, "INAPPROPRIATE_CONTENT", "not really " + stamp);
        mockMvc.perform(post("/admin/reports/" + second.getId() + "/dismiss").with(admin(stamp)).with(csrf()).param("clanId", Long.toString(id)))
                .andExpect(redirectedUrl(page));
        assertThat(column("select state from clan_report where id = :id", second.getId())).isEqualTo("DISMISSED");
        mockMvc.perform(get("/admin/reports").param("state", "dismissed").with(admin(stamp)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("not really " + stamp)));

        // delete it: the row stays with deleted_at, nobody is left in it, and the page offers no more actions
        mockMvc.perform(post(page + "/delete").with(admin(stamp)).with(csrf()).param("reason", "spam ring"))
                .andExpect(flash().attribute("notice", containsString("Clan deleted")));
        assertThat(((Number) column("select count(*) from clan where id = :id and deleted_at is not null", id)).longValue()).isEqualTo(1);
        assertThat(((Number) column("select count(*) from clan_membership where clan_id = :id and left_at is null", id)).longValue()).isZero();
        mockMvc.perform(get(page).with(admin(stamp)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Clan Deleted")))
                .andExpect(content().string(not(containsString("Delete Clan"))));
        mockMvc.perform(get("/admin/clans").param("q", newName).param("filter", "deleted").with(admin(stamp)))
                .andExpect(content().string(containsString(newName)));
    }

    @Test
    @DisplayName("a signed-in player cannot open the pages or act, and the clan is left as it was")
    void playersAreKeptOut() throws Exception {
        String stamp = Long.toString(System.nanoTime());
        AppUser owner = someone("owner", stamp);
        ClanForm form = new ClanForm();
        form.setName("Guarded Clan " + stamp);
        form.setTag(freeTag());
        long id = management.create(owner, form).getId();
        RequestPostProcessor player = user(owner.getOidcSubject()).roles("USER");

        mockMvc.perform(get("/admin/clans").with(player)).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/clans/" + id).with(player)).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/clans/" + id + "/delete").with(player).with(csrf())).andExpect(status().isForbidden());
        assertThat(((Number) column("select count(*) from clan where id = :id and deleted_at is null and status = 'ACTIVE'", id)).longValue()).isEqualTo(1);
        assertThat(((Number) column("select count(*) from moderation_action where clan_id = :id", id)).longValue()).isZero();
    }
}
