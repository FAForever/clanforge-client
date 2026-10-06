//SPDX-License-Identifier: MIT
package com.faforever.clan_management.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

//a bearer token becomes the person its sub names with the roles TokenRoles reads, and a token naming nobody is turned away
public class ApiTokenConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtAuthenticationConverter delegate = new JwtAuthenticationConverter();

    public ApiTokenConverter() {
        delegate.setJwtGrantedAuthoritiesConverter(new TokenRoles());
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String sub = jwt.getSubject();
        //System.out.println("sub " + sub);
        if (sub == null || sub.isBlank()) throw new InvalidBearerTokenException("The token names no subject."); // every token without one would otherwise be the same shared user
        return delegate.convert(jwt);
    }
}
