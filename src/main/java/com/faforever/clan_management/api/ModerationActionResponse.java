//SPDX-License-Identifier: MIT
package com.faforever.clan_management.api;

import java.time.OffsetDateTime;

import com.faforever.clan_management.entity.ModerationAction;

//what an admin action answers with: the moderation row it recorded, flattened the way the other responses are
public record ModerationActionResponse(Long id, Long clanId, String clanName, String clanTag, String action, String reason, Long moderatorId, String moderatorDisplayName, Long reportId, OffsetDateTime performedAt) {

    public static ModerationActionResponse from(ModerationAction a) {
        return new ModerationActionResponse(a.getId(), a.getClan().getId(), a.getClan().getName(), a.getClan().getTag(), a.getAction(), a.getReason(), a.getModerator().getId(), a.getModerator().getDisplayName(), a.getReport() == null ? null : a.getReport().getId(), a.getPerformedAt());
    }
}
