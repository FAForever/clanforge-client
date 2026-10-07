//SPDX-License-Identifier: MIT
package com.faforever.clan_management.security;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.faforever.clan_management.service.AppUserService;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// who the bearer chain lets under /api/admin, tried with a probe route so no admin controller has to exist
@WebMvcTest(ApiAdminSecurityTest.AdminProbe.class)
@Import({SecurityConfig.class, ApiAdminSecurityTest.AdminProbe.class})
class ApiAdminSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;
    @MockitoBean
    private AppUserService appUserService;

    //a token the mocked decoder accepts, carrying whatever sits under ext
    static String bearer(JwtDecoder decoder, String sub, Object ext) {
        Instant now = Instant.now();
        Jwt.Builder b = Jwt.withTokenValue("token-" + sub).header("alg","RS256").subject(sub).issuedAt(now).expiresAt(now.plusSeconds(300));
        if (ext != null) b.claim("ext", ext);
        given(decoder.decode("token-" + sub)).willReturn(b.build());
        return "Bearer token-" + sub;
    }

    @Test
    @DisplayName("an admin route answers 401 with no token")
    void closedWithoutToken() throws Exception {
        mockMvc.perform(get("/api/admin/probe")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a valid token without the admin role is 403 on an admin route and still fine everywhere else")
    void signedInIsNotEnough() throws Exception {
        String plain = bearer(jwtDecoder, "7", null);
        String mapAdmin = bearer(jwtDecoder, "8",Map.of("roles", List.of("ADMIN_MAP","READ_AUDIT_LOG")));
        String oddExt = bearer(jwtDecoder, "9", "not-a-map");
        mockMvc.perform(get("/api/admin/probe").header(HttpHeaders.AUTHORIZATION,plain)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/probe").header(HttpHeaders.AUTHORIZATION, mapAdmin)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/probe").header(HttpHeaders.AUTHORIZATION, oddExt)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/deeper/probe").header(HttpHeaders.AUTHORIZATION, plain)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/probe").header(HttpHeaders.AUTHORIZATION, plain)).andExpect(status().isOk()).andExpect(content().string("7"));
    }

    @Test
    @DisplayName("a token carrying ADMIN_CLAN under ext.roles reaches the admin route as its subject")
    void adminRoleIsLetThrough() throws Exception {
        String admin = bearer(jwtDecoder, "42", Map.of("roles", List.of("READ_AUDIT_LOG","ADMIN_CLAN"),"username","Brutus"));
        mockMvc.perform(get("/api/admin/probe").header(HttpHeaders.AUTHORIZATION, admin)).andExpect(status().isOk()).andExpect(content().string("42"));
        mockMvc.perform(get("/api/admin/deeper/probe").header(HttpHeaders.AUTHORIZATION, admin)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("a token that names no subject is turned away with 401 on every api route, admin role or not")
    void tokenWithoutSubjectIsRefused() throws Exception {
        Instant now = Instant.now();
        given(jwtDecoder.decode("token-nosub")).willReturn(Jwt.withTokenValue("token-nosub").header("alg", "RS256").claim("ext", Map.of("roles", List.of("ADMIN_CLAN"))).issuedAt(now).expiresAt(now.plusSeconds(300)).build());
        mockMvc.perform(get("/api/admin/probe").header(HttpHeaders.AUTHORIZATION,"Bearer token-nosub")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/probe").header(HttpHeaders.AUTHORIZATION, "Bearer token-nosub")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a request body over the cap is 413 before anything reads it, and one under it goes through")
    void bodyOverTheCapIsRefused() throws Exception {
        String plain = bearer(jwtDecoder, "7", null);
        mockMvc.perform(post("/api/probe").header(HttpHeaders.AUTHORIZATION,plain).contentType("application/json").content(new byte[65537]))
                .andExpect(status().is(413))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("65536 bytes at most")));
        mockMvc.perform(post("/api/probe").header(HttpHeaders.AUTHORIZATION, plain).contentType("application/json").content(new byte[65536])).andExpect(status().isOk());
        mockMvc.perform(post("/api/probe").contentType("application/json").content(new byte[65537])).andExpect(status().is(413)); //no token needed to be turned away for size
    }

    @RestController
    public static class AdminProbe {

        @GetMapping({"/api/admin/probe", "/api/admin/deeper/probe"})
        public String admin(Authentication authentication) {
            return authentication.getName();
        }

        @PostMapping("/api/probe")
        public String anyone(Authentication authentication) {
            return authentication.getName();
        }
    }
}
