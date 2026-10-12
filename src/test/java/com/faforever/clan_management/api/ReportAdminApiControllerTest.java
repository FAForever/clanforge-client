//SPDX-License-Identifier: MIT
package com.faforever.clan_management.api;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
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
import org.springframework.web.filter.FormContentFilter;

import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.entity.ClanReport;
import com.faforever.clan_management.entity.ModerationAction;
import com.faforever.clan_management.security.SecurityConfig;
import com.faforever.clan_management.service.AdminAccess;
import com.faforever.clan_management.service.AdminService;
import com.faforever.clan_management.service.AppUserService;
import com.faforever.clan_management.service.ClanCard;
import com.faforever.clan_management.service.ClanNotFound;
import com.faforever.clan_management.service.ClanRuleViolation;
import com.faforever.clan_management.service.DuplicateReport;
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
    @Autowired
    private ApplicationContext context;

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
    @DisplayName("reporting a clan that is missing or hidden is 404, as the contract documents")
    void missingClanIsNotFound() throws Exception {
        given(users.require(any())).willReturn(sam);
        given(reports.file(sam, 7L, "OFFENSIVE_NAME", "gone")).willThrow(new ClanNotFound("That clan does not exist."));
        mockMvc.perform(post("/api/reports").header(HttpHeaders.AUTHORIZATION, bearer("sub-2", false)).contentType(MediaType.APPLICATION_JSON).content("{\"clanId\":7,\"category\":\"OFFENSIVE_NAME\",\"reason\":\"gone\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("That clan does not exist."));
    }

    @Test
    @DisplayName("a second open report by the same person is 409, as the contract documents")
    void duplicateReportIsConflict() throws Exception {
        given(users.require(any())).willReturn(sam);
        given(reports.file(sam, 7L, "OFFENSIVE_NAME", "again")).willThrow(new DuplicateReport("You have already reported this clan."));
        mockMvc.perform(post("/api/reports").header(HttpHeaders.AUTHORIZATION, bearer("sub-2", false)).contentType(MediaType.APPLICATION_JSON).content("{\"clanId\":7,\"category\":\"OFFENSIVE_NAME\",\"reason\":\"again\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You have already reported this clan."));
    }

    @Test
    @DisplayName("the admin listings are 403 for a valid token without the role, and no service is asked")
    void adminRoutesNeedTheRole() throws Exception {
        String plain = bearer("sub-2", false);
        mockMvc.perform(get("/api/admin/clans").header(HttpHeaders.AUTHORIZATION, plain)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/reports").header(HttpHeaders.AUTHORIZATION, plain)).andExpect(status().isForbidden());
        verifyNoInteractions(admins);
        verifyNoInteractions(reports);
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
                .andExpect(jsonPath("$.clans[0].name").value("Bad Company"))
                .andExpect(jsonPath("$.clans[0].tag").value("BC"))
                .andExpect(jsonPath("$.clans[0].description").value("Nightly ladder squad."))
                .andExpect(jsonPath("$.clans[0].createdAt").exists())
                .andExpect(jsonPath("$.clans[0].deletedAt").doesNotExist())
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

    @Test
    @DisplayName("a write the database turns down, such as two requests racing for one unique row, is 409 with a sentence and not a 500")
    void constraintClashIsConflict() throws Exception {
        given(users.require(any())).willReturn(sam);
        given(reports.file(sam, 7L,"OFFENSIVE_NAME", "raced")).willThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint \"clan_report_one_open\""));
        mockMvc.perform(post("/api/reports").header(HttpHeaders.AUTHORIZATION, bearer("sub-2", false)).contentType(MediaType.APPLICATION_JSON).content("{\"clanId\":7,\"category\":\"OFFENSIVE_NAME\",\"reason\":\"raced\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("That could not be saved.")));
        given(reports.file(sam, 7L,"OFFENSIVE_TAG", "raced")).willThrow(new DataIntegrityViolationException("could not execute statement", new SQLException("duplicate key value violates unique constraint", "23505")));
        mockMvc.perform(post("/api/reports").header(HttpHeaders.AUTHORIZATION,bearer("sub-2", false)).contentType(MediaType.APPLICATION_JSON).content("{\"clanId\":7,\"category\":\"OFFENSIVE_TAG\",\"reason\":\"raced\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("a value the database cannot store, such as a nul character, is the caller's 400 and not a clash")
    void badDataIsBadRequest() throws Exception {
        given(users.require(any())).willReturn(sam);
        given(reports.file(sam, 7L, "OFFENSIVE_NAME","nul")).willThrow(new DataIntegrityViolationException("could not execute statement", new SQLException("invalid byte sequence for encoding \"UTF8\": 0x00", "22021")));
        mockMvc.perform(post("/api/reports").header(HttpHeaders.AUTHORIZATION, bearer("sub-2", false)).contentType(MediaType.APPLICATION_JSON).content("{\"clanId\":7,\"category\":\"OFFENSIVE_NAME\",\"reason\":\"nul\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("That request holds a value the database cannot store")));
    }

    @Test
    @DisplayName("a body that is not json is 400 with a message field like every other refusal")
    void unreadableBodyIsBadRequestWithAMessage() throws Exception {
        mockMvc.perform(post("/api/reports").header(HttpHeaders.AUTHORIZATION, bearer("sub-2", false)).contentType(MediaType.APPLICATION_JSON).content("{\"clanId\":7,"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The request body could not be read as the JSON this route takes."));
        verifyNoInteractions(reports);
    }

    @Test
    @DisplayName("no filter reads a put, patch or delete form body in full before the api size cap has run")
    void formBodiesAreNotReadAhead() {
        assertThat(context.getBeansOfType(FormContentFilter.class)).isEmpty();
    }

    // --- the moderation actions on a clan (suspend/unsuspend/rename/retag/delete) and its history ---

    private ModerationAction action(long id, String kind) {
        ModerationAction a = new ModerationAction(clan, admin, kind, "cleaning up", null, "{}");
        ReflectionTestUtils.setField(a, "id", id);
        return a;
    }

    @Test
    @DisplayName("every action route is 401 without a token and never reaches the service")
    void actionsClosedWithoutToken() throws Exception {
        mockMvc.perform(post("/api/admin/clans/7/suspend").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/clans/7/delete").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/clans/7/history")).andExpect(status().isUnauthorized());
        verifyNoInteractions(admins);
    }

    @Test
    @DisplayName("every action route is 403 for a valid token without the admin role, and no service is asked")
    void actionsNeedTheRole() throws Exception {
        String plain = bearer("sub-2", false);
        mockMvc.perform(post("/api/admin/clans/7/suspend").header(HttpHeaders.AUTHORIZATION, plain).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/clans/7/rename").header(HttpHeaders.AUTHORIZATION, plain).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/clans/7/history").header(HttpHeaders.AUTHORIZATION, plain)).andExpect(status().isForbidden());
        verifyNoInteractions(admins);
    }

    @Test
    @DisplayName("suspending hands the clan, reason and report to the service and answers with the moderation row")
    void suspendAnswersWithTheRow() throws Exception {
        given(users.require(any())).willReturn(admin);
        given(admins.suspend(admin, 7L, "slur in the tag", 3L)).willReturn(action(11L, "SUSPEND"));
        mockMvc.perform(post("/api/admin/clans/7/suspend").header(HttpHeaders.AUTHORIZATION, bearer("sub-1", true)).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"slur in the tag\",\"reportId\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.clanId").value(7))
                .andExpect(jsonPath("$.clanTag").value("BC"))
                .andExpect(jsonPath("$.action").value("SUSPEND"))
                .andExpect(jsonPath("$.moderatorDisplayName").value("admin"))
                .andExpect(jsonPath("$.performedAt").exists());
        verify(admins).suspend(admin, 7L, "slur in the tag", 3L);
    }

    @Test
    @DisplayName("renaming, retagging and unsuspending pass their value and reason through to the service")
    void renameRetagUnsuspendPassTheValue() throws Exception {
        given(users.require(any())).willReturn(admin);
        String token = bearer("sub-1", true);
        given(admins.rename(eq(admin), eq(7L), eq("Clean Name"), any(), any())).willReturn(action(12L, "RENAME"));
        given(admins.retag(eq(admin), eq(7L), eq("GGG"), any(), any())).willReturn(action(13L, "RETAG"));
        given(admins.unsuspend(eq(admin), eq(7L), any())).willReturn(action(14L, "UNSUSPEND"));
        mockMvc.perform(post("/api/admin/clans/7/rename").header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Clean Name\",\"reason\":\"offensive\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.action").value("RENAME"));
        mockMvc.perform(post("/api/admin/clans/7/retag").header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON).content("{\"tag\":\"GGG\",\"reason\":\"offensive\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.action").value("RETAG"));
        mockMvc.perform(post("/api/admin/clans/7/unsuspend").header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"appeal granted\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.action").value("UNSUSPEND"));
        verify(admins).rename(admin, 7L, "Clean Name", "offensive", null);
        verify(admins).retag(admin, 7L, "GGG", "offensive", null);
        verify(admins).unsuspend(admin, 7L, "appeal granted");
    }

    @Test
    @DisplayName("an action the service refuses, such as suspending an already suspended clan, is 400 with its sentence")
    void refusedActionIsBadRequest() throws Exception {
        given(users.require(any())).willReturn(admin);
        given(admins.suspend(eq(admin), eq(7L), any(), any())).willThrow(new ClanRuleViolation("That clan is already suspended."));
        mockMvc.perform(post("/api/admin/clans/7/suspend").header(HttpHeaders.AUTHORIZATION, bearer("sub-1", true)).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"dup\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("That clan is already suspended."));
    }

    @Test
    @DisplayName("deleting answers with the moderation row, and the history lists a clan's actions newest first")
    void deleteAndHistory() throws Exception {
        given(users.require(any())).willReturn(admin);
        String token = bearer("sub-1", true);
        given(admins.delete(eq(admin), eq(7L), any(), any())).willReturn(action(15L, "DELETE"));
        given(admins.history(7L)).willReturn(List.of(action(15L, "DELETE"), action(11L, "SUSPEND")));
        mockMvc.perform(post("/api/admin/clans/7/delete").header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"spam ring\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.action").value("DELETE"));
        mockMvc.perform(get("/api/admin/clans/7/history").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].action").value("DELETE"))
                .andExpect(jsonPath("$[1].action").value("SUSPEND"));
        verify(admins).history(7L);
    }

    @Test
    @DisplayName("an unreadable body on an admin action is 400 with a message, not a 500, and the service is left untouched")
    void actionWithUnreadableBodyIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/admin/clans/7/suspend").header(HttpHeaders.AUTHORIZATION, bearer("sub-1", true)).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The request body could not be read as the JSON this route takes."));
        verifyNoInteractions(admins);
    }
}
