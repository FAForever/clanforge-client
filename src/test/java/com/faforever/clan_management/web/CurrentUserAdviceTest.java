//SPDX-License-Identifier: MIT
package com.faforever.clan_management.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;

import com.faforever.clan_management.service.AppUserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

// the top bar's Admin link follows exactly the authority /admin asks for, nothing that merely looks like it
class CurrentUserAdviceTest {

    private final CurrentUserAdvice advice = new CurrentUserAdvice(mock(AppUserService.class), " ADMIN_CLAN ");

    @Test
    @DisplayName("an administrator sees the admin link")
    void administratorIsAdmin() {
        assertThat(advice.isAdmin(new TestingAuthenticationToken("kim", "n/a", "ROLE_USER", "ROLE_ADMIN_CLAN"))).isTrue();
    }

    @Test
    @DisplayName("a player, a look-alike scope or role, a signed-out visitor and an unauthenticated token do not")
    void othersAreNot() {
        assertThat(advice.isAdmin(new TestingAuthenticationToken("sam", "n/a", "ROLE_USER"))).isFalse();
        assertThat(advice.isAdmin(new TestingAuthenticationToken("sam", "n/a", "SCOPE_ADMIN_CLAN", "ROLE_ADMIN_MAP", "ADMIN_CLAN"))).isFalse();
        assertThat(advice.isAdmin(null)).isFalse();
        TestingAuthenticationToken notYet = new TestingAuthenticationToken("kim", "n/a", "ROLE_ADMIN_CLAN");
        notYet.setAuthenticated(false);
        assertThat(advice.isAdmin(notYet)).isFalse();
    }
}
