//SPDX-License-Identifier: MIT
package com.faforever.clan_management.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.faforever.clan_management.entity.ClanReport;
import com.faforever.clan_management.service.AppUserService;
import com.faforever.clan_management.service.ReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Reports", description = "Report clans for moderator review.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class ReportApiController {

    private final AppUserService users;
    private final ReportService reports;

    public ReportApiController(AppUserService users,ReportService reports) {
        this.users = users;
        this.reports = reports;
    }

    //anyone with a token can report a clan, what they may report and how often is ReportServices call
    @PostMapping("/api/reports")
    @Operation(summary = "Report a clan")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Report created"),
            @ApiResponse(responseCode = "400", description = "Report details are invalid"),
            @ApiResponse(responseCode = "401", description = "Bearer token is missing or invalid"),
            @ApiResponse(responseCode = "404", description = "Clan was not found"),
            @ApiResponse(responseCode = "409", description = "An open report already exists")
    })
    public ResponseEntity<ReportResponse> file(@RequestBody ReportRequest body, Authentication auth) {
        ClanReport report = reports.file(users.require(auth),body.clanId(), body.category(),body.reason());
        return ResponseEntity.status(HttpStatus.CREATED).body(ReportResponse.from(report));
    }
}
