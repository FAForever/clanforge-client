package com.faforever.clan_management.api;

import java.time.Instant;
import java.util.List;

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
import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.security.SecurityConfig;
import com.faforever.clan_management.service.AppUserService;
import com.faforever.clan_management.service.ClanAccessRefused;
import com.faforever.clan_management.service.ClanManagementService;
import com.faforever.clan_management.service.ClanService;
import com.faforever.clan_management.service.MembershipService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClanApiController.class)
@Import(SecurityConfig.class)
class ClanUpdateDeleteApiControllerTest {

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
    private final Clan clan = clan(7L, testOwner);

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

    @Test
    @DisplayName("updating and deleting require a bearer token")
    void writesRequireBearerToken() throws Exception {
        mockMvc.perform(patch("/api/clans/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bad Company\",\"tag\":\"BC\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/clans/7"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(users, clanManagementService, membershipService);
    }

    @Test
    @DisplayName("updating passes the path clan to the service")
    void updateClan() throws Exception {
        given(users.require(any())).willReturn(testOwner);
        given(clanManagementService.update(any(), eq(7L), any()))
                .willReturn(clan);
        given(membershipService.roster(clan)).willReturn(List.of());

        mockMvc.perform(patch("/api/clans/7")
                        .header(HttpHeaders.AUTHORIZATION, bearer("sub-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bad Company\",\"tag\":\"BC\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));

        verify(clanManagementService).update(any(), eq(7L), any());
    }

    @Test
    @DisplayName("deleting passes the path clan to the service and returns 204")
    void deleteClan() throws Exception {
        given(users.require(any())).willReturn(testOwner);

        mockMvc.perform(delete("/api/clans/7")
                        .header(HttpHeaders.AUTHORIZATION, bearer("sub-1")))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(clanManagementService).delete(testOwner, 7L);
    }

    @Test
    @DisplayName("using another clan id returns JSON 403")
    void differentClanIsForbidden() throws Exception {
        given(users.require(any())).willReturn(testOwner);
        given(clanManagementService.update(any(), eq(9L), any()))
                .willThrow(new ClanAccessRefused(
                        "You can only edit your own clan."
                ));

        mockMvc.perform(patch("/api/clans/9")
                        .header(HttpHeaders.AUTHORIZATION, bearer("sub-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bad Company\",\"tag\":\"BC\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("You can only edit your own clan."));
    }
}
