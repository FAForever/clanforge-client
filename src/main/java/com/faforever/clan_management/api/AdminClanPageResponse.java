//SPDX-License-Identifier: MIT
package com.faforever.clan_management.api;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;

import com.faforever.clan_management.service.ClanCard;

public record AdminClanPageResponse(List<AdminClanResponse> clans, int page, int size, long totalElements,int totalPages, boolean hasPrevious, boolean hasNext) {

    public static AdminClanPageResponse from(Page<ClanCard> page) {
        List<AdminClanResponse> rows = new ArrayList<>();
        for (ClanCard c : page.getContent()) {
            rows.add(AdminClanResponse.from(c));
        }
        //System.out.println("admin page " + page.getNumber() + " " + rows.size());
        return new AdminClanPageResponse(rows, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.hasPrevious(), page.hasNext());
    }
}
