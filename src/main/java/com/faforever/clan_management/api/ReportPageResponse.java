//SPDX-License-Identifier: MIT
package com.faforever.clan_management.api;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;

import com.faforever.clan_management.entity.ClanReport;

// one page of the report queue, the same paging fields the clan list answers with
public record ReportPageResponse(List<ReportResponse> reports,int page, int size, long totalElements,int totalPages, boolean hasPrevious, boolean hasNext) {

    public static ReportPageResponse from(Page<ClanReport> page) {
        List<ReportResponse> rows = new ArrayList<>();
        for (ClanReport r : page.getContent()) {
            rows.add(ReportResponse.from(r));
        }
        return new ReportPageResponse(rows, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.hasPrevious(), page.hasNext());
    }
}
