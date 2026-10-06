package com.faforever.clan_management.web;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Tells the login page which sign-in options to offer.
 *
 * Spring Boot only creates a ClientRegistrationRepository when an OIDC provider
 * is configured, so its presence is the same signal SecurityConfig uses to offer
 * OIDC. The in-memory username/password accounts are gated behind the "dev"
 * profile (see SecurityConfig.devUserDetailsService), so the development form can
 * only authenticate when that profile is active. Reading both here keeps the page
 * from offering a sign-in the filter chain cannot honour — in particular a
 * deployed build (OIDC only, no dev profile) must not render a dead password form.
 */
@ControllerAdvice
public class LoginOptionsAdvice {

    private final boolean oidcEnabled;
    private final boolean devLoginEnabled;

    public LoginOptionsAdvice(ObjectProvider<ClientRegistrationRepository> clientRegistrations, Environment environment) {
        this.oidcEnabled = clientRegistrations.getIfAvailable() != null;
        this.devLoginEnabled = environment.acceptsProfiles(Profiles.of("dev"));
    }

    @ModelAttribute("oidcEnabled")
    public boolean oidcEnabled() {
        return oidcEnabled;
    }

    @ModelAttribute("devLoginEnabled")
    public boolean devLoginEnabled() {
        return devLoginEnabled;
    }
}
