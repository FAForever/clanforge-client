package com.faforever.clan_management;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
            .requestMatchers("/", "/error", "/clans", "/clans/**").permitAll()
            .anyRequest().authenticated()
        );
        // this is a temporary form login for testing the access-control flow only
        // I will replace with HYDRA once that's set up
        http.formLogin(form -> form.permitAll());
        // runs before Spring Security's own login-processing filter so it can
        // block a request before the actual login attempt happens
        http.addFilterBefore(new LoginRateLimitFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    // Testing gives formLogin a known user/password to log in with, i will remove once real OIDC (ORY Hydra) login is wired up.
    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails testUser = User.withDefaultPasswordEncoder()
            .username("admin")
            .password("admin")
            .roles("USER")
            .build();
        return new InMemoryUserDetailsManager(testUser);
    }
}