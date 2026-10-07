//SPDX-License-Identifier: MIT
package com.faforever.clan_management.api;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.security.SecurityConfig;
import com.faforever.clan_management.service.AppUserService;
import com.faforever.clan_management.service.ClanAccessRefused;
import com.faforever.clan_management.service.ClanManagementService;
import com.faforever.clan_management.service.ClanRuleViolation;
import com.faforever.clan_management.service.MembershipService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// the member-management routes act on the CALLER'S OWN clan; run through the real
// bearer chain with the services mocked, so no database is needed
@WebMvcTest(MembershipApiController.class)
@Import(SecurityConfig.class)
class MembershipApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;
    @MockitoBean
    private AppUserService users;
    @MockitoBean
    private MembershipService memberships;
    @MockitoBean
    private ClanManagementService clans;

    private final AppUser kai = user(1L, "kai");

    private static AppUser user(long id, String name) {
        AppUser u = new AppUser("sub-" + id, name, "local", null);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private String bearer(String sub) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token-" + sub).header("alg", "RS256").subject(sub).issuedAt(now).expiresAt(now.plusSeconds(300)).build();
        given(jwtDecoder.decode("token-" + sub)).willReturn(jwt);
        return "Bearer token-" + sub;
    }

    @Test
    @DisplayName("every member-management route answers 401 without a bearer token and never reaches a service")
    void closedWithoutToken() throws Exception {
        mockMvc.perform(delete("/api/clan/members/2")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/clan/members/2").contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MODERATOR\"}")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/clan/transfer").contentType(MediaType.APPLICATION_JSON).content("{\"userId\":2}")).andExpect(status().isUnauthorized());
        verifyNoInteractions(memberships);
        verifyNoInteractions(clans);
        verifyNoInteractions(users);
    }

    @Test
    @DisplayName("removing a member hands the caller and the user id to the service and answers 204")
    void removeAnswersNoContent() throws Exception {
        given(users.require(any())).willReturn(kai);
        mockMvc.perform(delete("/api/clan/members/2").header(HttpHeaders.AUTHORIZATION, bearer("sub-1")))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(memberships).removeMember(kai, 2L);
    }

    @Test
    @DisplayName("changing a role hands the user id and the role to the service and answers 204")
    void setRoleAnswersNoContent() throws Exception {
        given(users.require(any())).willReturn(kai);
        mockMvc.perform(patch("/api/clan/members/2").header(HttpHeaders.AUTHORIZATION, bearer("sub-1")).contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MODERATOR\"}"))
                .andExpect(status().isNoContent());
        verify(memberships).setRole(kai, 2L, "MODERATOR");
    }

    @Test
    @DisplayName("transferring ownership hands the new owner id to the service and answers 204")
    void transferAnswersNoContent() throws Exception {
        given(users.require(any())).willReturn(kai);
        mockMvc.perform(post("/api/clan/transfer").header(HttpHeaders.AUTHORIZATION, bearer("sub-1")).contentType(MediaType.APPLICATION_JSON).content("{\"userId\":2}"))
                .andExpect(status().isNoContent());
        verify(clans).transferOwnership(kai, 2L);
    }

    @Test
    @DisplayName("a refusal about who is asking comes back 403, and a broken clan rule 400, each with its sentence")
    void errorsMapToStatus() throws Exception {
        given(users.require(any())).willReturn(kai);
        willThrow(new ClanAccessRefused("Only the owner can change roles.")).given(memberships).setRole(eq(kai), eq(2L), any());
        mockMvc.perform(patch("/api/clan/members/2").header(HttpHeaders.AUTHORIZATION, bearer("sub-1")).contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only the owner can change roles."));
        willThrow(new ClanRuleViolation("That player is not in your clan.")).given(memberships).removeMember(kai, 9L);
        mockMvc.perform(delete("/api/clan/members/9").header(HttpHeaders.AUTHORIZATION, bearer("sub-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("That player is not in your clan."));
    }
}
