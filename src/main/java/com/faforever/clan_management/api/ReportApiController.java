//SPDX-License-Identifier: MIT
package com.faforever.clan_management.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.faforever.clan_management.entity.ClanReport;
import com.faforever.clan_management.service.AppUserService;
import com.faforever.clan_management.service.ReportService;

@RestController
public class ReportApiController {

    private final AppUserService users;
    private final ReportService reports;

    public ReportApiController(AppUserService users,ReportService reports) {
        this.users = users;
        this.reports = reports;
    }

    //anyone with a token can report a clan, what they may report and how often is ReportServices call
    @PostMapping("/api/reports")
    public ResponseEntity<ReportResponse> file(@RequestBody ReportRequest body, Authentication auth) {
        ClanReport report = reports.file(users.require(auth),body.clanId(), body.category(),body.reason());
        //System.out.println("api report " + body.clanId() + " " + report.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ReportResponse.from(report));
    }
}
