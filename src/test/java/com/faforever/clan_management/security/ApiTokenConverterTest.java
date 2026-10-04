//SPDX-License-Identifier: MIT
package com.faforever.clan_management.security;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiTokenConverterTest {

    private final ApiTokenConverter converter = new ApiTokenConverter();

    private Jwt.Builder token() {
        Instant now = Instant.now();
        return Jwt.withTokenValue("t").header("alg", "RS256").issuedAt(now).expiresAt(now.plusSeconds(300)).claim("ext", Map.of("roles", List.of("ADMIN_CLAN")));
    }

    @Test
    @DisplayName("a token becomes its subject with the roles under ext.roles")
    void subjectAndRoles() {
        AbstractAuthenticationToken auth = converter.convert(token().subject("396624").build());
        assertThat(auth.getName()).isEqualTo("396624");
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).contains("ROLE_ADMIN_CLAN");
    }

    @Test
    @DisplayName("a token with no subject or a blank one is refused as an invalid token")
    void noSubjectIsRefused() {
        assertThatThrownBy(() -> converter.convert(token().build())).isInstanceOf(InvalidBearerTokenException.class);
        assertThatThrownBy(() -> converter.convert(token().subject(" ").build())).isInstanceOf(InvalidBearerTokenException.class).hasMessageContaining("no subject");
    }
}
