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

//the invitation endpoints, the caller is whoever the bearer token names and every rule stays in InvitationService
@RestController
@RequestMapping("/api")
public class InvitationApiController {

    private final AppUserService users;
    private final InvitationService invitations;

    public InvitationApiController(AppUserService users, InvitationService invitations) {
        this.users = users;
        this.invitations = invitations;
    }

    @PostMapping("/clans/{id}/invite")
    public ResponseEntity<InvitationResponse> invite(@PathVariable Long id, @RequestBody InviteRequest body,Authentication auth) {
        ClanInvitation inv = invitations.invite(users.require(auth), id, body.username(), body.role());
        //System.out.println("api invite " + id + " " + body.username() + " " + inv.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(InvitationResponse.from(new InvitationCard(inv)));
    }

    @GetMapping("/invitations")
    public List<InvitationResponse> inbox(Authentication auth) {
        List<InvitationResponse> out = new ArrayList<>();
        for (InvitationCard c : invitations.inbox(users.require(auth))) {
            out.add(InvitationResponse.from(c)); // open invitations only, newest first, the order the service gives
        }
        return out;
    }

    @PostMapping("/invitations/{id}/accept")
    public MembershipResponse accept(@PathVariable Long id,Authentication auth) {
        return MembershipResponse.from(invitations.accept(users.require(auth), id));
    }

    @PostMapping("/invitations/{id}/decline")
    public ResponseEntity<Void> decline(@PathVariable Long id, Authentication auth) {
        invitations.decline(users.require(auth), id);
        //System.out.println("api decline " + id);
        return ResponseEntity.noContent().build();
    }
}
