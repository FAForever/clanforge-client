package com.faforever.clan_management.web;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.assertj.core.api.Assertions;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.faforever.clan_management.security.SecurityConfig;
import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.service.ClanCard;
import com.faforever.clan_management.service.ClanService;
import com.faforever.clan_management.service.AppUserService;
import com.faforever.clan_management.service.ClanManagementService;
import com.faforever.clan_management.service.MembershipService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(ClanController.class)
@Import(SecurityConfig.class)
class ClanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClanService clanService;

    // the roster and the signed-in person on the detail page, and the
    // top bar on every page, come from these two
    @MockitoBean
    private MembershipService membershipService;

    @MockitoBean
    private AppUserService appUserService;

    @MockitoBean
    private ClanManagementService clanManagementService;

    /**
     * Clan has a protected constructor and no setters, which is correct for a
     * JPA-managed entity but means tests cannot build one directly. Populate the
     * fields reflectively rather than widening the entity's API for tests.
     */
    private Clan clan(long id, String name, String tag) {
        Clan clan = BeanUtils.instantiateClass(Clan.class);
        ReflectionTestUtils.setField(clan, "id", id);
        ReflectionTestUtils.setField(clan, "name", name);
        ReflectionTestUtils.setField(clan, "tag", tag);
        ReflectionTestUtils.setField(clan, "createdAt", OffsetDateTime.parse("2014-02-16T00:00:00Z"));
        ReflectionTestUtils.setField(clan, "updatedAt", OffsetDateTime.parse("2014-02-16T00:00:00Z"));
        return clan;
    }

    @Test
    @DisplayName("anonymous users can browse the clan list")
    void listIsPublic() throws Exception {
        Page<Clan> page = new PageImpl<>(
                List.of(clan(1L, "FAF Developers", "DEV")), PageRequest.of(0, 20), 1);
        given(clanService.searchPublicClanCards(anyString(), anyString(), anyString(), any(Pageable.class))).willReturn(page.map(c -> new ClanCard(c, 7L)));

        mockMvc.perform(get("/clans"))
                .andExpect(status().isOk())
                .andExpect(view().name("clans/list"))
                .andExpect(model().attribute("totalClans", 1L))
                .andExpect(content().string(Matchers.containsString("FAF Developers")));
    }

    @Test
    @DisplayName("anonymous users can open a public clan")
    void detailIsPublicForVisibleClan() throws Exception {
        given(clanService.findVisibleClan(1L)).willReturn(Optional.of(clan(1L, "Bad Company", "BC")));

        mockMvc.perform(get("/clans/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("clans/detail"))
                .andExpect(content().string(Matchers.containsString("Bad Company")));
    }

    @Test
    @DisplayName("a clan the caller may not see returns 404, not 403")
    void hiddenClanIsIndistinguishableFromMissing() throws Exception {
        // ClanService returns empty for both a private clan and one that does not
        // exist. Answering 403 for private would confirm the clan exists and let
        // anyone enumerate private clans by walking ids.
        given(clanService.findVisibleClan(999L)).willReturn(Optional.empty());

        mockMvc.perform(get("/clans/999")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("clan names are escaped, not rendered as markup")
    void clanNamesAreEscaped() throws Exception {
        // Clan names are user supplied. Thymeleaf's th:text escapes them; this
        // test fails if someone switches it to th:utext.
        Page<Clan> page = new PageImpl<>(
                List.of(clan(1L, "<script>alert(1)</script>", "XSS")), PageRequest.of(0, 20), 1);
        given(clanService.searchPublicClanCards(anyString(), anyString(), anyString(), any(Pageable.class))).willReturn(page.map(c -> new ClanCard(c, 7L)));

        mockMvc.perform(get("/clans"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.not(
                        Matchers.containsString("<script>alert(1)</script>"))))
                .andExpect(content().string(Matchers.containsString("&lt;script&gt;")));
    }

    @Test
    @DisplayName("an absurd page number is clamped instead of hitting the database")
    void pageNumberIsClamped() throws Exception {
        given(clanService.searchPublicClanCards(anyString(), anyString(), anyString(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(), PageRequest.of(10_000, 20), 0));

        mockMvc.perform(get("/clans").param("page", "999999999"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        Mockito.verify(clanService).searchPublicClanCards(anyString(), anyString(), anyString(), captor.capture());
        Assertions.assertThat(captor.getValue().getPageNumber()).isLessThanOrEqualTo(10_000);
    }
}
