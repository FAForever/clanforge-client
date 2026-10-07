package com.faforever.clan_management.service;

import java.util.List;


import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.entity.ClanMembership;

//what every page needs about the signed-in person: who they are, which clan, how many invites wait
public class SignedInUser {

    private final AppUser user;
    private final ClanMembership membership;
    private final long pendingInvitations;
    private final List<Notice> notices;

    public SignedInUser(AppUser user, ClanMembership membership, long pendingInvitations) {
        this(user, membership, pendingInvitations, List.of());
    }

    public SignedInUser(AppUser user, ClanMembership membership, long pendingInvitations, List<Notice> notices) {
        this.user = user;
        this.membership = membership;
        this.pendingInvitations = pendingInvitations;
        this.notices = notices;
    }

    public List<Notice> getNotices() {
        return notices;
    }

    public AppUser getUser() {
        return user;
    }

    public ClanMembership getMembership() {
        return membership;
    }

    public long getPendingInvitations() {
        return pendingInvitations;
    }

    public String getInitials() {
        return Names.initials(user.getDisplayName());
    }

    public String getClanName() {
        return membership == null ? null : membership.getClan().getName();
    }

    public String getClanTag() {
        return membership == null ? null : membership.getClan().getTag();
    }

    public String getRoleLabel() {
        return membership == null ? "" : Names.roleLabel(membership.getRole());
    }

    public boolean isInClan() {
        return membership != null;
    }

    public boolean isOwner() {
        return membership != null && MembershipService.ownerRole.equals(membership.getRole());
    }

    public boolean isCanManage() {
        if (membership == null) return false;
        String role = membership.getRole(); //owner or moderator, the two roles that run a clan
        return MembershipService.ownerRole.equals(role) || MembershipService.moderatorRole.equals(role);
    }
}
