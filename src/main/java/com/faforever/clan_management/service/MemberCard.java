package com.faforever.clan_management.service;

import java.time.OffsetDateTime;

import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.entity.ClanMembership;

// one roster row, the membership plus the bits the page derives from it
public class MemberCard {

    private final ClanMembership membership;

    public MemberCard(ClanMembership membership) {
        this.membership = membership;
    }

    public ClanMembership getMembership() {
        return membership;
    }

    public AppUser getUser() {
        return membership.getUser();
    }

    public String getRole() {
        return membership.getRole();
    }

    public String getRoleLabel() {
        return Names.roleLabel(membership.getRole());
    }

    public String getInitials() {
        return Names.initials(membership.getUser().getDisplayName());//first two letters or digits, for the avatar circle
    }

    public OffsetDateTime getJoinedAt() {
        return membership.getJoinedAt();
    }

    public boolean isOwner() {
        return MembershipService.ownerRole.equals(membership.getRole());
    }

    public boolean isModerator() {
        return MembershipService.moderatorRole.equals(membership.getRole());
    }
}
