package com.faforever.clan_management.service;

import java.time.OffsetDateTime;

import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.entity.ClanInvitation;

//one invitation row for either list, the inbox or the invites a clan has sent
public class InvitationCard {

    private final ClanInvitation invitation;

    public InvitationCard(ClanInvitation invitation) {
        this.invitation = invitation;
    }

    public Long getId() {
        return invitation.getId();
    }

    public Clan getClan() {
        return invitation.getClan();
    }

    public AppUser getInvitee() {
        return invitation.getInvitee();
    }

    public AppUser getInviter() {
        return invitation.getInviter();
    }

    public String getInviteeInitials() {
        return Names.initials(invitation.getInvitee().getDisplayName());
    }

    public String getRole() {
        return invitation.getRole();
    }

    public String getRoleLabel() {
        return Names.roleLabel(invitation.getRole());
    }

    public String getState() {
        return invitation.getState();
    }

    public OffsetDateTime getCreatedAt() {
        return invitation.getCreatedAt();
    }

    public OffsetDateTime getExpiresAt() {
        return invitation.getExpiresAt();
    }
}
