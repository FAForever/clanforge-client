//SPDX-License-Identifier: MIT
package com.faforever.clan_management.api;

import java.time.OffsetDateTime;

import com.faforever.clan_management.service.InvitationCard;

public record InvitationResponse(Long id, Long clanId,String clanName, String clanTag, Long inviteeId, String inviteeDisplayName, Long inviterId, String inviterDisplayName, String role,String state, OffsetDateTime createdAt, OffsetDateTime expiresAt) {

    // built from the card the pages already use, so both read an invitation the same way
    public static InvitationResponse from(InvitationCard c) {
        //System.out.println("invitation " + c.getId() + " " + c.getState());
        return new InvitationResponse(c.getId(), c.getClan().getId(), c.getClan().getName(), c.getClan().getTag(), c.getInvitee().getId(), c.getInvitee().getDisplayName(),c.getInviter().getId(),c.getInviter().getDisplayName(), c.getRole(), c.getState(),c.getCreatedAt(), c.getExpiresAt());
    }
}
