package com.faforever.clan_management.api;

import java.util.List;

import org.springframework.data.domain.Page;

import com.faforever.clan_management.service.ClanCard;

public record ClanPageResponse(
        List<ClanSummaryResponse> clans,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasPrevious,
        boolean hasNext
) {
    public static ClanPageResponse from(Page<ClanCard> page) {
        return new ClanPageResponse(
                page.getContent().stream()
                        .map(ClanSummaryResponse::from)
                        .toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasPrevious(),
                page.hasNext()
        );
    }
}