//SPDX-License-Identifier: MIT
package com.faforever.clan_management.api;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.faforever.clan_management.entity.ClanInvitation;
import com.faforever.clan_management.service.AppUserService;
import com.faforever.clan_management.service.InvitationCard;
import com.faforever.clan_management.service.InvitationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

//the invitation endpoints, the caller is whoever the bearer token names and every rule stays in InvitationService
@RestController
@RequestMapping("/api")
@Tag(name = "Invitations", description = "Invite users and respond to clan invitations.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class InvitationApiController {

    private final AppUserService users;
    private final InvitationService invitations;

    public InvitationApiController(AppUserService users, InvitationService invitations) {
        this.users = users;
        this.invitations = invitations;
    }

    @PostMapping("/clans/{id}/invite")
    @Operation(summary = "Invite a user to a clan")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Invitation created"),
            @ApiResponse(responseCode = "400", description = "Invitation violates a clan rule"),
            @ApiResponse(responseCode = "401", description = "Bearer token is missing or invalid"),
            @ApiResponse(responseCode = "403", description = "Caller cannot invite users to this clan"),
            @ApiResponse(responseCode = "404", description = "Clan or user was not found")
    })
    public ResponseEntity<InvitationResponse> invite(@PathVariable Long id, @RequestBody InviteRequest body,Authentication auth) {
        ClanInvitation inv = invitations.invite(users.require(auth), id, body.username(), body.role());
        //System.out.println("api invite " + id + " " + body.username() + " " + inv.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(InvitationResponse.from(new InvitationCard(inv)));
    }

    @GetMapping("/invitations")
    @Operation(summary = "List the caller's open invitations")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Open invitations returned"),
            @ApiResponse(responseCode = "401", description = "Bearer token is missing or invalid")
    })
    public List<InvitationResponse> inbox(Authentication auth) {
        List<InvitationResponse> out = new ArrayList<>();
        for (InvitationCard c : invitations.inbox(users.require(auth))) {
            out.add(InvitationResponse.from(c)); // open invitations only, newest first, the order the service gives
        }
        return out;
    }

    @PostMapping("/invitations/{id}/accept")
    @Operation(summary = "Accept an invitation")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invitation accepted"),
            @ApiResponse(responseCode = "400", description = "Invitation cannot be accepted"),
            @ApiResponse(responseCode = "401", description = "Bearer token is missing or invalid"),
            @ApiResponse(responseCode = "403", description = "Invitation belongs to another user"),
            @ApiResponse(responseCode = "404", description = "Invitation was not found")
    })
    public MembershipResponse accept(@PathVariable Long id,Authentication auth) {
        return MembershipResponse.from(invitations.accept(users.require(auth), id));
    }

    @PostMapping("/invitations/{id}/decline")
    @Operation(summary = "Decline an invitation")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Invitation declined"),
            @ApiResponse(responseCode = "400", description = "Invitation cannot be declined"),
            @ApiResponse(responseCode = "401", description = "Bearer token is missing or invalid"),
            @ApiResponse(responseCode = "403", description = "Invitation belongs to another user"),
            @ApiResponse(responseCode = "404", description = "Invitation was not found")
    })
    public ResponseEntity<Void> decline(@PathVariable Long id, Authentication auth) {
        invitations.decline(users.require(auth), id);
        //System.out.println("api decline " + id);
        return ResponseEntity.noContent().build();
    }
}
