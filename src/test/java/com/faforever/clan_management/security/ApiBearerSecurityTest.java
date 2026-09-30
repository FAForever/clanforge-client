package com.faforever.clan_management.security;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.BDDMockito.given;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.faforever.clan_management.service.AppUserService;

@WebMvcTest(ApiBearerSecurityTest.ApiProbeController.class)
@Import({
        SecurityConfig.class,
        ApiBearerSecurityTest.ApiProbeController.class
})
class ApiBearerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private AppUserService appUserService;

    @Test
    @DisplayName("API writes reject requests without a bearer token")
    void apiWriteWithoutTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("API writes reject an invalid bearer token")
    void apiWriteWithInvalidTokenIsRejected() throws Exception {
        given(jwtDecoder.decode("bad-token"))
                .willThrow(new BadJwtException("Invalid token"));

        mockMvc.perform(post("/api/test")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer bad-token"
                        ))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("API writes accept a valid bearer token")
    void apiWriteWithValidTokenIsAccepted() throws Exception {
        Instant now = Instant.now();

        Jwt jwt = Jwt.withTokenValue("good-token")
                .header("alg", "RS256")
                .subject("faf-user-42")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();

        given(jwtDecoder.decode("good-token"))
                .willReturn(jwt);

        mockMvc.perform(post("/api/test")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer good-token"
                        ))
                .andExpect(status().isOk())
                .andExpect(content().string("faf-user-42"));
    }

    @Test
    @WithMockUser
    @DisplayName("web requests still require CSRF")
    void webPostStillRequiresCsrf() throws Exception {
        mockMvc.perform(post("/logout").with(user("admin")))
                .andExpect(status().isForbidden());
    }

    @RestController
    public static class ApiProbeController {

        @PostMapping(
                value = "/api/test",
                produces = MediaType.TEXT_PLAIN_VALUE
        )
        public String apiWrite(Authentication authentication) {
            return authentication.getName();
        }
    }
}