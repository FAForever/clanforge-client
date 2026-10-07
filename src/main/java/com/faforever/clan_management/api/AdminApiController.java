//SPDX-License-Identifier: MIT
package com.faforever.clan_management.api;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.service.AdminAccess;
import com.faforever.clan_management.service.AdminService;
import com.faforever.clan_management.service.ReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

// the administration listings and the moderation actions on a clan; the bearer chain already turns away anyone without the admin role and AdminAccess asks again
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
        return ReportPageResponse.from(reports.byState(state, PageRequest.of(clamp(page), pageSize)));
    }

    // The actions. Each records a moderation row and answers with it. AdminService
    // locks the clan, so two administrators acting at once are serialised there, and
    // a refused action (unknown clan, already suspended, invalid name/tag, a report
    // about another clan) surfaces as the service's 400 through ApiErrorHandler.
    @PostMapping("/clans/{id}/suspend")
    @Operation(summary = "Suspend a clan")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Clan suspended, moderation record returned"),
            @ApiResponse(responseCode = "400", description = "Action refused (unknown clan, already suspended, or a report about another clan)"),
            @ApiResponse(responseCode = "401", description = "Bearer token is missing or invalid"),
            @ApiResponse(responseCode = "403", description = "Administrator role is required")
    })
    public ModerationActionResponse suspend(@PathVariable Long id, @RequestBody AdminActionRequest body, Authentication auth) {
        AppUser admin = access.require(auth);
        return ModerationActionResponse.from(admins.suspend(admin, id, body.reason(), body.reportId()));
    }

    @PostMapping("/clans/{id}/unsuspend")
    @Operation(summary = "Lift a clan's suspension")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Suspension lifted, moderation record returned"),
            @ApiResponse(responseCode = "400", description = "Action refused (unknown clan, or the clan is not suspended)"),
            @ApiResponse(responseCode = "401", description = "Bearer token is missing or invalid"),
            @ApiResponse(responseCode = "403", description = "Administrator role is required")
    })
    public ModerationActionResponse unsuspend(@PathVariable Long id, @RequestBody AdminActionRequest body, Authentication auth) {
        AppUser admin = access.require(auth);
        return ModerationActionResponse.from(admins.unsuspend(admin, id, body.reason()));
    }

    @PostMapping("/clans/{id}/rename")
    @Operation(summary = "Rename a clan")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Clan renamed, moderation record returned"),
            @ApiResponse(responseCode = "400", description = "Action refused (unknown clan, the name breaks a rule, or a report about another clan)"),
            @ApiResponse(responseCode = "401", description = "Bearer token is missing or invalid"),
            @ApiResponse(responseCode = "403", description = "Administrator role is required")
    })
    public ModerationActionResponse rename(@PathVariable Long id, @RequestBody AdminActionRequest body, Authentication auth) {
        AppUser admin = access.require(auth);
        return ModerationActionResponse.from(admins.rename(admin, id, body.name(), body.reason(), body.reportId()));
    }

    @PostMapping("/clans/{id}/retag")
    @Operation(summary = "Change a clan's tag")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Clan tag changed, moderation record returned"),
            @ApiResponse(responseCode = "400", description = "Action refused (unknown clan, the tag breaks a rule, or a report about another clan)"),
            @ApiResponse(responseCode = "401", description = "Bearer token is missing or invalid"),
            @ApiResponse(responseCode = "403", description = "Administrator role is required")
    })
    public ModerationActionResponse retag(@PathVariable Long id, @RequestBody AdminActionRequest body, Authentication auth) {
        AppUser admin = access.require(auth);
        return ModerationActionResponse.from(admins.retag(admin, id, body.tag(), body.reason(), body.reportId()));
    }

    @PostMapping("/clans/{id}/delete")
    @Operation(summary = "Delete a clan (soft delete)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Clan deleted, moderation record returned"),
            @ApiResponse(responseCode = "400", description = "Action refused (unknown clan, or a report about another clan)"),
            @ApiResponse(responseCode = "401", description = "Bearer token is missing or invalid"),
            @ApiResponse(responseCode = "403", description = "Administrator role is required")
    })
    public ModerationActionResponse delete(@PathVariable Long id, @RequestBody AdminActionRequest body, Authentication auth) {
        AppUser admin = access.require(auth);
        return ModerationActionResponse.from(admins.delete(admin, id, body.reason(), body.reportId()));
    }

    // The moderation history of one clan, newest first, so an admin can review what has been done before acting.
    @GetMapping("/clans/{id}/history")
    @Operation(summary = "List a clan's moderation history, newest first")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Moderation history returned"),
            @ApiResponse(responseCode = "401", description = "Bearer token is missing or invalid"),
            @ApiResponse(responseCode = "403", description = "Administrator role is required")
    })
    public List<ModerationActionResponse> history(@PathVariable Long id, Authentication auth) {
        access.require(auth);
        return admins.history(id).stream().map(ModerationActionResponse::from).toList();
    }

    //a crafted ?page cannot ask the database for an enormous offset
    private int clamp(int page) {
        return Math.min(Math.max(page,0), maxPage);
    }
}
