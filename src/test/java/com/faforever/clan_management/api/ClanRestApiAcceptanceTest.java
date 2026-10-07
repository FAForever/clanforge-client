//SPDX-License-Identifier: MIT
package com.faforever.clan_management.api;

import java.time.Instant;
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
import org.springframework.data.domain.Sort;
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
import com.faforever.clan_management.service.ClanCard;
import com.faforever.clan_management.service.ClanForm;
import com.faforever.clan_management.service.ClanManagementService;
import com.faforever.clan_management.service.ClanPatch;
import com.faforever.clan_management.service.ClanRuleViolation;
import com.faforever.clan_management.service.ClanService;
import com.faforever.clan_management.service.MemberCard;
import com.faforever.clan_management.service.MembershipService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Project Acceptance Test F (REST API), procedures F1-F6, at the HTTP contract level.
//
// The services are mocks, so this runs anywhere (CI included) with no database. The mocks
// are given just enough state - one Clan object that PATCH edits and DELETE soft-deletes -
// that "the update is reflected on a subsequent GET" and "a GET after DELETE is 404" are
// real round trips through the controller rather than two unrelated stubs.
// ClanRestApiAcceptanceIntegrationTest repeats F1-F6 against postgres and the real services.
@WebMvcTest(ClanApiController.class)
@Import(SecurityConfig.class)
class ClanRestApiAcceptanceTest {

    private static final String VALID_CREATE = """
            {
              "name": "Test F Clan",
              "tag": "TF1",
              "description": "Created by acceptance test F",
              "region": "Oceania",
              "publicClan": true,
              "requiresInvitation": false
            }
            """;

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

    private AppUser owner;
    private Clan clan;

    @BeforeEach
    void setUp() {
        owner = new AppUser("sub-42", "test_f_owner", "local", null);
        ReflectionTestUtils.setField(owner, "id", 42L);

        clan = new Clan("Test F Clan", "TF1", "Created by acceptance test F", owner, "PUBLIC", false);
        clan.setRegion("Oceania");
        ReflectionTestUtils.setField(clan, "id", 77L);

        given(users.require(any())).willReturn(owner);
        given(membershipService.roster(any())).willReturn(List.of(new MemberCard(new ClanMembership(clan, owner, "OWNER"))));
        // a GET sees the clan while it is live, and nothing once DELETE has soft-deleted it
        given(clanService.findVisibleClan(anyLong())).willAnswer(inv ->
                inv.getArgument(0).equals(77L) && clan.getDeletedAt() == null ? Optional.of(clan) : Optional.empty());
    }

    private String bearer() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token-sub-42")
                .header("alg", "RS256")
                .subject("sub-42")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
        given(jwtDecoder.decode("token-sub-42")).willReturn(jwt);
        return "Bearer token-sub-42";
    }

    // ---------------------------------------------------------------- F1

    @Test
    @DisplayName("F1: POST /api/clans with a valid payload returns 201 and a ClanDetailResponse")
    void f1PostValidClanReturns201WithSchema() throws Exception {
        given(clanManagementService.create(any(), any())).willReturn(clan);

        mockMvc.perform(post("/api/clans")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                // every field of the documented ClanDetailResponse schema, with its type
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.id").value(77))
                .andExpect(jsonPath("$.name").value("Test F Clan"))
                .andExpect(jsonPath("$.tag").value("TF1"))
                .andExpect(jsonPath("$.description").value("Created by acceptance test F"))
                .andExpect(jsonPath("$.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.requiresInvitation").value(false))
                .andExpect(jsonPath("$.region").value("Oceania"))
                .andExpect(jsonPath("$.hasLogo").isBoolean())
                .andExpect(jsonPath("$.ownerId").value(42))
                .andExpect(jsonPath("$.ownerDisplayName").value("test_f_owner"))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.members").isArray())
                .andExpect(jsonPath("$.members.length()").value(1))
                .andExpect(jsonPath("$.members[0].displayName").value("test_f_owner"))
                .andExpect(jsonPath("$.members[0].role").value("OWNER"))
                // nothing internal leaks into the response
                .andExpect(jsonPath("$.owner").doesNotExist())
                .andExpect(jsonPath("$.deletedAt").doesNotExist())
                .andExpect(jsonPath("$.members[0].user").doesNotExist());

        ArgumentCaptor<ClanForm> form = ArgumentCaptor.forClass(ClanForm.class);
        verify(clanManagementService).create(eq(owner), form.capture());
        assertThat(form.getValue().getName()).isEqualTo("Test F Clan");
        assertThat(form.getValue().getTag()).isEqualTo("TF1");
        assertThat(form.getValue().isPublicClan()).isTrue();
        assertThat(form.getValue().isRequiresInvitation()).isFalse();
    }

    @Test
    @DisplayName("F1: a POST that leaves out the optional fields defaults to a public, open clan")
    void f1PostDefaultsOptionalFields() throws Exception {
        given(clanManagementService.create(any(), any())).willReturn(clan);

        mockMvc.perform(post("/api/clans")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test F Clan\",\"tag\":\"TF1\"}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<ClanForm> form = ArgumentCaptor.forClass(ClanForm.class);
        verify(clanManagementService).create(any(), form.capture());
        assertThat(form.getValue().isPublicClan()).isTrue();
        assertThat(form.getValue().isRequiresInvitation()).isFalse();
    }

    // ---------------------------------------------------------------- F2

    @Test
    @DisplayName("F2: POST without the required name and tag returns 400 with a JSON message")
    void f2PostMissingRequiredFieldsReturns400() throws Exception {
        // the service is the one place the name and tag rules live, so the controller must hand
        // the blank fields through and turn the refusal into a 400 with its sentence
        given(clanManagementService.create(any(), any())).willAnswer(inv -> {
            ClanForm f = inv.getArgument(1);
            if (f.getName() == null || f.getName().isBlank()) throw new ClanRuleViolation("Give the clan a name.");
            throw new AssertionError("expected a blank name");
        });

        mockMvc.perform(post("/api/clans")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"no name, no tag\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Give the clan a name."));
    }

    @Test
    @DisplayName("F2: POST with a body that is not JSON returns 400 with a JSON message, not a stack trace")
    void f2PostMalformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/clans")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Unclosed"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("The request body could not be read as the JSON this route takes."));

        verifyNoInteractions(clanManagementService);
    }

    @Test
    @DisplayName("F2: POST with a field of the wrong type returns 400")
    void f2PostWrongFieldTypeReturns400() throws Exception {
        mockMvc.perform(post("/api/clans")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test F Clan\",\"tag\":\"TF1\",\"publicClan\":{\"not\":\"a boolean\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isString());

        verifyNoInteractions(clanManagementService);
    }

    @Test
    @DisplayName("F2: POST without a bearer token returns 401 and names the Bearer scheme")
    void f2PostWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/clans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")));

        verifyNoInteractions(clanManagementService);
    }

    // ---------------------------------------------------------------- F3

    @Test
    @DisplayName("F3: GET /api/clans returns 200 with a page that includes the new clan")
    void f3ListIncludesNewClan() throws Exception {
        Pageable firstPage = PageRequest.of(0, 20, Sort.by("name").ascending());
        given(clanService.searchPublicClanCards("Test F Clan", "all", "name", firstPage))
                .willReturn(new PageImpl<>(List.of(new ClanCard(clan, 1L)), firstPage, 1));

        mockMvc.perform(get("/api/clans").param("q", "Test F Clan"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.clans").isArray())
                .andExpect(jsonPath("$.clans[*].id", hasItem(77)))
                .andExpect(jsonPath("$.clans[0].name").value("Test F Clan"))
                .andExpect(jsonPath("$.clans[0].tag").value("TF1"))
                .andExpect(jsonPath("$.clans[0].description").value("Created by acceptance test F"))
                .andExpect(jsonPath("$.clans[0].requiresInvitation").value(false))
                .andExpect(jsonPath("$.clans[0].region").value("Oceania"))
                .andExpect(jsonPath("$.clans[0].hasLogo").isBoolean())
                .andExpect(jsonPath("$.clans[0].memberCount").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.hasPrevious").value(false))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("F3: GET /api/clans is public and needs no token")
    void f3ListNeedsNoToken() throws Exception {
        given(clanService.searchPublicClanCards(any(), any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/clans"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("F3: an out-of-range page number is clamped instead of failing")
    void f3NegativePageIsClamped() throws Exception {
        Pageable firstPage = PageRequest.of(0, 20, Sort.by("name").ascending());
        given(clanService.searchPublicClanCards("", "all", "name", firstPage))
                .willReturn(new PageImpl<>(List.of(), firstPage, 0));

        mockMvc.perform(get("/api/clans").param("page", "-5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0));
    }

    // ---------------------------------------------------------------- F4

    @Test
    @DisplayName("F4: GET /api/clans/{id} for the created clan returns 200 with the right data")
    void f4GetExistingClanReturns200() throws Exception {
        mockMvc.perform(get("/api/clans/77"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(77))
                .andExpect(jsonPath("$.name").value("Test F Clan"))
                .andExpect(jsonPath("$.tag").value("TF1"))
                .andExpect(jsonPath("$.description").value("Created by acceptance test F"))
                .andExpect(jsonPath("$.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.region").value("Oceania"))
                .andExpect(jsonPath("$.ownerId").value(42))
                .andExpect(jsonPath("$.members[0].role").value("OWNER"));
    }

    @Test
    @DisplayName("F4: GET /api/clans/{id} for an id that does not exist returns 404")
    void f4GetMissingClanReturns404() throws Exception {
        mockMvc.perform(get("/api/clans/999999"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/clans/999999/members"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("F4: GET /api/clans/{id} with an id that is not a number returns 400, not 500")
    void f4GetNonNumericIdReturns400() throws Exception {
        mockMvc.perform(get("/api/clans/not-a-number"))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- F5

    @Test
    @DisplayName("F5: PATCH /api/clans/{id} returns 200 and the change shows on a following GET")
    void f5PatchIsReflectedOnGet() throws Exception {
        // stand-in for the persistence layer: apply the patch to the one clan GET reads
        given(clanManagementService.patch(eq(owner), eq(77L), any())).willAnswer(inv -> {
            ClanPatch p = inv.getArgument(2);
            ClanForm f = ClanForm.of(clan);
            p.applyTo(f);
            clan.setName(f.getName());
            clan.setTag(f.getTag());
            clan.setDescription(f.getDescription());
            clan.setRegion(f.getRegion());
            clan.setVisibility(f.isPublicClan() ? "PUBLIC" : "PRIVATE");
            clan.setRequiresInvitation(f.isRequiresInvitation());
            return clan;
        });

        mockMvc.perform(patch("/api/clans/77")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test F Renamed\",\"description\":\"Patched by F5\",\"requiresInvitation\":true}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(77))
                .andExpect(jsonPath("$.name").value("Test F Renamed"))
                .andExpect(jsonPath("$.description").value("Patched by F5"))
                .andExpect(jsonPath("$.requiresInvitation").value(true));

        mockMvc.perform(get("/api/clans/77"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test F Renamed"))
                .andExpect(jsonPath("$.description").value("Patched by F5"))
                .andExpect(jsonPath("$.requiresInvitation").value(true))
                // fields the PATCH left out are unchanged
                .andExpect(jsonPath("$.tag").value("TF1"))
                .andExpect(jsonPath("$.region").value("Oceania"))
                .andExpect(jsonPath("$.visibility").value("PUBLIC"));

        ArgumentCaptor<ClanPatch> sent = ArgumentCaptor.forClass(ClanPatch.class);
        verify(clanManagementService).patch(eq(owner), eq(77L), sent.capture());
        assertThat(sent.getValue().tag()).isNull();
        assertThat(sent.getValue().region()).isNull();
        assertThat(sent.getValue().publicClan()).isNull();
    }

    @Test
    @DisplayName("F5: a PATCH that breaks a clan rule returns 400 with the rule's message")
    void f5PatchRuleViolationReturns400() throws Exception {
        given(clanManagementService.patch(any(), eq(77L), any()))
                .willThrow(new ClanRuleViolation("Clan tag must be 1 to 3 letters or numbers."));

        mockMvc.perform(patch("/api/clans/77")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tag\":\"TOOLONG\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Clan tag must be 1 to 3 letters or numbers."));
    }

    @Test
    @DisplayName("F5: PATCH without a bearer token returns 401 and changes nothing")
    void f5PatchWithoutTokenReturns401() throws Exception {
        mockMvc.perform(patch("/api/clans/77")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hijacked\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(clanManagementService);
        assertThat(clan.getName()).isEqualTo("Test F Clan");
    }

    // ---------------------------------------------------------------- F6

    @Test
    @DisplayName("F6: DELETE /api/clans/{id} returns 204 and a following GET returns 404")
    void f6DeleteThenGetReturns404() throws Exception {
        willAnswer(inv -> {
            clan.setDeletedAt(OffsetDateTime.now());
            return null;
        }).given(clanManagementService).delete(owner, 77L);

        mockMvc.perform(get("/api/clans/77"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/clans/77")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mockMvc.perform(get("/api/clans/77"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/clans/77/members"))
                .andExpect(status().isNotFound());

        verify(clanManagementService).delete(owner, 77L);
    }

    @Test
    @DisplayName("F6: DELETE without a bearer token returns 401 and the clan stays")
    void f6DeleteWithoutTokenReturns401() throws Exception {
        mockMvc.perform(delete("/api/clans/77"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(clanManagementService);
        mockMvc.perform(get("/api/clans/77"))
                .andExpect(status().isOk());
    }
}
