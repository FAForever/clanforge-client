//SPDX-License-Identifier: MIT
package com.faforever.clan_management.api;

import java.time.OffsetDateTime;

import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.service.ClanCard;

//a clan as an administrator sees it, with the status, visibility and deletion the public list leaves out
public record AdminClanResponse(Long id, String name, String tag, String description, String visibility, boolean requiresInvitation, String status,boolean deleted, Long ownerId, String ownerDisplayName, long memberCount, OffsetDateTime createdAt,OffsetDateTime deletedAt) {

    public static AdminClanResponse from(ClanCard card) {
        Clan c = card.getClan();
        return new AdminClanResponse(c.getId(), c.getName(), c.getTag(), c.getDescription(), c.getVisibility(), c.isRequiresInvitation(), c.getStatus(),c.getDeletedAt() != null, c.getOwner().getId(),c.getOwner().getDisplayName(), card.getMemberCount(), c.getCreatedAt(), c.getDeletedAt());
    }
}
