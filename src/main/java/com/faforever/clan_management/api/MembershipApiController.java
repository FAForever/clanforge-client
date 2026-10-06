//SPDX-License-Identifier: MIT
package com.faforever.clan_management.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.faforever.clan_management.service.AppUserService;
import com.faforever.clan_management.service.ClanManagementService;
import com.faforever.clan_management.service.MembershipService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

// Managing the members of the CALLER'S OWN clan. The clan is always the one the
// bearer token's user owns or moderates, never named in the path - the service
// layer resolves it from the caller, exactly as the website does. Every rule
// (who may act, roles, the suspend freeze) stays in MembershipService /
// ClanManagementService; this controller only adapts it to REST.
@RestController
@RequestMapping("/api/clan")
@Tag(name = "Membership", description = "Manage the members of your own clan.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class MembershipApiController {

    private final AppUserService users;
    private final MembershipService memberships;
    private final ClanManagementService clans;

    public MembershipApiController(AppUserService users, MembershipService memberships, ClanManagementService clans) {
        this.users = users;
        this.memberships = memberships;
        this.clans = clans;
    }

    @DeleteMapping("/members/{userId}")
    @Operation(summary = "Remove a member from your clan")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Member removed"),
            @ApiResponse(responseCode = "400", description = "That player is not in your clan, or cannot be removed"),
            @ApiResponse(responseCode = "401", description = "Bearer token is missing or invalid"),
            @ApiResponse(responseCode = "403", description = "Caller is not an owner or moderator of a clan")
    })
    public ResponseEntity<Void> removeMember(@PathVariable Long userId, Authentication auth) {
        memberships.removeMember(users.require(auth), userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/members/{userId}")
    @Operation(summary = "Change a member's role (owner only; MODERATOR or MEMBER)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Role changed"),
            @ApiResponse(responseCode = "400", description = "The role is unknown or the target is not in your clan"),
            @ApiResponse(responseCode = "401", description = "Bearer token is missing or invalid"),
            @ApiResponse(responseCode = "403", description = "Only the clan owner can change roles")
    })
    public ResponseEntity<Void> setRole(@PathVariable Long userId, @RequestBody RoleRequest body, Authentication auth) {
        memberships.setRole(users.require(auth), userId, body.role());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/transfer")
    @Operation(summary = "Transfer ownership of your clan to a current member (owner only)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Ownership transferred; the former owner stays on as a moderator"),
            @ApiResponse(responseCode = "400", description = "The chosen user is not a current member of your clan"),
            @ApiResponse(responseCode = "401", description = "Bearer token is missing or invalid"),
            @ApiResponse(responseCode = "403", description = "Only the clan owner can transfer ownership")
    })
    public ResponseEntity<Void> transfer(@RequestBody TransferRequest body, Authentication auth) {
        clans.transferOwnership(users.require(auth), body.userId());
        return ResponseEntity.noContent().build();
    }
}
