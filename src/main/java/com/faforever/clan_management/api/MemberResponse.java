package com.faforever.clan_management.api;

import com.faforever.clan_management.service.MemberCard;

public record MemberResponse(
        String displayName,
        String role
) {
    public static MemberResponse from(MemberCard member) {
        return new MemberResponse(
                member.getUser().getDisplayName(),
                member.getRole()
        );
    }
}
