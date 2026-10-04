package com.faforever.clan_management.api;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import com.faforever.clan_management.entity.ClanMembership;
import com.faforever.clan_management.security.SecurityConfig;
import com.faforever.clan_management.service.AppUserService;
import com.faforever.clan_management.service.ClanForm;
import com.faforever.clan_management.service.ClanManagementService;
import com.faforever.clan_management.service.ClanRuleViolation;
import com.faforever.clan_management.service.ClanService;
import com.faforever.clan_management.service.MemberCard;
import com.faforever.clan_management.service.MembershipService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClanApiController.class)
@Import(SecurityConfig.class)
class ClanCreateApiControllerTest {

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
    @DisplayName("creating a clan requires a bearer token")
    void createRequiresBearerToken() throws Exception {
        String body = "{\"name\":\"Bad Company\",\"tag\":\"BC\"}";

        mockMvc.perform(post("/api/clans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(users, clanManagementService, membershipService);
    }

    @Test
    @DisplayName("creating passes every form field to the service and returns 201")
    void createClan() throws Exception {
        given(users.require(any())).willReturn(testOwner);
        given(clanManagementService.create(any(), any())).willReturn(clan);
        ClanMembership owner = new ClanMembership(clan, testOwner, "OWNER");
        given(membershipService.roster(clan))
                .willReturn(List.of(new MemberCard(owner)));

        mockMvc.perform(post("/api/clans")
                        .header(HttpHeaders.AUTHORIZATION, bearer("sub-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Bad Company",
                                  "tag": "BC",
                                  "description": "Test clan",
                                  "region": "Oceania",
                                  "publicClan": false,
                                  "requiresInvitation": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.name").value("Bad Company"))
                .andExpect(jsonPath("$.members[0].role").value("OWNER"));

        ArgumentCaptor<ClanForm> form = ArgumentCaptor.forClass(ClanForm.class);
        verify(clanManagementService).create(any(), form.capture());
        assertThat(form.getValue().getName()).isEqualTo("Bad Company");
        assertThat(form.getValue().getTag()).isEqualTo("BC");
        assertThat(form.getValue().getDescription()).isEqualTo("Test clan");
        assertThat(form.getValue().getRegion()).isEqualTo("Oceania");
        assertThat(form.getValue().isPublicClan()).isFalse();
        assertThat(form.getValue().isRequiresInvitation()).isTrue();
    }

    @Test
    @DisplayName("a create rule violation is returned as JSON 400")
    void createRuleViolationIsJson() throws Exception {
        given(users.require(any())).willReturn(testOwner);
        given(clanManagementService.create(any(), any()))
                .willThrow(new ClanRuleViolation("The clan tag is already in use."));

        mockMvc.perform(post("/api/clans")
                        .header(HttpHeaders.AUTHORIZATION, bearer("sub-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bad Company\",\"tag\":\"BC\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("The clan tag is already in use."));

    }

}
