//SPDX-License-Identifier: MIT
package com.faforever.clan_management.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.entity.ClanReport;
import com.faforever.clan_management.security.SecurityConfig;
import com.faforever.clan_management.service.AdminAccess;
import com.faforever.clan_management.service.AdminService;
import com.faforever.clan_management.service.AppUserService;
import com.faforever.clan_management.service.ClanCard;
import com.faforever.clan_management.service.ClanRuleViolation;
import com.faforever.clan_management.service.ReportService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

//the report route and the two admin listings through the real bearer chain and the real AdminAccess, services mocked
@WebMvcTest({ReportApiController.class, AdminApiController.class})
@Import({SecurityConfig.class,AdminAccess.class})
class ReportAdminApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;
    @MockitoBean
    private AppUserService users;
    @MockitoBean
    private ReportService reports;
    @MockitoBean
    private AdminService admins;

    private final AppUser sam = user(2L, "sam");
    private final AppUser admin = user(1L, "admin");
    private final Clan clan = clan(7L,"Bad Company", "BC", sam);

    private static AppUser user(long id, String name) {
        AppUser u = new AppUser("sub-" + id, name, "local",null);
        ReflectionTestUtils.setField(u, "id",id);
        return u;
    }

    private static Clan clan(long id, String name, String tag, AppUser owner) {
        Clan c = new Clan(name, tag, "Nightly ladder squad.",owner,"PRIVATE", true);
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    private ClanReport report(long id) {
        ClanReport r = new ClanReport(clan,sam, "OFFENSIVE_NAME","the name is a slur");
        ReflectionTestUtils.setField(r, "id", id);
        return r;
    }

    // a token the mocked decoder accepts, with the admin role under ext.roles when asked for
    private String bearer(String sub, boolean asAdmin) {
        Instant now = Instant.now();
        Jwt.Builder b = Jwt.withTokenValue("token-"+sub).header("alg", "RS256").subject(sub).issuedAt(now).expiresAt(now.plusSeconds(300));
        if (asAdmin) b.claim("ext",Map.of("roles", List.of("ADMIN_CLAN")));
        given(jwtDecoder.decode("token-" + sub)).willReturn(b.build());
        return "Bearer token-"+sub;
    }

    @Test
    @DisplayName("all three routes answer 401 without a token and never reach a service")
    void closedWithoutToken() throws Exception {
        mockMvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON).content("{\"clanId\":7}")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/clans")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/reports")).andExpect(status().isUnauthorized());
        verifyNoInteractions(reports);
        verifyNoInteractions(admins);
    }

    @Test
    @DisplayName("reporting hands the clan, category and reason to the service and answers 201 with the report")
    void reportAnswersCreated() throws Exception {
        given(users.require(any())).willReturn(sam);
        given(reports.file(sam, 7L, "OFFENSIVE_NAME", "the name is a slur")).willReturn(report(3L));
        mockMvc.perform(post("/api/reports").header(HttpHeaders.AUTHORIZATION, bearer("sub-2", false)).contentType(MediaType.APPLICATION_JSON).content("{\"clanId\":7,\"category\":\"OFFENSIVE_NAME\",\"reason\":\"the name is a slur\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.clanId").value(7))
                .andExpect(jsonPath("$.clanTag").value("BC"))
                .andExpect(jsonPath("$.reporterDisplayName").value("sam"))
                .andExpect(jsonPath("$.category").value("OFFENSIVE_NAME"))
                .andExpect(jsonPath("$.state").value("OPEN"))
                .andExpect(jsonPath("$.resolvedAt").doesNotExist())
                .andExpect(jsonPath("$.resolvedById").doesNotExist());
        verify(users, never()).signedIn(any());
    }

    @Test
    @DisplayName("a refused report comes back as 400 with the sentence the service gave")
    void refusedReportIsBadRequest() throws Exception {
        given(users.require(any())).willReturn(sam);
        given(reports.file(sam, 7L, "RUDE", null)).willThrow(new ClanRuleViolation("Say what the report is about: an offensive name, an offensive tag or inappropriate content."));
        mockMvc.perform(post("/api/reports").header(HttpHeaders.AUTHORIZATION, bearer("sub-2", false)).contentType(MediaType.APPLICATION_JSON).content("{\"clanId\":7,\"category\":\"RUDE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Say what the report is about: an offensive name, an offensive tag or inappropriate content."));
    }

    @Test
    @DisplayName("the admin listings are 403 for a valid token without the role, and no service is asked")
    void adminRoutesNeedTheRole() throws Exception {
        String plain = bearer("sub-2", false);
        mockMvc.perform(get("/api/admin/clans").header(HttpHeaders.AUTHORIZATION, plain)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/reports").header(HttpHeaders.AUTHORIZATION, plain)).andExpect(status().isForbidden());
        verifyNoInteractions(admins);
        verifyNoInteractions(reports);
        //System.out.println("403 for sub-2");
    }

    @Test
    @DisplayName("an administrator lists every clan, private ones included, with status, owner and member count")
    void adminListsClans() throws Exception {
        given(users.require(any())).willReturn(admin);
        Pageable page = PageRequest.of(2,20);
        given(admins.clans("bad", "suspended", page)).willReturn(new PageImpl<>(List.of(new ClanCard(clan, 12L)), page, 41));
        mockMvc.perform(get("/api/admin/clans").param("q","bad").param("filter", "suspended").param("page", "2").header(HttpHeaders.AUTHORIZATION,bearer("sub-1", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clans.length()").value(1))
                .andExpect(jsonPath("$.clans[0].id").value(7))
                .andExpect(jsonPath("$.clans[0].visibility").value("PRIVATE"))
                .andExpect(jsonPath("$.clans[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.clans[0].deleted").value(false))
                .andExpect(jsonPath("$.clans[0].ownerDisplayName").value("sam"))
                .andExpect(jsonPath("$.clans[0].memberCount").value(12))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(41))
                .andExpect(jsonPath("$.hasPrevious").value(true))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("the page number is clamped for both listings, and an unknown filter is the services 400")
    void pageIsClampedAndFilterChecked() throws Exception {
        given(users.require(any())).willReturn(admin);
        String token = bearer("sub-1", true);
        given(admins.clans(anyString(), anyString(), any(Pageable.class))).willAnswer(a -> new PageImpl<>(List.of(),a.getArgument(2),0));
        given(reports.byState(anyString(), any(Pageable.class))).willAnswer(a -> new PageImpl<>(List.of(),a.getArgument(1), 0));
        mockMvc.perform(get("/api/admin/clans").param("page","999999999").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk()).andExpect(jsonPath("$.page").value(10000));
        mockMvc.perform(get("/api/admin/reports").param("page", "-4").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk()).andExpect(jsonPath("$.page").value(0));
        ArgumentCaptor<Pageable> asked = ArgumentCaptor.forClass(Pageable.class);
        verify(admins).clans(eq(""), eq("all"), asked.capture());
        //System.out.println("asked " + asked.getValue());
        assertThat(asked.getValue().getPageSize()).isEqualTo(20);
        assertThat(asked.getValue().getSort().isSorted()).isFalse();
        given(admins.clans(eq(""),eq("banned"), any(Pageable.class))).willThrow(new ClanRuleViolation("Clans are listed as all, active, suspended or deleted."));
        mockMvc.perform(get("/api/admin/clans").param("filter", "banned").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Clans are listed as all, active, suspended or deleted."));
    }

    @Test
    @DisplayName("an administrator reads the report queue, open by default, or another state when asked")
    void adminListsReports() throws Exception {
        given(users.require(any())).willReturn(admin);
        String token = bearer("sub-1", true);
        Pageable page = PageRequest.of(0, 20);
        ClanReport closed = report(4L);
        closed.close("DISMISSED", admin);
        given(reports.byState("OPEN", page)).willReturn(new PageImpl<>(List.of(report(3L)),page, 1));
        given(reports.byState("DISMISSED", page)).willReturn(new PageImpl<>(List.of(closed), page, 1));
        mockMvc.perform(get("/api/admin/reports").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reports.length()").value(1))
                .andExpect(jsonPath("$.reports[0].id").value(3))
                .andExpect(jsonPath("$.reports[0].state").value("OPEN"))
                .andExpect(jsonPath("$.reports[0].reason").value("the name is a slur"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/admin/reports").param("state", "DISMISSED").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reports[0].state").value("DISMISSED"))
                .andExpect(jsonPath("$.reports[0].resolvedById").value(1))
                .andExpect(jsonPath("$.reports[0].resolvedAt").exists());
    }
}
