//SPDX-License-Identifier: MIT
package com.faforever.clan_management.web;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.entity.ClanMembership;
import com.faforever.clan_management.entity.ClanReport;
import com.faforever.clan_management.entity.ModerationAction;
import com.faforever.clan_management.security.SecurityConfig;
import com.faforever.clan_management.service.AdminAccess;
import com.faforever.clan_management.service.AdminOverview;
import com.faforever.clan_management.service.AdminService;
import com.faforever.clan_management.service.AppUserService;
import com.faforever.clan_management.service.ClanAccessRefused;
import com.faforever.clan_management.service.ClanCard;
import com.faforever.clan_management.service.ClanRuleViolation;
import com.faforever.clan_management.service.MemberCard;
import com.faforever.clan_management.service.MembershipService;
import com.faforever.clan_management.service.ReportService;
import com.faforever.clan_management.service.SignedInUser;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

// the admin pages through the real web security chain and the real AdminAccess, services mocked,
// so every template renders and every route's access rule is exercised without a database.
// Signed in as an administrator unless a test says otherwise.
@WebMvcTest(AdminController.class)
@Import({SecurityConfig.class, AdminAccess.class})
@WithMockUser(username = "admin", roles = {"USER", "ADMIN_CLAN"})
class AdminPagesTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper json;

    @MockitoBean
    private AppUserService users;
    @MockitoBean
    private AdminService admins;
    @MockitoBean
    private ReportService reports;
    @MockitoBean
    private MembershipService memberships;

    private final AppUser admin = user(1L, "admin");
    private final AppUser sam = user(2L, "sam");
    private final AppUser reporter = user(3L, "reporter_kim");
    private final Clan clan = clan(7L, "Bad Company", "BC", sam);

    private static AppUser user(long id, String name) {
        AppUser u = new AppUser("sub-" + id, name, "local", null);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private static Clan clan(long id, String name, String tag, AppUser owner) {
        Clan c = new Clan(name, tag, "Nightly ladder squad.", owner, "PUBLIC", true);
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    private ClanReport report(long id, String reason) {
        ClanReport r = new ClanReport(clan, reporter, "OFFENSIVE_NAME", reason);
        ReflectionTestUtils.setField(r, "id", id);
        return r;
    }

    private ModerationAction action(long id, String kind, String reason, String previous) {
        ModerationAction a = new ModerationAction(clan, admin, kind, reason, null, previous);
        ReflectionTestUtils.setField(a, "id", id);
        return a;
    }

    @BeforeEach
    void signedInAsAdmin() {
        given(users.require(any())).willReturn(admin);
        given(users.signedIn(any())).willReturn(Optional.of(new SignedInUser(admin, null, 0)));
        given(admins.overview()).willReturn(new AdminOverview(1478, 1470, 5, 3, 2, 9, 4));
    }

    private void dossier(List<MemberCard> roster, List<ModerationAction> history, List<ClanReport> open) {
        given(admins.clan(7L)).willReturn(Optional.of(clan));
        given(memberships.roster(clan)).willReturn(roster);
        given(admins.history(7L)).willReturn(history);
        given(reports.openFor(7L)).willReturn(open);
    }

    // ------------------------------------------------------------------ access

    @Test
    @WithAnonymousUser
    @DisplayName("an anonymous visitor is sent to sign in from every admin page and action, and no service is asked")
    void anonymousIsSentToLogin() throws Exception {
        mockMvc.perform(get("/admin")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/admin/clans")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/admin/clans/7")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/admin/reports")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mockMvc.perform(post("/admin/clans/7/suspend").with(csrf())).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        verifyNoInteractions(admins, reports);
    }

    @Test
    @WithMockUser(username = "sam", roles = "USER")
    @DisplayName("a signed-in player without the admin role is refused with 403 on every page and action")
    void nonAdminIsForbidden() throws Exception {
        mockMvc.perform(get("/admin/clans")).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/clans/7")).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/reports")).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/clans/7/suspend").with(csrf()).param("reason", "x")).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/clans/7/delete").with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/reports/3/dismiss").with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(admins, reports);
    }

    @Test
    @DisplayName("an action without its csrf token is refused before anything changes")
    void actionsNeedCsrf() throws Exception {
        mockMvc.perform(post("/admin/clans/7/suspend").param("reason", "x")).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/reports/3/dismiss")).andExpect(status().isForbidden());
        verify(admins, org.mockito.Mockito.never()).suspend(any(), any(), any(), any());
        verifyNoInteractions(reports);
    }

    @Test
    @DisplayName("should AdminAccess ever refuse where SecurityConfig let through, the answer is a 403 and never a 500")
    void adminAccessRefusalIsForbidden() {
        AdminAccess refusing = mock(AdminAccess.class);
        given(refusing.require(any())).willThrow(new ClanAccessRefused("Only an administrator can do that."));
        AdminController controller = new AdminController(refusing, admins, reports, memberships, json);
        assertThatThrownBy(() -> controller.suspend(7L, "x", null, null, new RedirectAttributesModelMap()))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> controller.clan(7L, null, new org.springframework.ui.ExtendedModelMap()))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(admins);
    }

    // ------------------------------------------------------------------ clan list

    @Test
    @DisplayName("/admin opens the clan list")
    void consoleOpensClanList() throws Exception {
        mockMvc.perform(get("/admin")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/clans"));
    }

    @Test
    @DisplayName("the clan list shows every clan with its status, owner and members, the console figures, and the admin link in the top bar")
    void clanListRenders() throws Exception {
        ReflectionTestUtils.setField(clan, "status", "SUSPENDED");
        Pageable page = PageRequest.of(2, 20);
        given(admins.clans("bad", "suspended", page)).willReturn(new PageImpl<>(List.of(new ClanCard(clan, 12L)), page, 41));
        mockMvc.perform(get("/admin/clans").param("q", "bad").param("filter", " Suspended ").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/clans"))
                .andExpect(model().attribute("filter", "suspended"))
                .andExpect(content().string(containsString("Clan Moderation")))
                .andExpect(content().string(containsString("Bad Company")))
                .andExpect(content().string(containsString("[BC] · #7")))
                .andExpect(content().string(containsString("href=\"/admin/clans/7\"")))
                .andExpect(content().string(containsString("status-pill is-suspended")))
                .andExpect(content().string(containsString(">sam<")))
                .andExpect(content().string(containsString("1,478")))
                .andExpect(content().string(containsString("nav-link-admin")))
                .andExpect(content().string(containsString("Admin Console")))
                .andExpect(content().string(containsString("page=1")));
    }

    @Test
    @DisplayName("a filter the service does not know comes back to the list with its sentence")
    void unknownFilterIsFlashed() throws Exception {
        given(admins.clans(eq(""), eq("banned"), any(Pageable.class))).willThrow(new ClanRuleViolation("Clans are listed as all, active, suspended or deleted."));
        mockMvc.perform(get("/admin/clans").param("filter", "banned"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/clans"))
                .andExpect(flash().attribute("error", "Clans are listed as all, active, suspended or deleted."));
    }

    @Test
    @DisplayName("the page number is clamped, so a crafted address cannot ask for an enormous offset")
    void pageIsClamped() throws Exception {
        given(admins.clans(anyString(), anyString(), any(Pageable.class))).willAnswer(a -> new PageImpl<>(List.of(), a.getArgument(2), 0));
        mockMvc.perform(get("/admin/clans").param("page", "999999999")).andExpect(status().isOk());
        mockMvc.perform(get("/admin/clans").param("page", "-3")).andExpect(status().isOk());
        ArgumentCaptor<Pageable> asked = ArgumentCaptor.forClass(Pageable.class);
        verify(admins, org.mockito.Mockito.times(2)).clans(eq(""), eq("all"), asked.capture());
        assertThat(asked.getAllValues()).extracting(Pageable::getPageNumber).containsExactly(10_000, 0);
        assertThat(asked.getAllValues()).extracting(Pageable::getPageSize).containsOnly(20);
    }

    // ------------------------------------------------------------------ dossier

    @Test
    @DisplayName("a live clan's page shows its profile, open reports, history with what it was called before, roster, and every action")
    void dossierOfLiveClan() throws Exception {
        ClanMembership owner = new ClanMembership(clan, sam, "OWNER");
        dossier(List.of(new MemberCard(owner)),
                List.of(action(11L, "RENAME", "was a slur", "{\"name\":\"Old Name\",\"tag\":\"BC\"}"), action(10L, "SUSPEND", null, "{}")),
                List.of(report(3L, "the name is a slur")));
        mockMvc.perform(get("/admin/clans/7"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/clan"))
                .andExpect(content().string(containsString("Bad Company")))
                .andExpect(content().string(containsString("Nightly ladder squad.")))
                .andExpect(content().string(containsString("the name is a slur")))
                .andExpect(content().string(containsString("reported by reporter_kim")))
                .andExpect(content().string(containsString("Renamed")))
                .andExpect(content().string(containsString("Old Name")))
                .andExpect(content().string(containsString("was a slur")))
                .andExpect(content().string(containsString("timeline-item is-warn")))
                .andExpect(content().string(containsString("Suspend Clan")))
                .andExpect(content().string(containsString("Rename Clan")))
                .andExpect(content().string(containsString("Change Tag")))
                .andExpect(content().string(containsString("Delete Clan")))
                .andExpect(content().string(containsString("Answers report")))
                .andExpect(content().string(containsString("data-confirm")))
                .andExpect(content().string(containsString("/admin/reports/3/dismiss?clanId=7")))
                .andExpect(content().string(not(containsString("Lift Suspension"))));
    }

    @Test
    @DisplayName("a suspended clan offers lifting the suspension instead of suspending it again")
    void dossierOfSuspendedClan() throws Exception {
        ReflectionTestUtils.setField(clan, "status", "SUSPENDED");
        dossier(List.of(), List.of(), List.of());
        mockMvc.perform(get("/admin/clans/7"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Lift Suspension")))
                .andExpect(content().string(not(containsString("Suspend Clan"))))
                .andExpect(content().string(not(containsString("Answers report")))); //no open report, so no picker
    }

    @Test
    @DisplayName("a deleted clan's page keeps its record and history but offers no action")
    void dossierOfDeletedClan() throws Exception {
        ReflectionTestUtils.setField(clan, "deletedAt", OffsetDateTime.now());
        dossier(List.of(), List.of(action(12L, "DELETE", "spam ring", "{}")), List.of());
        mockMvc.perform(get("/admin/clans/7"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Clan Deleted")))
                .andExpect(content().string(containsString("status-pill is-deleted")))
                .andExpect(content().string(containsString("spam ring")))
                .andExpect(content().string(not(containsString("Suspend Clan"))))
                .andExpect(content().string(not(containsString("Delete Clan"))))
                .andExpect(content().string(not(containsString("Rename Clan"))));
    }

    @Test
    @DisplayName("markup a player puts in a clan name, description or report is shown to the administrator as text and never runs")
    void playerMarkupIsEscaped() throws Exception {
        Clan hostile = clan(7L, "<script>alert('name')</script>", "<b>", sam);
        ReflectionTestUtils.setField(hostile, "description", "<img src=x onerror=alert('desc')>");
        ClanReport bait = new ClanReport(hostile, reporter, "INAPPROPRIATE_CONTENT", "<svg onload=alert('report')>");
        ReflectionTestUtils.setField(bait, "id", 3L);
        given(admins.clan(7L)).willReturn(Optional.of(hostile));
        given(memberships.roster(hostile)).willReturn(List.of());
        given(admins.history(7L)).willReturn(List.of());
        given(reports.openFor(7L)).willReturn(List.of(bait));
        mockMvc.perform(get("/admin/clans/7"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;alert(&#39;name&#39;)&lt;/script&gt;")))
                .andExpect(content().string(containsString("&lt;img src=x onerror=alert(&#39;desc&#39;)&gt;")))
                .andExpect(content().string(containsString("&lt;svg onload=alert(&#39;report&#39;)&gt;")))
                .andExpect(content().string(not(containsString("<script>alert"))))
                .andExpect(content().string(not(containsString("<img src=x"))))
                .andExpect(content().string(not(containsString("<svg onload"))))
                // the second layer: even markup that got through could not run, no inline script is allowed and the page cannot be framed
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")));
    }

    @Test
    @DisplayName("a clan id that does not exist is a 404")
    void missingClanIsNotFound() throws Exception {
        given(admins.clan(99L)).willReturn(Optional.empty());
        mockMvc.perform(get("/admin/clans/99")).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ actions

    @Test
    @DisplayName("suspending hands the clan, reason and report to the service and returns to the clan's page with a notice")
    void suspendGoesBackWithNotice() throws Exception {
        mockMvc.perform(post("/admin/clans/7/suspend").with(csrf()).param("reason", "slur in the tag").param("reportId", "3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/clans/7"))
                .andExpect(flash().attribute("notice", containsString("Clan suspended")));
        verify(admins).suspend(admin, 7L, "slur in the tag", 3L);
    }

    @Test
    @DisplayName("the report picker left on No report sends an empty value, which means no report")
    void emptyReportPickerIsNoReport() throws Exception {
        mockMvc.perform(post("/admin/clans/7/suspend").with(csrf()).param("reportId", ""))
                .andExpect(status().is3xxRedirection());
        verify(admins).suspend(admin, 7L, null, null);
    }

    @Test
    @DisplayName("rename, retag, unsuspend and delete each reach their own service call with the form's values")
    void everyActionIsWired() throws Exception {
        mockMvc.perform(post("/admin/clans/7/rename").with(csrf()).param("name", "Clean Name").param("reason", "offensive").param("reportId", "3"))
                .andExpect(redirectedUrl("/admin/clans/7")).andExpect(flash().attribute("notice", "Clan renamed."));
        mockMvc.perform(post("/admin/clans/7/retag").with(csrf()).param("tag", "CLN").param("reason", "offensive"))
                .andExpect(redirectedUrl("/admin/clans/7")).andExpect(flash().attribute("notice", "Clan tag changed."));
        mockMvc.perform(post("/admin/clans/7/unsuspend").with(csrf()).param("reason", "appeal upheld"))
                .andExpect(redirectedUrl("/admin/clans/7")).andExpect(flash().attribute("notice", "Suspension lifted."));
        mockMvc.perform(post("/admin/clans/7/delete").with(csrf()).param("reason", "spam ring"))
                .andExpect(redirectedUrl("/admin/clans/7")).andExpect(flash().attribute("notice", containsString("Clan deleted")));
        verify(admins).rename(admin, 7L, "Clean Name", "offensive", 3L);
        verify(admins).retag(admin, 7L, "CLN", "offensive", null);
        verify(admins).unsuspend(admin, 7L, "appeal upheld");
        verify(admins).delete(admin, 7L, "spam ring", null);
    }

    @Test
    @DisplayName("an action the service refuses returns to the clan's page with the service's sentence as an error")
    void refusalIsFlashed() throws Exception {
        given(admins.rename(admin, 7L, "Bad Company", null, null)).willThrow(new ClanRuleViolation("That is already the clan's name."));
        given(admins.suspend(admin, 7L, null, null)).willThrow(new ClanRuleViolation("That clan is already suspended."));
        mockMvc.perform(post("/admin/clans/7/rename").with(csrf()).param("name", "Bad Company"))
                .andExpect(redirectedUrl("/admin/clans/7"))
                .andExpect(flash().attribute("error", "That is already the clan's name."))
                .andExpect(flash().attributeCount(1));
        mockMvc.perform(post("/admin/clans/7/suspend").with(csrf()))
                .andExpect(redirectedUrl("/admin/clans/7"))
                .andExpect(flash().attribute("error", "That clan is already suspended."));
    }

    // ------------------------------------------------------------------ report queue

    @Test
    @DisplayName("the report queue shows open reports with the clan, the reporter's words and a way to dismiss")
    void reportQueueRenders() throws Exception {
        Pageable page = PageRequest.of(0, 20);
        given(reports.byState("OPEN", page)).willReturn(new PageImpl<>(List.of(report(3L, "the name is a slur")), page, 1));
        mockMvc.perform(get("/admin/reports"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/reports"))
                .andExpect(model().attribute("state", "OPEN"))
                .andExpect(content().string(containsString("Report Queue")))
                .andExpect(content().string(containsString("the name is a slur")))
                .andExpect(content().string(containsString("OFFENSIVE NAME")))
                .andExpect(content().string(containsString("Bad Company [BC]")))
                .andExpect(content().string(containsString("Review Clan")))
                .andExpect(content().string(containsString("/admin/reports/3/dismiss")))
                .andExpect(content().string(containsString("state-pill is-open")));
    }

    @Test
    @DisplayName("the state is read whatever its case, and an empty state says the queue is clear")
    void reportStateAndEmptyQueue() throws Exception {
        given(reports.byState(anyString(), any(Pageable.class))).willAnswer(a -> new PageImpl<>(List.of(), a.getArgument(1), 0));
        mockMvc.perform(get("/admin/reports").param("state", "resolved"))
                .andExpect(status().isOk()).andExpect(model().attribute("state", "RESOLVED"));
        mockMvc.perform(get("/admin/reports"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("The queue is clear")));
        verify(reports).byState(eq("RESOLVED"), any(Pageable.class));
    }

    @Test
    @DisplayName("a state the service does not know comes back to the queue with its sentence")
    void unknownStateIsFlashed() throws Exception {
        given(reports.byState(eq("LOST"), any(Pageable.class))).willThrow(new ClanRuleViolation("Reports are open, resolved or dismissed."));
        mockMvc.perform(get("/admin/reports").param("state", "lost"))
                .andExpect(redirectedUrl("/admin/reports"))
                .andExpect(flash().attribute("error", "Reports are open, resolved or dismissed."));
    }

    @Test
    @DisplayName("dismissing closes the report and goes back to the clan's page or the queue, whichever it came from")
    void dismissGoesBack() throws Exception {
        mockMvc.perform(post("/admin/reports/3/dismiss").with(csrf()).param("clanId", "7"))
                .andExpect(redirectedUrl("/admin/clans/7"))
                .andExpect(flash().attribute("notice", "Report #3 dismissed."));
        mockMvc.perform(post("/admin/reports/4/dismiss").with(csrf()))
                .andExpect(redirectedUrl("/admin/reports"));
        verify(reports).close(admin, 3L, true);
        verify(reports).close(admin, 4L, true);
    }

    @Test
    @DisplayName("dismissing a report that is already closed comes back with the service's sentence")
    void dismissRefusalIsFlashed() throws Exception {
        given(reports.close(admin, 3L, true)).willThrow(new ClanRuleViolation("That report is already closed."));
        mockMvc.perform(post("/admin/reports/3/dismiss").with(csrf()))
                .andExpect(redirectedUrl("/admin/reports"))
                .andExpect(flash().attribute("error", "That report is already closed."));
    }
}
