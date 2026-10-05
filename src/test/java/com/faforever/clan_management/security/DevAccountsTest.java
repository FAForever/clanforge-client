//SPDX-License-Identifier: MIT
package com.faforever.clan_management.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

// the dev profile's in-memory accounts: admin is a site administrator so the admin pages can be shown locally, player and guest are not
class DevAccountsTest {

    private static UserDetailsService accounts(String role) {
        return new SecurityConfig().devUserDetailsService(new BCryptPasswordEncoder(4), role);
    }

    @Test
    @DisplayName("only the admin account holds the admin role")
    void onlyAdminIsAnAdministrator() {
        UserDetailsService dev = accounts("ADMIN_CLAN");
        assertThat(dev.loadUserByUsername("admin").getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN_CLAN");
        assertThat(dev.loadUserByUsername("player").getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_USER");
        assertThat(dev.loadUserByUsername("guest").getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("the admin role is whatever clan.admin.role names, trimmed, so it always matches the /admin rule")
    void roleFollowsTheProperty() {
        assertThat(accounts(" MODS ").loadUserByUsername("admin").getAuthorities()).extracting(GrantedAuthority::getAuthority).contains("ROLE_MODS");
    }
}
