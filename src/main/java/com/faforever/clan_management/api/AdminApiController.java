//SPDX-License-Identifier: MIT
package com.faforever.clan_management.api;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.faforever.clan_management.service.AdminAccess;
import com.faforever.clan_management.service.AdminService;
import com.faforever.clan_management.service.ReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

// the administration listings, the bearer chain already turns away anyone without the admin role and AdminAccess asks again
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Administration", description = "Moderator-only clan and report listings.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AdminApiController {

    static final int pageSize = 20;
    static final int maxPage = 10_000;

    private final AdminAccess access;
    private final AdminService admins;
    private final ReportService reports;

    public AdminApiController(AdminAccess access,AdminService admins, ReportService reports) {
        this.access = access;
        this.admins = admins;
        this.reports = reports;
    }

    @GetMapping("/clans")
    @Operation(summary = "List clans for moderation")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Clan moderation page returned"),
            @ApiResponse(responseCode = "401", description = "Bearer token is missing or invalid"),
            @ApiResponse(responseCode = "403", description = "Administrator role is required")
    })
    public AdminClanPageResponse clans(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "all") String filter,Authentication auth) {
        access.require(auth);
        return AdminClanPageResponse.from(admins.clans(q,filter, PageRequest.of(clamp(page), pageSize))); //unsorted, the order is part of the admin queries
    }

    @GetMapping("/reports")
    @Operation(summary = "List reports for moderation")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Report moderation page returned"),
            @ApiResponse(responseCode = "401", description = "Bearer token is missing or invalid"),
            @ApiResponse(responseCode = "403", description = "Administrator role is required")
    })
    public ReportPageResponse reports(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "OPEN") String state, Authentication auth) {
        access.require(auth);
        //System.out.println("admin reports " + state + " " + page);
        return ReportPageResponse.from(reports.byState(state, PageRequest.of(clamp(page), pageSize)));
    }

    //a crafted ?page cannot ask the database for an enormous offset
    private int clamp(int page) {
        return Math.min(Math.max(page,0), maxPage);
    }
}
