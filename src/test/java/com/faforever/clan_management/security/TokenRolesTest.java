//SPDX-License-Identifier: MIT
package com.faforever.clan_management.security;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;

class TokenRolesTest {

    private final TokenRoles roles = new TokenRoles();

    private Jwt.Builder token() {
        Instant now = Instant.now();
        return Jwt.withTokenValue("t").header("alg", "RS256").subject("42").issuedAt(now).expiresAt(now.plusSeconds(300));
    }

    private List<String> names(Jwt jwt) {
        return roles.convert(jwt).stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    @DisplayName("each role under ext.roles becomes a ROLE_ authority and the scopes stay beside them")
    void rolesAndScopes() {
        Jwt jwt = token().claim("ext", Map.of("roles", List.of("ADMIN_CLAN","READ_AUDIT_LOG"), "username","Brutus")).claim("scp",List.of("public_profile")).build();
        //System.out.println("names " + names(jwt));
        assertThat(names(jwt)).containsExactlyInAnyOrder("ROLE_ADMIN_CLAN", "ROLE_READ_AUDIT_LOG", "SCOPE_public_profile");
    }

    @Test
    @DisplayName("a token with no ext, or an ext of another shape, carries no roles and does not fail")
    void oddShapesGiveNoRoles() {
        assertThat(names(token().build())).isEmpty();
        assertThat(names(token().claim("ext", "not-a-map").build())).isEmpty();
        assertThat(names(token().claim("ext", Map.of("username", "Brutus")).build())).isEmpty();
        assertThat(names(token().claim("ext", Map.of("roles", "ADMIN_CLAN")).build())).isEmpty();
        assertThat(names(token().claim("ext", Map.of("roles", Arrays.asList("ADMIN_CLAN",7, " ", null))).build())).containsExactly("ROLE_ADMIN_CLAN");
        assertThat(names(token().claim("ext", Map.of("roles", List.of("  ADMIN_CLAN  "))).build())).containsExactly("ROLE_ADMIN_CLAN");
    }
}
