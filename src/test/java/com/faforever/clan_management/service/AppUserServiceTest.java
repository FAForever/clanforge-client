//SPDX-License-Identifier: MIT
package com.faforever.clan_management.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;

import com.faforever.clan_management.repository.AppUserRepository;
import com.faforever.clan_management.repository.ClanInvitationRepository;
import com.faforever.clan_management.repository.ClanMembershipRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

//who counts as signed in, before any row is looked up or made
@ExtendWith(MockitoExtension.class)
class AppUserServiceTest {

    @Mock
    private AppUserRepository users;
    @Mock
    private ClanMembershipRepository memberships;
    @Mock
    private ClanInvitationRepository invitations;
    @Mock
    private NotificationService notifications;
    @InjectMocks
    private AppUserService service;

    @Test
    @DisplayName("a sign-in that names nobody gets no app_user row, so it can never share one with another such sign-in")
    void blankSubjectIsNobody() {
        assertThat(service.find(new TestingAuthenticationToken("", "n/a", "ROLE_USER"))).isEmpty();
        assertThat(service.find(new TestingAuthenticationToken("   ","n/a", "ROLE_USER"))).isEmpty();
        assertThatThrownBy(() -> service.require(new TestingAuthenticationToken("","n/a","ROLE_USER"))).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(users);
    }
}
