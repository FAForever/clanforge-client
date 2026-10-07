//SPDX-License-Identifier: MIT
package com.faforever.clan_management;

import java.time.Instant;
import java.util.List;

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
import com.faforever.clan_management.entity.ClanInvitation;
import com.faforever.clan_management.repository.AppUserRepository;
import com.faforever.clan_management.repository.ClanInvitationRepository;
import com.faforever.clan_management.repository.ClanEventRepository;
import com.faforever.clan_management.repository.ClanRepository;
import com.faforever.clan_management.service.ClanForm;
import com.faforever.clan_management.service.ClanManagementService;
import com.faforever.clan_management.service.InvitationService;
import com.faforever.clan_management.service.MembershipService;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

//the invitation routes against a real postgres, token to app_user row to invitation to membership, rolled back at the end
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
@Transactional
class InvitationApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private ClanRepository clans;
    @Autowired
    private ClanEventRepository events;
    @Autowired
    private ClanManagementService management;
    @Autowired
    private MembershipService memberships;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private InvitationService invitationService;
    @Autowired
    private ClanInvitationRepository invitationRows;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private String freeTag() {
        for (String t : List.of("ZQ9", "ZQ8","ZQ7", "XQ9", "XQ8")) {
            if (clans.findByTagIgnoreCaseAndDeletedAtIsNull(t).isEmpty()) return t;
        }
        throw new IllegalStateException("every candidate tag is taken");
    }

    //a token whose sub is the given subject, the only thing the chain and AppUserService read from it
    private String bearer(String sub) {
        Instant now = Instant.now();
        given(jwtDecoder.decode("token-" + sub)).willReturn(Jwt.withTokenValue("token-"+sub).header("alg", "RS256").subject(sub).issuedAt(now).expiresAt(now.plusSeconds(300)).build());
        return "Bearer token-" + sub;
    }

    private Clan clanOwnedBy(AppUser owner, String stamp) {
        ClanForm form = new ClanForm();
        form.setName("Api Clan " + stamp);
        form.setTag(freeTag());
        form.setRequiresInvitation(true);
        return management.create(owner,form);
    }

    @Test
    @DisplayName("invite, inbox, accept and decline run from token to database rows, each with its event")
    void inviteInboxAcceptDecline() throws Exception {
        long before = events.count();
        String stamp = Long.toString(System.nanoTime());
        AppUser owner = users.save(new AppUser("api-owner-" + stamp, "api_owner_" + stamp, "local", null));
        AppUser first = users.save(new AppUser("api-first-" + stamp,"api_first_" + stamp, "local", null));
        AppUser second = users.save(new AppUser("api-second-" + stamp, "api_second_" + stamp, "local",null));
        Clan clan = clanOwnedBy(owner,stamp);
        MvcResult sent = mockMvc.perform(post("/api/clans/" + clan.getId() + "/invite").header(HttpHeaders.AUTHORIZATION,bearer(owner.getOidcSubject())).contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"" + first.getDisplayName() + "\",\"role\":\"MODERATOR\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clanId").value(clan.getId()))
                .andExpect(jsonPath("$.inviteeId").value(first.getId()))
                .andExpect(jsonPath("$.inviterId").value(owner.getId()))
                .andExpect(jsonPath("$.state").value("PENDING"))
                .andReturn();
        long toFirst = json.readTree(sent.getResponse().getContentAsString()).path("id").asLong();
        mockMvc.perform(get("/api/invitations").header(HttpHeaders.AUTHORIZATION, bearer(first.getOidcSubject())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(toFirst))
                .andExpect(jsonPath("$[0].clanTag").value(clan.getTag()));
        mockMvc.perform(post("/api/invitations/" + toFirst + "/accept").header(HttpHeaders.AUTHORIZATION,bearer(first.getOidcSubject())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clanId").value(clan.getId()))
                .andExpect(jsonPath("$.userId").value(first.getId()))
                .andExpect(jsonPath("$.role").value("MODERATOR"));
        assertThat(memberships.roster(clan)).extracting(m -> m.getUser().getId()+":" + m.getRole()).containsExactly(owner.getId() + ":OWNER", first.getId() + ":MODERATOR");
        MvcResult again = mockMvc.perform(post("/api/clans/" + clan.getId() + "/invite").header(HttpHeaders.AUTHORIZATION, bearer(owner.getOidcSubject())).contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"" + second.getDisplayName()+"\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andReturn();
        long toSecond = json.readTree(again.getResponse().getContentAsString()).path("id").asLong();
        mockMvc.perform(post("/api/invitations/" + toSecond+"/decline").header(HttpHeaders.AUTHORIZATION, bearer(second.getOidcSubject()))).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/invitations").header(HttpHeaders.AUTHORIZATION, bearer(second.getOidcSubject())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        assertThat(memberships.roster(clan)).hasSize(2);
        assertThat(events.count()-before).isEqualTo(7); //created, owner joined, invite, accepted, joined, invite, declined
    }

    @Test
    @DisplayName("the refusals keep their status through the whole stack: 403 for who is asking, 400 for a broken rule, 401 with no token")
    void refusalsKeepTheirStatus() throws Exception {
        String stamp = Long.toString(System.nanoTime());
        AppUser owner = users.save(new AppUser("api-owner-" + stamp, "api_owner_" + stamp, "local", null));
        AppUser outsider = users.save(new AppUser("api-out-" + stamp, "api_out_"+stamp, "local", null));
        Clan clan = clanOwnedBy(owner, stamp);
        long other = clan.getId() + 1000000;
        mockMvc.perform(post("/api/clans/" + other + "/invite").header(HttpHeaders.AUTHORIZATION, bearer(owner.getOidcSubject())).contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"" + outsider.getDisplayName()+"\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You can only invite players to your own clan."));
        mockMvc.perform(post("/api/clans/" + clan.getId() + "/invite").header(HttpHeaders.AUTHORIZATION, bearer(outsider.getOidcSubject())).contentType(MediaType.APPLICATION_JSON).content("{\"username\":\""+owner.getDisplayName() + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You are not in a clan."));
        mockMvc.perform(post("/api/clans/" + clan.getId() + "/invite").header(HttpHeaders.AUTHORIZATION,bearer(owner.getOidcSubject())).contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"nobody_" + stamp + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No player named 'nobody_" + stamp + "'."));
        MvcResult theirs = mockMvc.perform(post("/api/clans/" + clan.getId() + "/invite").header(HttpHeaders.AUTHORIZATION, bearer(owner.getOidcSubject())).contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"" + outsider.getDisplayName()+"\"}")).andExpect(status().isCreated()).andReturn();
        long theirsId = json.readTree(theirs.getResponse().getContentAsString()).path("id").asLong();
        mockMvc.perform(post("/api/invitations/" + theirsId + "/accept").header(HttpHeaders.AUTHORIZATION, bearer(owner.getOidcSubject()))) //the owner trying the outsiders invitation
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("That invitation does not exist."));
        mockMvc.perform(post("/api/invitations/999999999/accept").header(HttpHeaders.AUTHORIZATION, bearer(outsider.getOidcSubject())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("That invitation does not exist."));
        mockMvc.perform(get("/api/invitations")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a subject the application has never seen gets its own app_user row on the first call and the same row on the second")
    void newSubjectGetsOneRow() throws Exception {
        String sub = "api-new-" + System.nanoTime();
        assertThat(users.findByOidcSubject(sub)).isEmpty();
        mockMvc.perform(get("/api/invitations").header(HttpHeaders.AUTHORIZATION,bearer(sub))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        Long id = users.findByOidcSubject(sub).orElseThrow().getId();
        mockMvc.perform(get("/api/invitations").header(HttpHeaders.AUTHORIZATION,bearer(sub))).andExpect(status().isOk());
        assertThat(users.findByOidcSubject(sub).orElseThrow().getId()).isEqualTo(id);
    }

    @Test
    @DisplayName("the clan behind an invitation is handed out for locking only to its invitee and only while it is open")
    void lockLookupSeesOnlyOpenInvitations() {
        String stamp = Long.toString(System.nanoTime());
        AppUser owner = users.save(new AppUser("lk-owner-"+stamp, "lk_owner_" + stamp, "local", null));
        AppUser invitee = users.save(new AppUser("lk-invitee-" + stamp,"lk_invitee_"+stamp, "local", null));
        Clan clan = clanOwnedBy(owner, stamp);
        ClanInvitation inv = invitationService.invite(owner, invitee.getDisplayName(), null);
        assertThat(invitationRows.findClanIdByIdAndInviteeId(inv.getId(), invitee.getId(), java.time.OffsetDateTime.now())).contains(clan.getId());
        assertThat(invitationRows.findClanIdByIdAndInviteeId(inv.getId(), owner.getId(), java.time.OffsetDateTime.now())).isEmpty(); //not the invitee
        invitationService.decline(invitee, inv.getId());
        assertThat(invitationRows.findClanIdByIdAndInviteeId(inv.getId(), invitee.getId(),java.time.OffsetDateTime.now())).isEmpty(); //answered, so no way onto the lock
        ClanInvitation lapsed = invitationService.invite(owner,invitee.getDisplayName(), null);
        jdbc.update("update clan_invitation set expires_at = now() - interval '1 minute' where id = ?", lapsed.getId()); //still pending, but past its expiry
        assertThat(invitationRows.findClanIdByIdAndInviteeId(lapsed.getId(), invitee.getId(), java.time.OffsetDateTime.now())).isEmpty();
    }
}
