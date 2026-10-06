package com.faforever.clan_management.api;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.entity.ClanMembership;
import com.faforever.clan_management.security.SecurityConfig;
import com.faforever.clan_management.service.AppUserService;
import com.faforever.clan_management.service.ClanManagementService;
import com.faforever.clan_management.service.ClanService;
import com.faforever.clan_management.service.MemberCard;
import com.faforever.clan_management.service.MembershipService;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClanApiController.class)
@Import(SecurityConfig.class)
class ClanMembersApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AppUserService users;
    @MockitoBean
    private ClanService clanService;
    @MockitoBean
    private ClanManagementService clanManagementService;
    @MockitoBean
    private MembershipService membershipService;

    @Test
    @DisplayName("anyone can list the members of a public clan")
    void publicClanMembersAreReturned() throws Exception {
        AppUser owner = user(1L, "test-owner");
        AppUser member = user(2L, "test-member");
        Clan clan = clan(7L, owner);
        given(clanService.findVisibleClan(7L)).willReturn(Optional.of(clan));
        given(membershipService.roster(clan)).willReturn(List.of(
                new MemberCard(new ClanMembership(clan, owner, "OWNER")),
                new MemberCard(new ClanMembership(clan, member, "MEMBER"))
        ));

        mockMvc.perform(get("/api/clans/7/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].displayName").value("test-owner"))
                .andExpect(jsonPath("$[0].role").value("OWNER"))
                .andExpect(jsonPath("$[0].id").doesNotExist())
                .andExpect(jsonPath("$[0].joinedAt").doesNotExist())
                .andExpect(jsonPath("$[1].displayName").value("test-member"))
                .andExpect(jsonPath("$[1].role").value("MEMBER"));
    }

    @Test
    @DisplayName("members of a private or missing clan are not exposed")
    void hiddenClanMembersReturnNotFound() throws Exception {
        given(clanService.findVisibleClan(99L)).willReturn(Optional.empty());

        mockMvc.perform(get("/api/clans/99/members"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(membershipService);
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
