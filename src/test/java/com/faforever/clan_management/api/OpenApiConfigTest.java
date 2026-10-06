package com.faforever.clan_management.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;

class OpenApiConfigTest {

    @Test
    void documentsApiNameAndBearerAuthentication() {
        OpenAPI openApi = new OpenApiConfig().clanManagementOpenApi();

        assertThat(openApi.getInfo().getTitle())
                .isEqualTo("Clan Management REST API");
        assertThat(openApi.getComponents().getSecuritySchemes())
                .containsKey(OpenApiConfig.BEARER_AUTH);

        SecurityScheme bearer = openApi.getComponents()
                .getSecuritySchemes()
                .get(OpenApiConfig.BEARER_AUTH);
        assertThat(bearer.getType()).isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(bearer.getScheme()).isEqualTo("bearer");
        assertThat(bearer.getBearerFormat()).isEqualTo("JWT");
    }
}
