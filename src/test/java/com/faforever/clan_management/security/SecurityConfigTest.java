package com.faforever.clan_management.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.faforever.clan_management.service.AdminAccess;
import com.faforever.clan_management.service.AdminService;
import com.faforever.clan_management.service.AppUserService;
import com.faforever.clan_management.service.CalendarService;
import com.faforever.clan_management.service.Regions;
import com.faforever.clan_management.service.ClanManagementService;
import com.faforever.clan_management.service.ClanService;
import com.faforever.clan_management.service.InvitationService;
import com.faforever.clan_management.service.MembershipService;
import com.faforever.clan_management.service.ReportService;
import com.faforever.clan_management.service.TagRule;
import com.faforever.clan_management.api.OpenApiConfig;

import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springdoc.webmvc.ui.SwaggerConfig;

import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Access-control and security-header tests.
 *
 * Uses @WebMvcTest so the web and security layers load without JPA or a
 * database - these rules are testable before any endpoint or entity exists.
 */
@WebMvcTest
@Import({SecurityConfig.class, OpenApiConfig.class})
@ImportAutoConfiguration({
        SpringDocConfiguration.class,
        SpringDocConfigProperties.class,
        SpringDocWebMvcConfiguration.class,
        SwaggerConfig.class,
        SwaggerUiConfigProperties.class,
        SwaggerUiOAuthProperties.class
})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    // ClanController is now part of the web layer, so the slice needs its
    // collaborator. These tests are about routing and headers, not clan data.
    @MockitoBean
    private ClanService clanService;

    // The management pages and the top bar advice bring in the rest.
    @MockitoBean
    private AppUserService appUserService;

    @MockitoBean
    private MembershipService membershipService;

    @MockitoBean
    private ClanManagementService clanManagementService;

    @MockitoBean
    private InvitationService invitationService;

    @MockitoBean
    private TagRule tagRule;

    @MockitoBean
    private Regions regions;

    @MockitoBean
    private CalendarService calendarService;

    // The report and admin REST controllers are part of the slice too.
    @MockitoBean
    private ReportService reportService;

    @MockitoBean
    private AdminService adminService;

    @MockitoBean
    private AdminAccess adminAccess;

    @Test
    @DisplayName("anonymous users may reach the public clan routes")
    void publicRoutesArePermitted() throws Exception {
        // 200, not a redirect to login. The point of this test is that anonymous
        // callers are not bounced to the login page, whatever the route returns.
        org.mockito.BDDMockito.given(clanService.searchPublicClanCards(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
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
    @DisplayName("OpenAPI documentation routes are public")
    void openApiRoutesArePermitted() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title")
                        .value("Clan Management REST API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/clans']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/clans'].get.summary")
                        .value("List public clans"))
                .andExpect(jsonPath("$.paths['/api/clans'].get.security")
                        .doesNotExist())
                .andExpect(jsonPath("$.paths['/api/clans'].post.security[0].bearerAuth")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/clans'].post.responses['201']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/clans/{id}'].patch.responses['403']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/invitations'].get.security[0].bearerAuth")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/clans/{id}/invite'].post.responses['201']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/reports'].post.security[0].bearerAuth")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/reports'].post.responses['409']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/admin/clans'].get.security[0].bearerAuth")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/admin/clans'].get.responses['403']")
                        .exists());

        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("Swagger UI assets load under the content security policy")
    void swaggerUiLoadsUnderContentSecurityPolicy() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("script-src 'self'"),
                                org.hamcrest.Matchers.containsString("style-src 'self'"),
                                org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("unsafe-inline")))))
                .andExpect(content().string(
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("src=\"./swagger-ui-bundle.js\""),
                                org.hamcrest.Matchers.containsString("src=\"./swagger-initializer.js\""))));

        mockMvc.perform(get("/swagger-ui/swagger-ui-bundle.js"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/swagger-ui/swagger-ui.css"))
                .andExpect(status().isOk());
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
