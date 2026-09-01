package com.faforever.clan_management;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.faforever.clan_management.service.ClanService;

import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Access-control and security-header tests.
 *
 * Uses @WebMvcTest so the web and security layers load without JPA or a
 * database - these rules are testable before any endpoint or entity exists.
 */
@WebMvcTest
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    // ClanController is now part of the web layer, so the slice needs its
    // collaborator. These tests are about routing and headers, not clan data.
    @MockitoBean
    private ClanService clanService;

    @Test
    @DisplayName("anonymous users may reach the public clan routes")
    void publicRoutesArePermitted() throws Exception {
        // 200, not a redirect to login. The point of this test is that anonymous
        // callers are not bounced to the login page, whatever the route returns.
        org.mockito.BDDMockito.given(clanService.findPublicClanCards(org.mockito.ArgumentMatchers.any()))
                .willReturn(Page.empty());
        mockMvc.perform(get("/clans")).andExpect(status().isOk());

        // an unknown clan is a 404 for anonymous callers, still not a login redirect
        org.mockito.BDDMockito.given(clanService.findVisibleClan(1L)).willReturn(Optional.empty());
        mockMvc.perform(get("/clans/1")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("anonymous users are sent to login for anything else")
    void protectedRoutesRequireAuthentication() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("/error stays reachable so real error responses are not masked")
    void errorPathIsPermitted() throws Exception {
        // Spring forwards unhandled 404s to /error as an ERROR dispatch, and the
        // security filter chain applies to those too. If /error were not permitted
        // every anonymous 404 would surface as an access-denied response instead.
        mockMvc.perform(get("/error"))
                .andExpect(status().is(org.hamcrest.Matchers.not(302)));
    }

    @Test
    @DisplayName("security headers are set on every response")
    void securityHeadersArePresent() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().exists("Permissions-Policy"));
    }

    @Test
    @DisplayName("the content security policy blocks inline script and framing")
    void contentSecurityPolicyIsStrict() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("default-src 'self'"),
                                org.hamcrest.Matchers.containsString("object-src 'none'"),
                                org.hamcrest.Matchers.containsString("frame-ancestors 'none'"),
                                org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("unsafe-inline")))));
    }
}
