//SPDX-License-Identifier: MIT
package com.faforever.clan_management.security;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderInitializationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

// a browser sign-in takes the provider's ext.roles from the id token or from an access token the
// decoder verifies, and nothing from an opaque token, an unreachable issuer or a claim of another shape
class OidcRolesTest {

    private static final Instant now = Instant.now();

    // what the default OidcUserService hands back, before any role is added
    private static final OAuth2UserService<OidcUserRequest, OidcUser> plain =
            r -> new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER"), new SimpleGrantedAuthority("SCOPE_openid")), r.getIdToken());

    private static ClientRegistration registration(String nameKey) {
        ClientRegistration.Builder b = ClientRegistration.withRegistrationId("hydra")
                .clientId("clan-management").clientSecret("secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://idp.example/oauth2/auth")
                .tokenUri("https://idp.example/oauth2/token")
                .jwkSetUri("https://idp.example/.well-known/jwks.json")
                .scope("openid");
        if (nameKey != null) b.userNameAttributeName(nameKey);
        return b.build();
    }

    private static OidcUserRequest request(ClientRegistration registration, Map<String, Object> idClaims, String accessToken) {
        OidcIdToken id = new OidcIdToken("id-token", now, now.plusSeconds(300), idClaims);
        OAuth2AccessToken access = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, accessToken, now, now.plusSeconds(300));
        return new OidcUserRequest(registration, access, id);
    }

    private static List<String> authorities(OidcUser user) {
        return user.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    @DisplayName("roles under ext.roles in the id token become ROLE_ authorities next to the ones the sign-in already had")
    void rolesFromIdToken() {
        OidcUser user = new OidcRoles(plain, null).loadUser(request(registration(null),
                Map.of("sub", "user-1", "ext", Map.of("roles", List.of("ADMIN_CLAN", " MODERATOR "))), "opaque"));
        assertThat(authorities(user)).contains("OIDC_USER", "SCOPE_openid", "ROLE_ADMIN_CLAN", "ROLE_MODERATOR");
        assertThat(user.getName()).isEqualTo("user-1");
    }

    @Test
    @DisplayName("roles in an access token the decoder verifies are read the same way TokenRoles reads an api token")
    void rolesFromVerifiedAccessToken() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        given(decoder.decode("access-jwt")).willReturn(Jwt.withTokenValue("access-jwt").header("alg", "RS256").subject("user-1")
                .claim("ext", Map.of("roles", List.of("ADMIN_CLAN"))).issuedAt(now).expiresAt(now.plusSeconds(300)).build());
        OidcUser user = new OidcRoles(plain, decoder).loadUser(request(registration(null), Map.of("sub", "user-1"), "access-jwt"));
        assertThat(authorities(user)).contains("ROLE_ADMIN_CLAN");
        assertThat(user.getName()).isEqualTo("user-1");
    }

    @Test
    @DisplayName("an opaque access token, or an issuer the decoder cannot reach, adds no role and still signs the person in")
    void unverifiableAccessTokenAddsNothing() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        given(decoder.decode("opaque")).willThrow(new BadJwtException("Invalid JWT serialization"));
        given(decoder.decode("unreachable")).willThrow(new JwtDecoderInitializationException("issuer down", new IllegalStateException()));
        OidcRoles roles = new OidcRoles(plain, decoder);
        assertThat(authorities(roles.loadUser(request(registration(null), Map.of("sub", "user-1"), "opaque")))).containsExactly("OIDC_USER", "SCOPE_openid");
        assertThat(authorities(roles.loadUser(request(registration(null), Map.of("sub", "user-1"), "unreachable")))).containsExactly("OIDC_USER", "SCOPE_openid");
    }

    @Test
    @DisplayName("an ext claim of another shape gives no role")
    void otherShapesGiveNothing() {
        OidcUser user = new OidcRoles(plain, null).loadUser(request(registration(null), Map.of("sub", "user-1", "ext", "not a map"), "opaque"));
        assertThat(authorities(user)).containsExactly("OIDC_USER", "SCOPE_openid");
    }

    @Test
    @DisplayName("the name the provider registration asks for is kept, so the app_user lookup sees the same principal name")
    void nameKeyIsKept() {
        OidcUser user = new OidcRoles(plain, null).loadUser(request(registration("preferred_username"),
                Map.of("sub", "user-1", "preferred_username", "kim"), "opaque"));
        assertThat(user.getName()).isEqualTo("kim");
    }
}
