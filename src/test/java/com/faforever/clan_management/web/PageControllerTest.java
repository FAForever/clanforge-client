package com.faforever.clan_management.web;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.faforever.clan_management.SecurityConfig;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(PageController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("dev")
class PageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("the landing page renders without opening the login popup")
    void landingPageIsPublic() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(content().string(Matchers.containsString("action=\"/login\"")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("wrapper active-popup"))));
    }

    @Test
    @DisplayName("the custom login page opens the popup and contains a CSRF-protected form")
    void customLoginPageIsPublic() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(content().string(Matchers.containsString("wrapper active-popup")))
                .andExpect(content().string(Matchers.containsString("action=\"/login\"")))
                .andExpect(content().string(Matchers.containsString("name=\"_csrf\"")));
    }

    @Test
    @DisplayName("anonymous users are redirected from management pages to the custom login page")
    void managementPagesRequireLogin() throws Exception {
        mockMvc.perform(get("/clanhome"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("the development login accepts the form and continues to the real clan list")
    void developmentLoginUsesCustomForm() throws Exception {
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "admin")
                        .param("password", "admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/clans"));
    }

    @Test
    @WithMockUser
    @DisplayName("authenticated users can render every migrated management template")
    void authenticatedUsersCanOpenMigratedPages() throws Exception {
        mockMvc.perform(get("/clanhome"))
                .andExpect(status().isOk())
                .andExpect(view().name("clanhome"));
        mockMvc.perform(get("/create-clan"))
                .andExpect(status().isOk())
                .andExpect(view().name("create-clan"));
        mockMvc.perform(get("/invitations"))
                .andExpect(status().isOk())
                .andExpect(view().name("invitations"));
        mockMvc.perform(get("/settings"))
                .andExpect(status().isOk())
                .andExpect(view().name("settings"));
    }
}
