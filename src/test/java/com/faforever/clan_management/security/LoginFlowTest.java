package com.faforever.clan_management.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.faforever.clan_management.web.PageController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What happens around a sign-in, as opposed to which routes are protected.
 *
 * Both cases here are regressions rather than new ground: each one describes
 * behaviour that was observed to be wrong against the running application, and
 * fails if the fix is undone.
 */
@WebMvcTest(PageController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("dev")
class LoginFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("signing in returns you to the page you were trying to reach")
    void loginReturnsToTheSavedRequest() throws Exception {
        // Ask for a protected page while signed out. Spring Security stores the
        // request in the session and bounces to the login page.
        MvcResult bounced = mockMvc.perform(get("/settings"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        MockHttpSession session = (MockHttpSession) bounced.getRequest().getSession(false);

        // Signing in with that same session must resume the saved request. The
        // earlier defaultSuccessUrl("/clans", true) discarded it and sent
        // everybody to /clans no matter what they had asked for.
        mockMvc.perform(post("/login")
                        .session(session)
                        .with(csrf())
                        .param("username", "admin")
                        .param("password", "admin"))
                .andExpect(status().is3xxRedirection())
                // ?continue is Spring Security's own marker on a replayed saved
                // request, not part of the path. What matters is /settings and
                // not /clans.
                .andExpect(redirectedUrlPattern("**/settings*"));
    }

    @Test
    @DisplayName("correct sign-ins are not counted against the rate limit")
    void repeatedSuccessfulLoginsAreNotRateLimited() throws Exception {
        // One more than the cap, all of them correct. Before the fix the filter
        // counted successes too, so the sixth came back 429 and one address
        // could sign in only five times a minute.
        for (int attempt = 1; attempt <= LoginRateLimitFilter.MAX_ATTEMPTS + 1; attempt++) {
            mockMvc.perform(post("/login")
                            .with(csrf())
                            .param("username", "admin")
                            .param("password", "admin"))
                    .andExpect(status().is3xxRedirection());
        }
    }
}
