//SPDX-License-Identifier: MIT
package com.faforever.clan_management.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.faforever.clan_management.service.AdminAccess;
import com.faforever.clan_management.service.AdminService;
import com.faforever.clan_management.service.ClanAccessRefused;
import com.faforever.clan_management.service.ReportService;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

//the controller on its own, without the filter chain in front of it, so the second check is seen to be there
@ExtendWith(MockitoExtension.class)
class AdminApiControllerAccessTest {

    @Mock
    private AdminAccess access;
    @Mock
    private AdminService admins;
    @Mock
    private ReportService reports;
    @InjectMocks
    private AdminApiController controller;

    @Test
    @DisplayName("each admin listing asks AdminAccess first and stops there when it refuses")
    void listingsAskAccessFirst() {
        Authentication someone = new TestingAuthenticationToken("7", "n/a", "ROLE_USER");
        given(access.require(someone)).willThrow(new ClanAccessRefused("Only an administrator can do that."));
        assertThatThrownBy(() -> controller.clans(0, "", "all",someone)).isInstanceOf(ClanAccessRefused.class);
        assertThatThrownBy(() -> controller.reports(0, "OPEN", someone)).isInstanceOf(ClanAccessRefused.class);
        //System.out.println("refused twice");
        verifyNoInteractions(admins);
        verifyNoInteractions(reports);
    }
}
