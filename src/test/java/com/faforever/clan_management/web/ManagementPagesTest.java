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
import com.faforever.clan_management.service.ClanForm;
import com.faforever.clan_management.service.ClanManagementService;
import com.faforever.clan_management.service.ClanRuleViolation;
import com.faforever.clan_management.service.ClanService;
import com.faforever.clan_management.service.InvitationCard;
import com.faforever.clan_management.service.InvitationService;
import com.faforever.clan_management.service.MemberCard;
import com.faforever.clan_management.service.MembershipService;
import com.faforever.clan_management.service.SignedInUser;
import com.faforever.clan_management.service.TagRule;

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
    private ClanService clanService;
    @MockitoBean
    private TagRule tagRule;

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
        //System.out.println("membership " + id + " " + role);
        return m;
    }

    private void signedInAs(ClanMembership membership,long pending) {
        given(users.signedIn(any())).willReturn(Optional.of(new SignedInUser(admin,membership, pending)));
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
                .andExpect(content().string(Matchers.not(Matchers.containsString("Leave Clan"))));
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
        ClanInvitation in = new ClanInvitation(other,admin, sam,OffsetDateTime.now().plusDays(3));
        ReflectionTestUtils.setField(in, "id", 11L);
        ClanInvitation out = new ClanInvitation(clan, sam, admin, OffsetDateTime.now().plusDays(3));
        ReflectionTestUtils.setField(out, "id", 12L);
        given(invitations.inbox(admin)).willReturn(List.of(new InvitationCard(in)));
        given(invitations.sent(clan)).willReturn(List.of(new InvitationCard(out)));
        mockMvc.perform(get("/invitations"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Eloreem&#39;s Guard [EGD]")))
                .andExpect(content().string(Matchers.containsString("/invitations/11/accept")))
                .andExpect(content().string(Matchers.containsString("Invites Sent")))
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
        //System.out.println("post join anonymous");
        mockMvc.perform(post("/clans/1/join").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
