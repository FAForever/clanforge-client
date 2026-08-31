package com.faforever.clan_management;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
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
          + "style-src 'self'; "
          + "img-src 'self' data:; "
          + "font-src 'self'; "
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
            .requestMatchers("/", "/error", "/clans", "/clans/**").permitAll()
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

        // Temporary form login for testing the access-control flow. Replaced by
        // OIDC (ORY Hydra) once that is wired up.
        http.formLogin(form -> form.permitAll());

        // Registered inside Spring Security's own chain rather than as a @Component,
        // so it runs before login processing. A plain @Component filter is registered
        // by Spring Boot as a generic servlet filter that runs AFTER the whole security
        // chain, which is too late to block a login attempt.
        http.addFilterBefore(new LoginRateLimitFilter(), UsernamePasswordAuthenticationFilter.class);

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
        return new InMemoryUserDetailsManager(testUser);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
