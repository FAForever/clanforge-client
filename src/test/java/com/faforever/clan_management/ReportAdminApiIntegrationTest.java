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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.repository.AppUserRepository;
import com.faforever.clan_management.repository.ClanRepository;
import com.faforever.clan_management.service.AdminService;
import com.faforever.clan_management.service.ClanForm;
import com.faforever.clan_management.service.ClanManagementService;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

//the report and admin routes against a real postgres, from the token to the rows, rolled back at the end
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL",matches = ".+")
@Transactional
class ReportAdminApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private ClanRepository clans;
    @Autowired
    private ClanManagementService management;
    @Autowired
    private AdminService admins;
    @Autowired
    private EntityManager em;
    @Autowired
    private ObjectMapper json;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private String freeTag() {
        for (String t : List.of("ZQ9", "ZQ8", "ZQ7", "XQ9", "XQ8")) {
            if (clans.findByTagIgnoreCaseAndDeletedAtIsNull(t).isEmpty()) return t;
        }
        throw new IllegalStateException("every candidate tag is taken");
    }

    // a token for the given subject, with the faforever admin role under ext.roles when asked for
    private String bearer(String sub,boolean asAdmin) {
        Instant now = Instant.now();
        Jwt.Builder b = Jwt.withTokenValue("token-" + sub).header("alg", "RS256").subject(sub).issuedAt(now).expiresAt(now.plusSeconds(300));
        if (asAdmin) b.claim("ext",Map.of("roles", List.of("ADMIN_CLAN")));
        given(jwtDecoder.decode("token-" + sub)).willReturn(b.build());
        return "Bearer token-" + sub;
    }

    private AppUser someone(String role,String stamp) {
        return users.save(new AppUser("api-" + role + "-" + stamp,"api_"+role + "_" + stamp, "local", null));
    }

    @Test
    @DisplayName("a report filed with a token lands in clan_report and shows in the administrators queue, which nobody else can read")
    void reportThenQueue() throws Exception {
        String stamp = Long.toString(System.nanoTime());
        AppUser owner = someone("owner", stamp);
        AppUser reporter = someone("reporter", stamp);
        ClanForm form = new ClanForm();
        form.setName("Reported Clan "+stamp);
        form.setTag(freeTag());
        Clan clan = management.create(owner, form);
        String reporterToken = bearer(reporter.getOidcSubject(), false);
        String adminToken = bearer("api-admin-"+stamp, true);
        MvcResult filed = mockMvc.perform(post("/api/reports").header(HttpHeaders.AUTHORIZATION, reporterToken).contentType(MediaType.APPLICATION_JSON).content("{\"clanId\":" + clan.getId()+",\"category\":\"offensive_tag\",\"reason\":\"  the tag is a slur \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clanId").value(clan.getId()))
                .andExpect(jsonPath("$.reporterId").value(reporter.getId()))
                .andExpect(jsonPath("$.category").value("OFFENSIVE_TAG"))
                .andExpect(jsonPath("$.reason").value("the tag is a slur"))
                .andExpect(jsonPath("$.state").value("OPEN"))
                .andReturn();
        long id = json.readTree(filed.getResponse().getContentAsString()).path("id").asLong();
        em.flush();
        assertThat(em.createNativeQuery("select state || ':' || category || ':' || reporter_id from clan_report where id = :id").setParameter("id", id).getSingleResult()).isEqualTo("OPEN:OFFENSIVE_TAG:" + reporter.getId());
        mockMvc.perform(post("/api/reports").header(HttpHeaders.AUTHORIZATION, reporterToken).contentType(MediaType.APPLICATION_JSON).content("{\"clanId\":" + clan.getId() + ",\"category\":\"OFFENSIVE_TAG\",\"reason\":\"again\"}"))
                .andExpect(status().isConflict()) //a second open report is 409, as the API contract documents
                .andExpect(jsonPath("$.message").value("You have already reported this clan."));
        AppUser another = someone("another", stamp);
        MvcResult newer = mockMvc.perform(post("/api/reports").header(HttpHeaders.AUTHORIZATION, bearer(another.getOidcSubject(), false)).contentType(MediaType.APPLICATION_JSON).content("{\"clanId\":" + clan.getId() + ",\"category\":\"OFFENSIVE_NAME\",\"reason\":\"a later report\"}"))
                .andExpect(status().isCreated()).andReturn();
        long newerId = json.readTree(newer.getResponse().getContentAsString()).path("id").asLong();
        mockMvc.perform(get("/api/admin/reports").header(HttpHeaders.AUTHORIZATION,adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reports[0].id").value(newerId)) //newest first
                .andExpect(jsonPath("$.reports[1].id").value(id))
                .andExpect(jsonPath("$.reports[1].clanTag").value(clan.getTag()))
                .andExpect(jsonPath("$.reports[1].reporterDisplayName").value(reporter.getDisplayName()));
        mockMvc.perform(get("/api/admin/reports").header(HttpHeaders.AUTHORIZATION, reporterToken)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/reports").param("state", "lost").header(HttpHeaders.AUTHORIZATION, adminToken)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/reports")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the admin clan listing finds a private clan, shows a suspension and a deletion, and is closed to everyone else")
    void adminClanListing() throws Exception {
        String stamp = Long.toString(System.nanoTime());
        AppUser owner = someone("owner", stamp);
        ClanForm form = new ClanForm();
        form.setName("Hidden Clan " + stamp);
        form.setTag(freeTag());
        form.setPublicClan(false);
        Clan clan = management.create(owner, form);
        String adminToken = bearer("api-admin-" + stamp,true);
        AppUser admin = users.findByOidcSubject("api-admin-" + stamp).orElseGet(() -> someone("admin",stamp));
        mockMvc.perform(get("/api/clans").param("q", "hidden clan " + stamp)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0)); //the public list never shows it
        mockMvc.perform(get("/api/admin/clans").param("q", "hidden clan " + stamp).header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.clans[0].id").value(clan.getId()))
                .andExpect(jsonPath("$.clans[0].visibility").value("PRIVATE"))
                .andExpect(jsonPath("$.clans[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.clans[0].ownerId").value(owner.getId()))
                .andExpect(jsonPath("$.clans[0].memberCount").value(1));
        admins.suspend(admin, clan.getId(),"checked by the integration test",null);
        mockMvc.perform(get("/api/admin/clans").param("q","hidden clan " + stamp).param("filter", "suspended").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clans[0].status").value("SUSPENDED"));
        mockMvc.perform(get("/api/admin/clans").param("q","hidden clan " + stamp).param("filter","active").header(HttpHeaders.AUTHORIZATION, adminToken)).andExpect(jsonPath("$.totalElements").value(0));
        admins.delete(admin, clan.getId(), null,null);
        mockMvc.perform(get("/api/admin/clans").param("q", "hidden clan " + stamp).param("filter", "deleted").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clans[0].deleted").value(true))
                .andExpect(jsonPath("$.clans[0].memberCount").value(0));
        mockMvc.perform(get("/api/admin/clans").header(HttpHeaders.AUTHORIZATION,bearer(owner.getOidcSubject(), false))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/clans").param("filter", "banned").header(HttpHeaders.AUTHORIZATION, adminToken)).andExpect(status().isBadRequest());
    }
}
