//SPDX-License-Identifier: MIT
package com.faforever.clan_management.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.entity.ClanReport;
import com.faforever.clan_management.entity.ModerationAction;
import com.faforever.clan_management.repository.ClanReportRepository;
import com.faforever.clan_management.repository.ClanRepository;
import com.faforever.clan_management.repository.ModerationActionRepository;

import tools.jackson.databind.ObjectMapper;

//what an administrator can do to any clan, the caller has already been checked by AdminAccess before these are reached
@Service
public class AdminService {

    static final int reasonMax = 500;

    private final ClanRepository clans;
    private final ClanReportRepository reports;
    private final ModerationActionRepository actions;
    private final ClanService clanService;
    private final ClanManagementService management;
    private final ClanEventService events;
    private final ObjectMapper json;

    public AdminService(ClanRepository clans, ClanReportRepository reports, ModerationActionRepository actions, ClanService clanService, ClanManagementService management, ClanEventService events, ObjectMapper json) {
        this.clans = clans;
        this.reports = reports;
        this.actions = actions;
        this.clanService = clanService;
        this.management = management;
        this.events = events;
        this.json = json;
    }

    // every clan whatever its visibility, by name then id, the filter narrows it to active, suspended or deleted ones
    public Page<ClanCard> clans(String q, String filter, Pageable pageable) {
        String pattern = "%"+ClanService.escapeLike(q == null ? "" : q.trim().toLowerCase()) + "%";
        String wanted = filter == null || filter.isBlank() ? "all" : filter.trim().toLowerCase();
        //System.out.println("admin list " + pattern + " " + wanted);
        if ("all".equals(wanted)) return clanService.withCounts(clans.searchEvery(pattern, pageable));
        if ("active".equals(wanted)) return clanService.withCounts(clans.searchLiveByStatus(pattern, "ACTIVE",pageable));
        if ("suspended".equals(wanted)) return clanService.withCounts(clans.searchLiveByStatus(pattern, "SUSPENDED", pageable));
        if ("deleted".equals(wanted)) return clanService.withCounts(clans.searchDeleted(pattern, pageable));
        throw new ClanRuleViolation("Clans are listed as all, active, suspended or deleted.");
    }

    public Optional<Clan> clan(Long id) {
        return clans.findById(id);
    }

    public List<ModerationAction> history(Long clanId) {
        return actions.findByClanIdOrderByPerformedAtDesc(clanId);
    }

    //a suspended clan takes no new members, MembershipService.join and InvitationService.accept both read the status
    @Transactional
    public ModerationAction suspend(AppUser admin, Long clanId, String reason, Long reportId) {
        Clan clan = live(clanId);
        if ("SUSPENDED".equals(clan.getStatus())) throw new ClanRuleViolation("That clan is already suspended.");
        ClanReport report = linked(reportId, clan);
        String why = why(reason);
        Map<String, Object> before = events.clanPayload(clan);
        clan.setStatus("SUSPENDED");
        return finish(admin, clan,"SUSPEND", why, report, before);
    }

    @Transactional
    public ModerationAction unsuspend(AppUser admin, Long clanId, String reason) {
        Clan clan = live(clanId);
        if (!"SUSPENDED".equals(clan.getStatus())) throw new ClanRuleViolation("That clan is not suspended.");
        String why = why(reason);
        Map<String, Object> before = events.clanPayload(clan);
        clan.setStatus("ACTIVE");
        return finish(admin, clan, "UNSUSPEND", why,null, before);
    }

    // the same name rules an owner meets on the settings page, without having to be in the clan
    @Transactional
    public ModerationAction rename(AppUser admin, Long clanId, String name, String reason, Long reportId) {
        Clan clan = live(clanId);
        String fresh = name == null ? "" : name.trim();
        if (fresh.equals(clan.getName())) throw new ClanRuleViolation("That is already the clan's name.");
        management.checkName(fresh, clan);
        ClanReport report = linked(reportId, clan);
        String why = why(reason);
        Map<String, Object> before = events.clanPayload(clan);
        clan.setName(fresh);
        //System.out.println("renamed " + before.get("name") + " to " + fresh);
        return finish(admin, clan, "RENAME", why, report, before);
    }

    @Transactional
    public ModerationAction retag(AppUser admin, Long clanId,String tag, String reason,Long reportId) {
        Clan clan = live(clanId);
        String fresh = tag == null ? "" : tag.trim();
        if (fresh.equals(clan.getTag())) throw new ClanRuleViolation("That is already the clan's tag.");
        management.checkTag(fresh, clan);
        ClanReport report = linked(reportId, clan);
        String why = why(reason);
        Map<String, Object> before = events.clanPayload(clan);
        clan.setTag(fresh);
        return finish(admin,clan, "RETAG", why, report, before);
    }

    //the same soft delete the owner gets: the row stays, memberships close, open invites are revoked
    @Transactional
    public ModerationAction delete(AppUser admin, Long clanId, String reason,Long reportId) {
        Clan clan = live(clanId);
        ClanReport report = linked(reportId, clan);
        String why = why(reason);
        Map<String, Object> before = events.clanPayload(clan);
        int gone = management.close(clan);
        //System.out.println("admin deleted " + clan.getTag() + " " + gone);
        return finish(admin,clan, "DELETE", why, report, before);
    }

    private Clan live(Long clanId) {
        return clans.findByIdAndDeletedAtIsNull(clanId).orElseThrow(() -> new ClanRuleViolation("That clan does not exist."));
    }

    // the report an action answers, it has to be an open one about this same clan
    private ClanReport linked(Long reportId, Clan clan) {
        if (reportId == null) return null;
        ClanReport report = reports.findById(reportId).orElseThrow(() -> new ClanRuleViolation("That report does not exist."));
        if (!report.getClan().getId().equals(clan.getId())) throw new ClanRuleViolation("That report is about another clan.");
        if (!"OPEN".equals(report.getState())) throw new ClanRuleViolation("That report is already closed.");
        return report;
    }

    private String why(String reason) {
        String text = reason == null || reason.isBlank() ? null : reason.trim();
        if (text != null && text.length() > reasonMax) throw new ClanRuleViolation("Reasons are " + reasonMax + " characters at most.");
        return text;
    }

    //one moderation row holding what the clan looked like before, one history event holding what it looks like now, and the report closed if there was one
    private ModerationAction finish(AppUser admin, Clan clan, String action, String why, ClanReport report, Map<String, Object> before) {
        clan.touch();
        if (report != null) report.close("RESOLVED",admin);
        ModerationAction row = actions.save(new ModerationAction(clan, admin, action, why,report, json.writeValueAsString(before)));
        Map<String, Object> payload = events.clanPayload(clan);
        payload.put("action", action);
        payload.put("reason", why);
        payload.put("moderationActionId",row.getId());
        payload.put("reportId", report == null ? null : report.getId());
        events.record("clan",clan.getId(), "ModerationActionPerformed", payload, admin);
        //System.out.println("moderation " + action + " " + clan.getId() + " row " + row.getId());
        return row;
    }
}
