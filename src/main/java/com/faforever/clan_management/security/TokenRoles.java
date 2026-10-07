//SPDX-License-Identifier: MIT
package com.faforever.clan_management.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

// https://github.com/FAForever/faf-java-api/blob/develop/src/main/java/com/faforever/api/security/FafAuthenticationConverter.java
// the roles faforever puts in a token under ext.roles, each one a ROLE_ authority, next to the usual scope authorities
public class TokenRoles implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        List<GrantedAuthority> out = new ArrayList<>(scopes.convert(jwt));
        out.addAll(roles(jwt.getClaim("ext"))); //a token from another provider has no ext at all, or one of another shape, and then there are simply no roles
        return out;
    }

    // the ROLE_ authorities an ext claim carries, read here for an api token and by OidcRoles for a browser sign-in
    static List<GrantedAuthority> roles(Object ext) {
        List<GrantedAuthority> out = new ArrayList<>();
        if (ext instanceof Map<?,?> map && map.get("roles") instanceof Collection<?> roles) {
            for (Object r : roles) {
                String name = r instanceof String s ? s.trim() : ""; //trimmed first, so a name of nothing but control characters is no role at all
                if (!name.isEmpty()) out.add(new SimpleGrantedAuthority("ROLE_" + name));
            }
        }
        return out;
    }
}
