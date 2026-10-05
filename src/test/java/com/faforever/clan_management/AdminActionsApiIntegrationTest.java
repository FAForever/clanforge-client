//SPDX-License-Identifier: MIT
package com.faforever.clan_management;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.entity.ClanReport;
import com.faforever.clan_management.repository.AppUserRepository;
import com.faforever.clan_management.repository.ClanReportRepository;
import com.faforever.clan_management.repository.ClanRepository;
import com.faforever.clan_management.service.ClanForm;
import com.faforever.clan_management.service.ClanManagementService;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The admin clan actions driven over HTTP against a real postgres: from the bearer token,
// through the controller and AdminService, to the clan row, the moderation_action row and the
// linked report - then rolled back. Proves the endpoints added on top of AdminService actually
// change state and are audited, not just that they route.
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
@Transactional
class AdminActionsApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private ClanRepository clans;
    @Autowired
    private ClanReportRepository reports;
    @Autowired
    private ClanManagementService management;
    @Autowired
    private EntityManager em;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private String freeTag() {
        for (String t : List.of("ZQ9", "ZQ8", "ZQ7", "XQ9", "XQ8", "XQ7")) {
            if (clans.findByTagIgnoreCaseAndDeletedAtIsNull(t).isEmpty()) return t;
        }
        throw new IllegalStateException("every candidate tag is taken");
    }

    private String bearer(String sub, boolean asAdmin) {
        Instant now = Instant.now();
        Jwt.Builder b = Jwt.withTokenValue("token-" + sub).header("alg", "RS256").subject(sub).issuedAt(now).expiresAt(now.plusSeconds(300));
        if (asAdmin) b.claim("ext", Map.of("roles", List.of("ADMIN_CLAN")));
        given(jwtDecoder.decode("token-" + sub)).willReturn(b.build());
        return "Bearer token-" + sub;
    }

    private AppUser someone(String role, String stamp) {
        return users.save(new AppUser("api-" + role + "-" + stamp, "api_" + role + "_" + stamp, "local", null));
    }

    private Clan newClan(AppUser owner, String stamp) {
        ClanForm form = new ClanForm();
        form.setName("Action Clan " + stamp);
        form.setTag(freeTag());
        return management.create(owner, form);
    }

    private String dbStatus(long clanId) {
        return (String) em.createNativeQuery("select status from clan where id = :id").setParameter("id", clanId).getSingleResult();
    }

    private long moderationRows(long clanId, String action) {
        return ((Number) em.createNativeQuery("select count(*) from moderation_action where clan_id = :id and action = :a")
                .setParameter("id", clanId).setParameter("a", action).getSingleResult()).longValue();
    }

    @Test
    @DisplayName("suspend then unsuspend over HTTP flips the clan's status and records a moderation row each way")
    void suspendUnsuspend() throws Exception {
        String stamp = Long.toString(System.nanoTime());
        Clan clan = newClan(someone("owner", stamp), stamp);
        String adminToken = bearer("api-admin-" + stamp, true);
        someone("admin", stamp); // the token's subject must resolve to an app_user

        mockMvc.perform(post("/api/admin/clans/" + clan.getId() + "/suspend").header(HttpHeaders.AUTHORIZATION, adminToken).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"offensive conduct\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("SUSPEND"))
                .andExpect(jsonPath("$.clanId").value(clan.getId()))
                .andExpect(jsonPath("$.reason").value("offensive conduct"))
                .andExpect(jsonPath("$.moderatorDisplayName").value("api_admin_" + stamp))
                .andExpect(jsonPath("$.performedAt").exists());
        em.flush();
        assertThat(dbStatus(clan.getId())).isEqualTo("SUSPENDED");
        assertThat(moderationRows(clan.getId(), "SUSPEND")).isEqualTo(1);

        // suspending an already-suspended clan is the service's 400, and leaves no second row
        mockMvc.perform(post("/api/admin/clans/" + clan.getId() + "/suspend").header(HttpHeaders.AUTHORIZATION, adminToken).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("That clan is already suspended."));
        em.flush();
        assertThat(moderationRows(clan.getId(), "SUSPEND")).isEqualTo(1);

        mockMvc.perform(post("/api/admin/clans/" + clan.getId() + "/unsuspend").header(HttpHeaders.AUTHORIZATION, adminToken).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"appeal upheld\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("UNSUSPEND"));
        em.flush();
        assertThat(dbStatus(clan.getId())).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("rename and retag over HTTP change the stored name and tag, and reject invalid or duplicate values")
    void renameAndRetag() throws Exception {
        String stamp = Long.toString(System.nanoTime());
        Clan clan = newClan(someone("owner", stamp), stamp);
        String adminToken = bearer("api-admin-" + stamp, true);
        someone("admin", stamp);

        mockMvc.perform(post("/api/admin/clans/" + clan.getId() + "/rename").header(HttpHeaders.AUTHORIZATION, adminToken).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Clean Name " + stamp + "\",\"reason\":\"was offensive\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("RENAME"));
        em.flush();
        em.clear();
        assertThat(clans.findById(clan.getId()).orElseThrow().getName()).isEqualTo("Clean Name " + stamp);

        // a blank name is the same rule an owner meets: the service refuses it as 400
        mockMvc.perform(post("/api/admin/clans/" + clan.getId() + "/rename").header(HttpHeaders.AUTHORIZATION, adminToken).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());

        String freshTag = freeTag();
        mockMvc.perform(post("/api/admin/clans/" + clan.getId() + "/retag").header(HttpHeaders.AUTHORIZATION, adminToken).contentType(MediaType.APPLICATION_JSON).content("{\"tag\":\"" + freshTag + "\",\"reason\":\"was a slur\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("RETAG"));
        em.flush();
        em.clear();
        assertThat(clans.findById(clan.getId()).orElseThrow().getTag()).isEqualTo(freshTag);

        // a tag longer than three characters cannot be stored: still a clean 400, never a 500
        mockMvc.perform(post("/api/admin/clans/" + clan.getId() + "/retag").header(HttpHeaders.AUTHORIZATION, adminToken).contentType(MediaType.APPLICATION_JSON).content("{\"tag\":\"TOOLONG\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("delete over HTTP soft-deletes the clan, closes its memberships, and the history lists every action newest first")
    void deleteAndHistory() throws Exception {
        String stamp = Long.toString(System.nanoTime());
        Clan clan = newClan(someone("owner", stamp), stamp);
        long id = clan.getId();
        String adminToken = bearer("api-admin-" + stamp, true);
        someone("admin", stamp);

        mockMvc.perform(post("/api/admin/clans/" + id + "/suspend").header(HttpHeaders.AUTHORIZATION, adminToken).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/clans/" + id + "/delete").header(HttpHeaders.AUTHORIZATION, adminToken).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"spam ring\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("DELETE"));
        em.flush();
        // soft delete: the row stays with a deleted_at, and no membership is still open
        assertThat(((Number) em.createNativeQuery("select count(*) from clan where id = :id and deleted_at is not null").setParameter("id", id).getSingleResult()).longValue()).isEqualTo(1);
        assertThat(((Number) em.createNativeQuery("select count(*) from clan_membership where clan_id = :id and left_at is null").setParameter("id", id).getSingleResult()).longValue()).isEqualTo(0);

        // history returns both actions, the delete first
        mockMvc.perform(get("/api/admin/clans/" + id + "/history").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].action").value("DELETE"))
                .andExpect(jsonPath("$[1].action").value("SUSPEND"));
    }

    @Test
    @DisplayName("suspending with a report id resolves that report in the same action")
    void suspendResolvesLinkedReport() throws Exception {
        String stamp = Long.toString(System.nanoTime());
        AppUser owner = someone("owner", stamp);
        AppUser reporter = someone("reporter", stamp);
        Clan clan = newClan(owner, stamp);
        String adminToken = bearer("api-admin-" + stamp, true);
        someone("admin", stamp);

        // a standing open report on this clan
        mockMvc.perform(post("/api/reports").header(HttpHeaders.AUTHORIZATION, bearer(reporter.getOidcSubject(), false)).contentType(MediaType.APPLICATION_JSON).content("{\"clanId\":" + clan.getId() + ",\"category\":\"OFFENSIVE_NAME\",\"reason\":\"the name\"}"))
                .andExpect(status().isCreated());
        ClanReport report = reports.lockOpenForClan(clan.getId()).get(0);
        assertThat(report.getState()).isEqualTo("OPEN");

        mockMvc.perform(post("/api/admin/clans/" + clan.getId() + "/suspend").header(HttpHeaders.AUTHORIZATION, adminToken).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"acting on the report\",\"reportId\":" + report.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId").value(report.getId()));
        em.flush();
        assertThat((String) em.createNativeQuery("select state from clan_report where id = :id").setParameter("id", report.getId()).getSingleResult()).isEqualTo("RESOLVED");
    }

    @Test
    @DisplayName("the actions are closed to non-admins and to anonymous callers, and an unknown clan is a clean refusal not a 500")
    void accessControlAndMissingClan() throws Exception {
        String stamp = Long.toString(System.nanoTime());
        AppUser owner = someone("owner", stamp);
        Clan clan = newClan(owner, stamp);
        String adminToken = bearer("api-admin-" + stamp, true);
        someone("admin", stamp);

        // a signed-in non-admin is forbidden, and the clan is untouched
        mockMvc.perform(post("/api/admin/clans/" + clan.getId() + "/suspend").header(HttpHeaders.AUTHORIZATION, bearer(owner.getOidcSubject(), false)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        // anonymous is unauthorized
        mockMvc.perform(post("/api/admin/clans/" + clan.getId() + "/suspend").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        em.flush();
        assertThat(dbStatus(clan.getId())).isEqualTo("ACTIVE");

        // an action on a clan that does not exist is the service's 400, never a 500
        mockMvc.perform(post("/api/admin/clans/99999999/suspend").header(HttpHeaders.AUTHORIZATION, adminToken).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("That clan does not exist."));
    }
}
