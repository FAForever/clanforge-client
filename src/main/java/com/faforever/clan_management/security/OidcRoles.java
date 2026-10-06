//SPDX-License-Identifier: MIT
package com.faforever.clan_management.security;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderInitializationException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.util.StringUtils;

// A browser sign-in carries the same faforever roles an api token does. Without this an
// administrator signing in on the website holds only the scope authorities and can never
// reach /admin. The roles come from ext.roles, the claim TokenRoles reads: in the id token
// when the provider puts them there, and in the access token when it is a JWT this
// application can verify with the resource server's decoder. Both tokens are checked
// before anything is read from them (the id token by the login flow, the access token by
// the decoder), so a role is only ever taken from something the provider signed.
public class OidcRoles implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OAuth2UserService<OidcUserRequest, OidcUser> delegate;
    private final JwtDecoder decoder; // null when no resource server is configured, then only the id token is read

    public OidcRoles(OAuth2UserService<OidcUserRequest, OidcUser> delegate, JwtDecoder decoder) {
        this.delegate = delegate;
        this.decoder = decoder;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) {
        OidcUser user = delegate.loadUser(request);
        Set<GrantedAuthority> all = new LinkedHashSet<>(user.getAuthorities());
        all.addAll(TokenRoles.roles(user.getClaims().get("ext")));
        if (decoder != null) {
            try {
                all.addAll(TokenRoles.roles(decoder.decode(request.getAccessToken().getTokenValue()).getClaim("ext")));
            } catch (JwtException | JwtDecoderInitializationException e) {
                // an opaque access token, or one this application cannot verify, adds no roles: the sign-in still works, without admin
            }
        }
        // keep the name the default service would have chosen, so auth.getName() is still the subject AppUserService looks up
        String nameKey = request.getClientRegistration().getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();
        return StringUtils.hasText(nameKey)
                ? new DefaultOidcUser(all, user.getIdToken(), user.getUserInfo(), nameKey)
                : new DefaultOidcUser(all, user.getIdToken(), user.getUserInfo());
    }
}
