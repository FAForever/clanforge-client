//SPDX-License-Identifier: MIT
package com.faforever.clan_management.api;

import java.time.OffsetDateTime;

import com.faforever.clan_management.entity.ClanReport;

public record ReportResponse(Long id, Long clanId, String clanName, String clanTag, Long reporterId, String reporterDisplayName, String category, String reason, String state, OffsetDateTime createdAt,OffsetDateTime resolvedAt,Long resolvedById) {

    public static ReportResponse from(ClanReport r) {
        return new ReportResponse(r.getId(), r.getClan().getId(), r.getClan().getName(), r.getClan().getTag(), r.getReporter().getId(), r.getReporter().getDisplayName(), r.getCategory(), r.getReason(), r.getState(), r.getCreatedAt(), r.getResolvedAt(), r.getResolvedBy() == null ? null : r.getResolvedBy().getId());
    }
}
