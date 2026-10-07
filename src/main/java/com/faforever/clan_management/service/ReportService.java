//SPDX-License-Identifier: MIT
package com.faforever.clan_management.service;

import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.entity.ClanReport;
import com.faforever.clan_management.repository.ClanMembershipRepository;
import com.faforever.clan_management.repository.ClanReportRepository;
import com.faforever.clan_management.repository.ClanRepository;

@Service
public class ReportService {

    static final int reasonMax = 1000;
    static final List<String> categories = List.of("OFFENSIVE_NAME", "OFFENSIVE_TAG", "INAPPROPRIATE_CONTENT");
    static final List<String> states = List.of("OPEN", "RESOLVED", "DISMISSED");

    private final ClanReportRepository reports;
    private final ClanRepository clans;
    private final ClanMembershipRepository memberships;

    public ReportService(ClanReportRepository reports,ClanRepository clans, ClanMembershipRepository memberships) {
        this.reports = reports;
        this.clans = clans;
        this.memberships = memberships;
    }

    //anyone signed in can report a clan they are able to see, once while their last report on it is still open
    @Transactional
    public ClanReport file(AppUser reporter, Long clanId, String category, String reason) {
        String kind = category == null ? "" : category.trim().toUpperCase(Locale.ROOT);
        if (!categories.contains(kind)) throw new ClanRuleViolation("Say what the report is about: an offensive name, an offensive tag or inappropriate content.");
        String text = reason == null ? "" : reason.trim();
        if (text.isEmpty()) throw new ClanRuleViolation("Say what is wrong with the clan.");
        if (text.length() > reasonMax) throw new ClanRuleViolation("Reports are " + reasonMax + " characters at most.");
        boolean seen = clanId != null && (clans.existsByIdAndVisibilityAndDeletedAtIsNull(clanId, "PUBLIC") || memberships.existsByClanIdAndUserIdAndLeftAtIsNull(clanId, reporter.getId()));
        if (!seen) throw new ClanRuleViolation("That clan does not exist."); //an outsider is turned away here, before the lock, and hears the same sentence as below
        if (reports.existsByClanIdAndReporterIdAndState(clanId, reporter.getId(),"OPEN")) throw new ClanRuleViolation("You have already reported this clan."); //asked again under the lock, this one only spares the wait
        Clan clan = clans.lockLive(clanId).orElse(null); //locked, so a report cannot land on a clan an administrator is deleting
        boolean hidden = clan != null && !"PUBLIC".equals(clan.getVisibility()) && memberships.findActiveByClanIdAndUserId(clan.getId(),reporter.getId()).isEmpty();
        if (clan == null || hidden) throw new ClanRuleViolation("That clan does not exist."); // a private clan reads the same as a missing one to an outsider
        if (reports.existsByClanIdAndReporterIdAndState(clan.getId(), reporter.getId(), "OPEN")) throw new ClanRuleViolation("You have already reported this clan.");
        return reports.save(new ClanReport(clan,reporter, kind, text));
    }

    // the queue an administrator works through, one state at a time or ALL of them, newest first
    public Page<ClanReport> byState(String state, Pageable pageable) {
        String wanted = state == null || state.isBlank() ? "OPEN" : state.trim().toUpperCase(Locale.ROOT);
        if ("ALL".equals(wanted)) return reports.findAllNewestFirst(pageable);
        if (!states.contains(wanted)) throw new ClanRuleViolation("Reports are open, resolved or dismissed.");
        return reports.findByStateNewestFirst(wanted, pageable);
    }

    // the open reports on one clan, for the admin page about it
    public List<ClanReport> openFor(Long clanId) {
        return reports.findOpenForClan(clanId);
    }

    //settled once, by whoever acted on it or threw it out
    @Transactional
    public ClanReport close(AppUser admin,Long reportId, boolean dismissed) {
        ClanReport report = reports.lockById(reportId).orElseThrow(() -> new ClanRuleViolation("That report does not exist."));
        if (!"OPEN".equals(report.getState())) throw new ClanRuleViolation("That report is already closed.");
        report.close(dismissed ? "DISMISSED" : "RESOLVED", admin);
        return report;
    }
}
