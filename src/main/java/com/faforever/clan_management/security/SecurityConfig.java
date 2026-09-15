package com.faforever.clan_management.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

@Configuration
public class SecurityConfig {

    // Content-Security-Policy. Deliberately strict: no inline script or style,
    // nothing loaded from other origins. Set before the front end is wired up,
    // because tightening a CSP after inline scripts exist is much harder than
    // building against it from the start. Any relaxation here should be a
    // conscious decision, not a reaction to something being broken.
    private static final String CSP =
            "default-src 'self'; "
          + "script-src 'self'; "
          // the clan pages use Google Fonts: the stylesheet comes from
          // fonts.googleapis.com and the font files from fonts.gstatic.com.
          // Named explicitly rather than opening style-src up to 'unsafe-inline'.
          + "style-src 'self' https://fonts.googleapis.com; "
          + "img-src 'self' data:; "
          + "font-src 'self' https://fonts.gstatic.com; "
          + "connect-src 'self'; "
          + "object-src 'none'; "
          + "frame-ancestors 'none'; "
          + "base-uri 'self'; "
          + "form-action 'self'";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
            // /error must stay permitted. Spring forwards unhandled 404s to it as an
            // ERROR dispatch, and since Spring Security 6 the filter chain applies to
            // ERROR dispatches too - so denying it turns every anonymous 404 into an
            // access-denied response and hides the real status.
            .requestMatchers("/", "/login", "/error").permitAll()
            // Reading clans is public. Joining one is a POST and needs a signed-in user.
            .requestMatchers(HttpMethod.GET, "/clans", "/clans/**").permitAll()
            // static assets must be reachable anonymously or the public clan
            // pages render without their stylesheet
            .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
            .anyRequest().authenticated()
        );

        http.headers(headers -> headers
            .contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
            .referrerPolicy(ref -> ref.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
            // only takes effect over HTTPS; harmless over plain HTTP in local dev
            .httpStrictTransportSecurity(hsts -> hsts
                .includeSubDomains(true)
                .maxAgeInSeconds(31536000))
            .permissionsPolicyHeader(pp -> pp.policy("geolocation=(), camera=(), microphone=()"))
            // X-Frame-Options DENY and X-Content-Type-Options nosniff are on by default
        );

        // Built here rather than as a @Bean on purpose. A Filter exposed as a bean
        // is also picked up by Spring Boot's servlet filter auto-registration and
        // ends up running twice, once inside this chain and once outside it.
        LoginRateLimitFilter rateLimitFilter = new LoginRateLimitFilter();

        // Send people back to whatever they were trying to reach. Only when there
        // is no such request do they land on /clans. The previous
        // defaultSuccessUrl("/clans", true) discarded the saved request, so
        // deep-linking to /settings while signed out and then signing in dropped
        // you on /clans instead.
        SavedRequestAwareAuthenticationSuccessHandler successHandler =
                new SavedRequestAwareAuthenticationSuccessHandler();
        successHandler.setDefaultTargetUrl("/clans");

        // Temporary form login for testing the access-control flow. Replaced by
        // OIDC (ORY Hydra) once that is wired up.
        http.formLogin(form -> form
            .loginPage("/login")
            .successHandler((request, response, authentication) -> {
                // A correct password is not a guessing attempt, so it must not
                // spend the address's budget. Without this the sixth sign-in in
                // a minute from one address is refused, which is a lockout
                // waiting to happen during a demo or behind a shared NAT.
                rateLimitFilter.forget(request);
                successHandler.onAuthenticationSuccess(request, response, authentication);
            })
            .failureUrl("/login?error")
            .permitAll());

        http.logout(logout -> logout
            .logoutSuccessUrl("/login?logout")
            .permitAll());

        // Registered inside Spring Security's own chain rather than as a @Component,
        // so it runs before login processing. A plain @Component filter is registered
        // by Spring Boot as a generic servlet filter that runs AFTER the whole security
        // chain, which is too late to block a login attempt.
        http.addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // Development-only login. Gated behind the "dev" profile so it cannot be
    // active in a deployed build: without this the hardcoded credentials below
    // would be a real account in the shipped application.
    // Run locally with: ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
    @Bean
    @Profile("dev")
    public UserDetailsService devUserDetailsService(PasswordEncoder passwordEncoder) {
        UserDetails testUser = User.withUsername("admin")
            .password(passwordEncoder.encode("admin"))
            .roles("USER")
            .build();
        // Two more accounts so a demo can show both sides of an invitation, a
        // join, a removal or a transfer from two browser windows.
        UserDetails player = User.withUsername("player")
            .password(passwordEncoder.encode("player"))
            .roles("USER")
            .build();
        UserDetails guest = User.withUsername("guest")
            .password(passwordEncoder.encode("guest"))
            .roles("USER")
            .build();
        return new InMemoryUserDetailsManager(testUser, player, guest);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
