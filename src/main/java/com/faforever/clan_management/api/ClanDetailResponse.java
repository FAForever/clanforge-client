package com.faforever.clan_management.api;

import java.time.OffsetDateTime;
import java.util.List;

import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.service.MemberCard;

public record ClanDetailResponse(
        Long id,
        String name,
        String tag,
        String description,
        String visibility,
        boolean requiresInvitation,
        String region,
        boolean hasLogo,
        Long ownerId,
        String ownerDisplayName,
        OffsetDateTime createdAt,
        List<MemberResponse> members
) {
    public static ClanDetailResponse from(
            Clan clan,
            List<MemberCard> roster
    ) {
        return new ClanDetailResponse(
                clan.getId(),
                clan.getName(),
                clan.getTag(),
                clan.getDescription(),
                clan.getVisibility(),
                clan.isRequiresInvitation(),
                clan.getRegion(),
                clan.isHasLogo(),
                clan.getOwner().getId(),
                clan.getOwner().getDisplayName(),
                clan.getCreatedAt(),
                roster.stream()
                        .map(MemberResponse::from)
                        .toList()
        );
    }
}