//SPDX-License-Identifier: MIT
package com.faforever.clan_management.service;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.faforever.clan_management.entity.AppUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

//the one question every admin route asks first
@ExtendWith(MockitoExtension.class)
class AdminAccessTest {

    @Mock
    private AppUserService users;

    private Authentication holding(String... authorities) {
        return new TestingAuthenticationToken("42", "n/a", authorities);
    }

    @Test
    @DisplayName("the configured role lets someone through and gives back their app_user row")
    void roleHolderIsLetThrough() {
        AdminAccess access = new AdminAccess(" ADMIN_CLAN ", users);
        Authentication admin = holding("SCOPE_public_profile", "ROLE_ADMIN_CLAN");
        AppUser row = Fixtures.user(1L, "admin");
        given(users.require(admin)).willReturn(row);
        assertThat(access.allows(admin)).isTrue();
        assertThat(access.require(admin)).isSameAs(row);
    }

    @Test
    @DisplayName("anyone without the role, signed in or not, is refused before a user row is looked up")
    void othersAreRefused() {
        AdminAccess access = new AdminAccess("ADMIN_CLAN", users);
        Authentication signedOut = holding("ROLE_ADMIN_CLAN");
        signedOut.setAuthenticated(false);
        assertThat(access.allows(null)).isFalse();
        assertThat(access.allows(signedOut)).isFalse();
        assertThat(access.allows(holding("ROLE_USER", "ROLE_ADMIN_MAP", "SCOPE_ADMIN_CLAN"))).isFalse();
        assertThatThrownBy(() -> access.require(holding("ROLE_USER"))).isInstanceOf(ClanAccessRefused.class).hasMessageContaining("administrator");
        verifyNoInteractions(users);
    }

    @Test
    @DisplayName("another deployment names its own role by property")
    void roleIsAProperty() {
        AdminAccess access = new AdminAccess("CLAN_BOSS",users);
        //System.out.println("boss " + access.allows(holding("ROLE_CLAN_BOSS")));
        assertThat(access.allows(holding("ROLE_CLAN_BOSS"))).isTrue();
        assertThat(access.allows(holding("ROLE_ADMIN_CLAN"))).isFalse();
    }
}
