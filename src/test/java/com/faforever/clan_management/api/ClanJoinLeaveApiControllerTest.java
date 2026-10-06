package com.faforever.clan_management.api;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.entity.ClanMembership;
import com.faforever.clan_management.security.SecurityConfig;
import com.faforever.clan_management.service.AppUserService;
import com.faforever.clan_management.service.ClanAccessRefused;
import com.faforever.clan_management.service.ClanManagementService;
import com.faforever.clan_management.service.ClanService;
import com.faforever.clan_management.service.MembershipService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClanApiController.class)
@Import(SecurityConfig.class)
class ClanJoinLeaveApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;
    @MockitoBean
    private AppUserService users;
    @MockitoBean
    private ClanService clanService;
    @MockitoBean
    private ClanManagementService clanManagementService;
    @MockitoBean
    private MembershipService membershipService;

    private final AppUser testOwner = user(1L, "test-owner");
    private final AppUser testMember = user(2L, "test-member");
    private final Clan clan = clan(7L, testOwner);

    @Test
    @DisplayName("joining and leaving require a bearer token")
    void membershipWritesRequireBearerToken() throws Exception {
        mockMvc.perform(post("/api/clans/7/join"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/clans/7/leave"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(users, membershipService);
    }

    @Test
    @DisplayName("joining returns the new membership")
    void joinClan() throws Exception {
        given(users.require(any())).willReturn(testMember);
        ClanMembership membership = new ClanMembership(clan, testMember, "MEMBER");
        given(membershipService.join(7L, testMember)).willReturn(membership);

        mockMvc.perform(post("/api/clans/7/join")
                        .header(HttpHeaders.AUTHORIZATION, bearer("sub-2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clanId").value(7))
                .andExpect(jsonPath("$.clanName").value("Bad Company"))
                .andExpect(jsonPath("$.userId").value(2))
                .andExpect(jsonPath("$.role").value("MEMBER"));
    }

    @Test
    @DisplayName("leaving checks the path clan and returns 204")
    void leaveClan() throws Exception {
        given(users.require(any())).willReturn(testMember);

        mockMvc.perform(post("/api/clans/7/leave")
                        .header(HttpHeaders.AUTHORIZATION, bearer("sub-2")))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(membershipService).leave(7L, testMember);
    }

    @Test
    @DisplayName("leaving a different clan id returns JSON 403")
    void leaveDifferentClanIsForbidden() throws Exception {
        given(users.require(any())).willReturn(testMember);
        org.mockito.Mockito.doThrow(new ClanAccessRefused(
                "You can only leave your own clan."
        )).when(membershipService).leave(9L, testMember);

        mockMvc.perform(post("/api/clans/9/leave")
                        .header(HttpHeaders.AUTHORIZATION, bearer("sub-2")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("You can only leave your own clan."));
    }

    private String bearer(String sub) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token-" + sub)
                .header("alg", "RS256")
                .subject(sub)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
        given(jwtDecoder.decode("token-" + sub)).willReturn(jwt);
        return "Bearer token-" + sub;
    }

    private static AppUser user(long id, String name) {
        AppUser user = new AppUser("sub-" + id, name, "local", null);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static Clan clan(long id, AppUser owner) {
        Clan clan = new Clan(
                "Bad Company",
                "BC",
                "Test clan",
                owner,
                "PUBLIC",
                false
        );
        ReflectionTestUtils.setField(clan, "id", id);
        return clan;
    }
}
