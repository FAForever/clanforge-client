package com.faforever.clan_management.api;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.BDDMockito.given;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.entity.ClanMembership;
import com.faforever.clan_management.security.SecurityConfig;
import com.faforever.clan_management.service.AppUserService;
import com.faforever.clan_management.service.ClanService;
import com.faforever.clan_management.service.MemberCard;
import com.faforever.clan_management.service.MembershipService;

@WebMvcTest(ClanApiController.class)
@Import(SecurityConfig.class)
class ClanApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClanService clanService;

    @MockitoBean
    private MembershipService membershipService;

    @MockitoBean
    private AppUserService appUserService;

    @Test
    @DisplayName("anonymous users can retrieve a public clan as JSON")
    void publicClanIsReturnedAsJson() throws Exception {
        AppUser owner = new AppUser(
                "oidc-10",
                "admin",
                "local",
                "10"
        );
        ReflectionTestUtils.setField(owner, "id", 10L);

        Clan clan = new Clan(
                "Bad Company",
                "BC",
                "Test clan",
                owner,
                "PUBLIC",
                false
        );
        ReflectionTestUtils.setField(clan, "id", 1L);
        clan.setMotd("Members only", owner);

        ClanMembership membership =
                new ClanMembership(clan, owner, "OWNER");

        given(clanService.findVisibleClan(1L))
                .willReturn(Optional.of(clan));

        given(membershipService.roster(clan))
                .willReturn(List.of(new MemberCard(membership)));

        mockMvc.perform(get("/api/clans/1"))
                .andExpect(status().isOk())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Bad Company"))
                .andExpect(jsonPath("$.tag").value("BC"))
                .andExpect(jsonPath("$.ownerId").value(10))
                .andExpect(jsonPath("$.ownerDisplayName").value("admin"))
                .andExpect(jsonPath("$.members[0].id").value(10))
                .andExpect(jsonPath("$.members[0].role").value("OWNER"))
                .andExpect(jsonPath("$.motd").doesNotExist());
    }

    @Test
    @DisplayName("a hidden or missing clan returns 404")
    void hiddenOrMissingClanReturnsNotFound() throws Exception {
        given(clanService.findVisibleClan(999L))
                .willReturn(Optional.empty());

        mockMvc.perform(get("/api/clans/999"))
                .andExpect(status().isNotFound());
    }
}