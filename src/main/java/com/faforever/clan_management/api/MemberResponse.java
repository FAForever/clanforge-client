package com.faforever.clan_management.api;

import java.time.OffsetDateTime;

import com.faforever.clan_management.service.MemberCard;

public record MemberResponse(
        Long id,
        String displayName,
        String role,
        OffsetDateTime joinedAt
) {
    public static MemberResponse from(MemberCard member) {
        return new MemberResponse(
                member.getUser().getId(),
                member.getUser().getDisplayName(),
                member.getRole(),
                member.getJoinedAt()
        );
    }
}