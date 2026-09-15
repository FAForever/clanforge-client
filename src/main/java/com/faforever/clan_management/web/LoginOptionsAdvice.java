package com.faforever.clan_management.web;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Tells the login page which sign-in to offer.
 *
 * Spring Boot only creates a ClientRegistrationRepository when an OIDC provider
 * is configured, so its presence is the same signal SecurityConfig uses to
 * decide between OIDC and the development form login. Reading it here keeps the
 * page and the filter chain from disagreeing about which one is active.
 */
@ControllerAdvice
public class LoginOptionsAdvice {

    private final boolean oidcEnabled;

    public LoginOptionsAdvice(ObjectProvider<ClientRegistrationRepository> clientRegistrations) {
        this.oidcEnabled = clientRegistrations.getIfAvailable() != null;
    }

    @ModelAttribute("oidcEnabled")
    public boolean oidcEnabled() {
        return oidcEnabled;
    }
}
