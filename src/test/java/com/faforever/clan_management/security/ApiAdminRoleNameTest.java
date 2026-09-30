//SPDX-License-Identifier: MIT
package com.faforever.clan_management.security;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.faforever.clan_management.service.AppUserService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

//the same probe with the admin role renamed, the way a deployment outside faforever would set it
@WebMvcTest(ApiAdminSecurityTest.AdminProbe.class)
@Import({SecurityConfig.class, ApiAdminSecurityTest.AdminProbe.class})
@TestPropertySource(properties = "clan.admin.role=CLAN_BOSS")
class ApiAdminRoleNameTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;
    @MockitoBean
    private AppUserService appUserService;

    @Test
    @DisplayName("with the role renamed by property, the new name gets in and ADMIN_CLAN no longer does")
    void roleNameComesFromTheProperty() throws Exception {
        String boss = ApiAdminSecurityTest.bearer(jwtDecoder, "1", Map.of("roles", List.of("CLAN_BOSS")));
        String fafAdmin = ApiAdminSecurityTest.bearer(jwtDecoder, "2", Map.of("roles",List.of("ADMIN_CLAN")));
        mockMvc.perform(get("/api/admin/probe").header(HttpHeaders.AUTHORIZATION, boss)).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/probe").header(HttpHeaders.AUTHORIZATION, fafAdmin)).andExpect(status().isForbidden());
        //System.out.println("boss in, faf admin out");
    }
}
