package com.faforever.clan_management.api;

import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.service.ClanCard;

public record ClanSummaryResponse(
        Long id,
        String name,
        String tag,
        String description,
        boolean requiresInvitation,
        String region,
        boolean hasLogo,
        long memberCount
) {
    public static ClanSummaryResponse from(ClanCard card) {
        Clan clan = card.getClan();

        return new ClanSummaryResponse(
                clan.getId(),
                clan.getName(),
                clan.getTag(),
                clan.getDescription(),
                clan.isRequiresInvitation(),
                clan.getRegion(),
                clan.isHasLogo(),
                card.getMemberCount()
        );
    }
}