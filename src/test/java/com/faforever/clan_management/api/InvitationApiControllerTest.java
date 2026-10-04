//SPDX-License-Identifier: MIT
package com.faforever.clan_management.api;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.entity.ClanInvitation;
import com.faforever.clan_management.entity.ClanMembership;
import com.faforever.clan_management.security.SecurityConfig;
import com.faforever.clan_management.service.AppUserService;
import com.faforever.clan_management.service.ClanAccessRefused;
import com.faforever.clan_management.service.ClanRuleViolation;
import com.faforever.clan_management.service.InvitationCard;
import com.faforever.clan_management.service.InvitationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

//the four invitation routes through the real bearer chain, with the services mocked so no database is needed
@WebMvcTest(InvitationApiController.class)
@Import(SecurityConfig.class)
class InvitationApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;
    @MockitoBean
    private AppUserService users;
    @MockitoBean
    private InvitationService invitations;

    private final AppUser kai = user(1L, "kai");
    private final AppUser sam = user(2L, "sam");
    private final Clan clan = clan(7L, "Bad Company","BC", kai);

    private static AppUser user(long id,String name) {
        AppUser u = new AppUser("sub-" + id, name,"local", null);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private static Clan clan(long id, String name, String tag, AppUser owner) {
        Clan c = new Clan(name, tag,null, owner, "PUBLIC", true);
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    private ClanInvitation invitation(long id, String role) {
        ClanInvitation i = new ClanInvitation(clan, sam, kai,OffsetDateTime.now().plusDays(7), role);
        ReflectionTestUtils.setField(i, "id", id);
        return i;
    }

    // a token the mocked decoder accepts, its sub is what the chain hands on as the callers name
    private String bearer(String sub) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token-"+sub).header("alg", "RS256").subject(sub).issuedAt(now).expiresAt(now.plusSeconds(300)).build();
        given(jwtDecoder.decode("token-" + sub)).willReturn(jwt);
        //System.out.println("bearer " + sub);
        return "Bearer token-" + sub;
    }

    @Test
    @DisplayName("every invitation route answers 401 without a bearer token and never reaches a service")
    void closedWithoutToken() throws Exception {
        mockMvc.perform(post("/api/clans/7/invite").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"sam\"}")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/invitations")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/invitations/8/accept")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/invitations/8/decline")).andExpect(status().isUnauthorized());
        verifyNoInteractions(invitations);
        verifyNoInteractions(users);
    }

    @Test
    @DisplayName("inviting hands the path id, the name and the role to the service and answers 201 with the invitation")
    void inviteAnswersCreated() throws Exception {
        given(users.require(any())).willReturn(kai);
        given(invitations.invite(kai,7L, "sam","MODERATOR")).willReturn(invitation(8L, "MODERATOR"));
        mockMvc.perform(post("/api/clans/7/invite").header(HttpHeaders.AUTHORIZATION, bearer("sub-1")).contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"sam\",\"role\":\"MODERATOR\"}"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(8))
                .andExpect(jsonPath("$.clanId").value(7))
                .andExpect(jsonPath("$.clanTag").value("BC"))
                .andExpect(jsonPath("$.inviteeId").value(2))
                .andExpect(jsonPath("$.inviteeDisplayName").value("sam"))
                .andExpect(jsonPath("$.inviterDisplayName").value("kai"))
                .andExpect(jsonPath("$.role").value("MODERATOR"))
                .andExpect(jsonPath("$.state").value("PENDING"))
                .andExpect(jsonPath("$.clanName").value("Bad Company"))
                .andExpect(jsonPath("$.inviterId").value(1))
                .andExpect(jsonPath("$.expiresAt").value(org.hamcrest.Matchers.startsWith(java.time.LocalDate.now().plusDays(7).toString()))) //a week out, the invitations own expiry and not its creation
                .andExpect(jsonPath("$.createdAt").value(org.hamcrest.Matchers.startsWith(java.time.LocalDate.now().toString())));
        ArgumentCaptor<Authentication> who = ArgumentCaptor.forClass(Authentication.class);
        verify(users).require(who.capture());
        //System.out.println("caller " + who.getValue().getName());
        assertThat(who.getValue().getName()).isEqualTo("sub-1"); //the tokens sub is the name the user lookup keys on
    }

    @Test
    @DisplayName("a missing role reaches the service as null, which is where the member default lives")
    void inviteWithoutRole() throws Exception {
        given(users.require(any())).willReturn(kai);
        given(invitations.invite(kai, 7L, "sam", null)).willReturn(invitation(8L, "MEMBER"));
        mockMvc.perform(post("/api/clans/7/invite").header(HttpHeaders.AUTHORIZATION, bearer("sub-1")).contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"sam\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("MEMBER"));
    }

    @Test
    @DisplayName("a broken clan rule comes back as 400 with the sentence the service gave")
    void ruleViolationIsBadRequest() throws Exception {
        given(users.require(any())).willReturn(kai);
        given(invitations.invite(kai, 7L,"nobody", null)).willThrow(new ClanRuleViolation("No player named 'nobody'."));
        mockMvc.perform(post("/api/clans/7/invite").header(HttpHeaders.AUTHORIZATION, bearer("sub-1")).contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"nobody\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No player named 'nobody'."));
    }

    @Test
    @DisplayName("a refusal about who is asking comes back as 403 with its sentence")
    void accessRefusalIsForbidden() throws Exception {
        given(users.require(any())).willReturn(sam);
        given(invitations.invite(sam,9L, "kai", null)).willThrow(new ClanAccessRefused("You can only invite players to your own clan."));
        mockMvc.perform(post("/api/clans/9/invite").header(HttpHeaders.AUTHORIZATION,bearer("sub-2")).contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"kai\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You can only invite players to your own clan."));
        given(invitations.accept(sam, 8L)).willThrow(new ClanAccessRefused("Only the owner can invite as moderator."));
        mockMvc.perform(post("/api/invitations/8/accept").header(HttpHeaders.AUTHORIZATION, bearer("sub-2")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only the owner can invite as moderator."));
    }

    @Test
    @DisplayName("the inbox lists the callers open invitations in the order the service gives")
    void inboxListsInvitations() throws Exception {
        given(users.require(any())).willReturn(sam);
        given(invitations.inbox(sam)).willReturn(List.of(new InvitationCard(invitation(9L, "MEMBER")), new InvitationCard(invitation(8L, "MODERATOR"))));
        mockMvc.perform(get("/api/invitations").header(HttpHeaders.AUTHORIZATION, bearer("sub-2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(9))
                .andExpect(jsonPath("$[0].clanName").value("Bad Company"))
                .andExpect(jsonPath("$[1].id").value(8))
                .andExpect(jsonPath("$[1].role").value("MODERATOR"));
        verify(users, never()).signedIn(any()); // the top bar lookup belongs to the pages, not to these routes
    }

    @Test
    @DisplayName("accepting answers with the clan joined and the role the invite offered")
    void acceptAnswersWithMembership() throws Exception {
        given(users.require(any())).willReturn(sam);
        ClanMembership m = new ClanMembership(clan, sam, "MODERATOR");
        given(invitations.accept(sam,8L)).willReturn(m);
        mockMvc.perform(post("/api/invitations/8/accept").header(HttpHeaders.AUTHORIZATION, bearer("sub-2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clanId").value(7))
                .andExpect(jsonPath("$.clanName").value("Bad Company"))
                .andExpect(jsonPath("$.clanTag").value("BC"))
                .andExpect(jsonPath("$.userId").value(2))
                .andExpect(jsonPath("$.role").value("MODERATOR"))
                .andExpect(jsonPath("$.joinedAt").exists());
    }

    @Test
    @DisplayName("declining answers 204 with no body once the service has closed the invite")
    void declineAnswersNoContent() throws Exception {
        given(users.require(any())).willReturn(sam);
        mockMvc.perform(post("/api/invitations/8/decline").header(HttpHeaders.AUTHORIZATION,bearer("sub-2")))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(invitations).decline(sam,8L);
        //System.out.println("declined 8");
    }
}
