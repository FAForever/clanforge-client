package com.faforever.clan_management.web;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.entity.ClanInvitation;
import com.faforever.clan_management.entity.ClanMembership;
import com.faforever.clan_management.security.SecurityConfig;
import com.faforever.clan_management.service.AppUserService;
import com.faforever.clan_management.service.CalendarService;
import com.faforever.clan_management.service.Regions;
import com.faforever.clan_management.service.ClanForm;
import com.faforever.clan_management.service.ClanManagementService;
import com.faforever.clan_management.service.ClanNotFound;
import com.faforever.clan_management.service.ClanRuleViolation;
import com.faforever.clan_management.service.ClanService;
import com.faforever.clan_management.service.InvitationCard;
import com.faforever.clan_management.service.InvitationService;
import com.faforever.clan_management.service.MemberCard;
import com.faforever.clan_management.service.MembershipService;
import com.faforever.clan_management.service.ReportService;
import com.faforever.clan_management.service.SignedInUser;
import com.faforever.clan_management.service.TagRule;

import com.faforever.clan_management.entity.CalendarEvent;
import com.faforever.clan_management.entity.ClanLogo;
import org.springframework.mock.web.MockMultipartFile;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

//the pages behind the login, rendered against mocked services so the templates are exercised without a database
@WebMvcTest({ClanHomeController.class,CreateClanController.class, InvitationController.class, SettingsController.class, ClanController.class})
@Import(SecurityConfig.class)
class ManagementPagesTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AppUserService users;
    @MockitoBean
    private MembershipService memberships;
    @MockitoBean
    private ClanManagementService management;
    @MockitoBean
    private InvitationService invitations;
    @MockitoBean
    private ReportService reportService;
    @MockitoBean
    private ClanService clanService;
    @MockitoBean
    private TagRule tagRule;
    @MockitoBean
    private Regions regions;
    @MockitoBean
    private CalendarService calendar;

    private final AppUser admin = user(1L, "admin");
    private final AppUser sam = user(2L, "User_5");
    private final Clan clan = clan(7L, "Bad Company", "BC",admin);
    private final ClanMembership ownerRow = membership(5L, clan, admin, "OWNER");
    private final ClanMembership memberRow = membership(6L, clan,sam, "MEMBER");

    private static AppUser user(long id, String name) {
        AppUser u = new AppUser("sub-" + id, name, "local", null);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private static Clan clan(long id, String name, String tag, AppUser owner) {
        Clan c = new Clan(name, tag, "Nightly ladder squad.", owner,"PUBLIC", true);
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    private static ClanMembership membership(long id, Clan clan, AppUser user, String role) {
        ClanMembership m = new ClanMembership(clan, user, role);
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }

    private void signedInAs(ClanMembership membership,long pending) {
        given(users.signedIn(any())).willReturn(Optional.of(new SignedInUser(admin, membership, pending, List.of(new com.faforever.clan_management.service.Notice("User_5 joined the clan.", OffsetDateTime.now().minusHours(2), "/clanhome")))));
        given(users.require(any())).willReturn(admin);
    }

    @Test
    @WithMockUser("admin")
    @DisplayName("my clan shows the real roster and the owner's management links")
    void myClanShowsRoster() throws Exception {
        signedInAs(ownerRow, 0);
        given(memberships.roster(clan)).willReturn(List.of(new MemberCard(ownerRow), new MemberCard(memberRow)));
        mockMvc.perform(get("/clanhome"))
                .andExpect(status().isOk())
                .andExpect(view().name("clanhome"))
                .andExpect(content().string(Matchers.containsString("Bad Company")))
                .andExpect(content().string(Matchers.containsString("User_5")))
                .andExpect(content().string(Matchers.containsString("Make moderator")))
                .andExpect(content().string(Matchers.containsString("Clan Settings")))
                .andExpect(content().string(Matchers.containsString("User_5 joined the clan.")))
                .andExpect(content().string(Matchers.containsString("2h ago")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Leave Clan"))));
    }

    @Test
    @WithMockUser("admin")
    @DisplayName("reporting a clan hands the category and reason to the service and redirects back with a notice")
    void reportRedirectsBackWithNotice() throws Exception {
        given(users.require(any())).willReturn(admin);
        mockMvc.perform(post("/clans/7/report").with(csrf())
                        .param("category", "OFFENSIVE_NAME")
                        .param("reason", "The tag is offensive."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/clans/7"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash().attributeExists("notice"));
        verify(reportService).file(eq(admin), eq(7L), eq("OFFENSIVE_NAME"), eq("The tag is offensive."));
    }

    @Test
    @WithMockUser("admin")
    @DisplayName("a rejected report stays on the report page carrying the service's message as an error")
    void reportRejectionShowsError() throws Exception {
        given(users.require(any())).willReturn(admin);
        given(reportService.file(any(), eq(7L), any(), any())).willThrow(new ClanRuleViolation("You have already reported this clan."));
        mockMvc.perform(post("/clans/7/report").with(csrf())
                        .param("category", "OFFENSIVE_NAME")
                        .param("reason", "dup"))
                .andExpect(status().is3xxRedirection())
                // the report form lives on its own page now, so a rejected submit returns
                // there (not to the clan page) with the error, ready to correct and resend
                .andExpect(redirectedUrl("/clans/7/report"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash().attribute("error", "You have already reported this clan."));
    }

    @Test
    @WithMockUser("admin")
    @DisplayName("reporting a clan that was deleted meanwhile redirects to Browse with an error")
    void reportOnAGoneClanRedirectsToBrowse() throws Exception {
        given(users.require(any())).willReturn(admin);
        given(reportService.file(any(), eq(7L), any(), any())).willThrow(new ClanNotFound("That clan does not exist."));
        mockMvc.perform(post("/clans/7/report").with(csrf())
                        .param("category", "OFFENSIVE_NAME")
                        .param("reason", "gone"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/clans"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash().attributeExists("error"));
    }

    @Test
    @WithMockUser("admin")
    @DisplayName("a join that loses the one-membership race surfaces a clean message, not a 500")
    void joinRaceSurfacesCleanly() throws Exception {
        given(users.require(any())).willReturn(sam);
        given(memberships.join(eq(7L), any())).willThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key violates membership_one_active_per_user"));
        mockMvc.perform(post("/clans/7/join").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/clans/7"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash().attribute("error", "You are already in a clan."));
    }

    @Test
    @WithMockUser("admin")
    @DisplayName("someone in no clan sees the empty state instead of a roster")
    void noClanShowsEmptyState() throws Exception {
        signedInAs(null, 2);
        mockMvc.perform(get("/clanhome"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("You are not in a clan yet")))
                .andExpect(content().string(Matchers.containsString("nav-badge\">2<")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Leave Clan"))));
    }

    @Test
    @WithMockUser("admin")
    @DisplayName("creating a clan posts the form to the service and lands on my clan")
    void createPostsToService() throws Exception {
        signedInAs(null,0);
        given(tagRule.getHint()).willReturn("1 to 3 letters or numbers");
        mockMvc.perform(post("/create-clan").with(csrf())
                        .param("name", "Bad Company")
                        .param("tag", "BC")
                        .param("description", "Nightly ladder squad.")
                        .param("publicClan", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/clanhome"));
        ArgumentCaptor<ClanForm> form = ArgumentCaptor.forClass(ClanForm.class);
        verify(management).create(eq(admin), form.capture());
        assertThat(form.getValue().getName()).isEqualTo("Bad Company");
        assertThat(form.getValue().getTag()).isEqualTo("BC");
        assertThat(form.getValue().isPublicClan()).isTrue();
    }

    @Test
    @WithMockUser("admin")
    @DisplayName("a refused clan comes straight back with the reason on the form")
    void refusedCreateShowsReason() throws Exception {
        signedInAs(null,0);
        given(tagRule.getHint()).willReturn("1 to 3 letters or numbers");
        given(management.create(any(),any())).willThrow(new ClanRuleViolation("The clan tag 'BC' is already in use."));
        mockMvc.perform(post("/create-clan").with(csrf()).param("name", "Bad Company").param("tag","BC"))
                .andExpect(status().isOk())
                .andExpect(view().name("create-clan"))
                .andExpect(content().string(Matchers.containsString("is already in use")));
    }

    @Test
    @WithMockUser("admin")
    @DisplayName("the invitations page lists the inbox and, for a manager, the invites sent")
    void invitationsPageListsBoth() throws Exception {
        signedInAs(ownerRow, 1);
        Clan other = clan(8L, "Eloreem's Guard", "EGD", sam);
        ClanInvitation in = new ClanInvitation(other, admin, sam, OffsetDateTime.now().plusDays(3), "MEMBER");
        ReflectionTestUtils.setField(in, "id", 11L);
        ClanInvitation out = new ClanInvitation(clan, sam, admin, OffsetDateTime.now().plusDays(3), "MODERATOR");
        ReflectionTestUtils.setField(out, "id", 12L);
        given(invitations.inbox(admin)).willReturn(List.of(new InvitationCard(in)));
        given(invitations.sent(clan)).willReturn(List.of(new InvitationCard(out)));
        mockMvc.perform(get("/invitations"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Eloreem&#39;s Guard [EGD]")))
                .andExpect(content().string(Matchers.containsString("/invitations/11/accept")))
                .andExpect(content().string(Matchers.containsString("Sent")))
                .andExpect(content().string(Matchers.containsString("/invitations/12/revoke")));
    }

    @Test
    @WithMockUser("admin")
    @DisplayName("accepting an invitation goes to the new clan's dashboard")
    void acceptRedirectsHome() throws Exception {
        signedInAs(null, 1);
        given(invitations.accept(admin, 11L)).willReturn(memberRow);
        mockMvc.perform(post("/invitations/11/accept").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/clanhome"));
    }

    @Test
    @WithMockUser("admin")
    @DisplayName("settings shows the danger zone to the owner only")
    void settingsDangerZoneIsOwnerOnly() throws Exception {
        signedInAs(ownerRow, 0);
        given(memberships.roster(clan)).willReturn(List.of(new MemberCard(ownerRow),new MemberCard(memberRow)));
        given(tagRule.getHint()).willReturn("1 to 3 letters or numbers");
        mockMvc.perform(get("/settings"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Danger Zone")))
                .andExpect(content().string(Matchers.containsString("Save Changes")));
        signedInAs(membership(9L, clan, admin, "MEMBER"), 0);
        mockMvc.perform(get("/settings"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.not(Matchers.containsString("Danger Zone"))))
                .andExpect(content().string(Matchers.containsString("Only the owner and moderators")));
    }

    @Test
    @DisplayName("anonymous visitors are sent to the login page for every management route")
    void anonymousIsRedirected() throws Exception {
        for (String path : List.of("/clanhome", "/create-clan", "/invitations", "/settings")) {
            mockMvc.perform(get(path))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login"));
        }
        mockMvc.perform(post("/clans/1/join").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser("admin")
    @DisplayName("settings shows the image, message of the day and events panels to a manager")
    void settingsShowsTheDesignPanels() throws Exception {
        signedInAs(ownerRow, 0);
        given(memberships.roster(clan)).willReturn(List.of(new MemberCard(ownerRow)));
        CalendarEvent ev = new CalendarEvent(clan, "Clan Ladder Night", OffsetDateTime.now().plusDays(3), admin);
        ReflectionTestUtils.setField(ev, "id", 21L);
        given(calendar.upcoming(clan, 20)).willReturn(List.of(ev));
        given(regions.getNames()).willReturn(List.of("EU/NA", "EU"));
        mockMvc.perform(get("/settings"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Clan Image")))
                .andExpect(content().string(Matchers.containsString("Message of the Day")))
                .andExpect(content().string(Matchers.containsString("Clan Ladder Night")))
                .andExpect(content().string(Matchers.containsString("/settings/events/21/remove")))
                .andExpect(content().string(Matchers.containsString("<option value=\"EU/NA\">")));
    }

    @Test
    @WithMockUser("admin")
    @DisplayName("the invite form offers the moderator role to the owner only")
    void inviteRoleForOwnerOnly() throws Exception {
        signedInAs(ownerRow, 0);
        given(invitations.sent(clan)).willReturn(List.of());
        mockMvc.perform(get("/invitations"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Invite as Moderator")));
        signedInAs(membership(9L, clan, admin, "MODERATOR"), 0);
        mockMvc.perform(get("/invitations"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Invite as Member")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Invite as Moderator"))));
    }

    @Test
    @DisplayName("the logo route serves the stored bytes with their type, and 404 when there is none")
    void logoRoute() throws Exception {
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 13, 'I', 'H', 'D', 'R'};
        given(management.logo(eq(7L), any())).willReturn(Optional.of(new ClanLogo(7L, png, "image/png")));
        given(management.logo(eq(8L), any())).willReturn(Optional.empty());
        mockMvc.perform(get("/clans/7/logo"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(png));
        mockMvc.perform(get("/clans/8/logo")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser("admin")
    @DisplayName("creating a clan with an image checks the image first, then saves it after the clan")
    void createWithImage() throws Exception {
        signedInAs(null, 0);
        given(tagRule.getHint()).willReturn("1 to 3 letters or numbers");
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 13, 'I', 'H', 'D', 'R'};
        given(management.checkImage(png)).willReturn("image/png");
        mockMvc.perform(multipart("/create-clan").file(new MockMultipartFile("image", "logo.png", "image/png", png))
                        .param("name", "Bad Company").param("tag", "BC").param("region", "EU").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/clanhome"));
        verify(management).checkImage(png);
        verify(management).create(eq(admin), any(ClanForm.class));
        verify(management).setLogo(admin, png);
    }
}
