//SPDX-License-Identifier: MIT
package com.faforever.clan_management.web;

import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.entity.ClanReport;
import com.faforever.clan_management.service.AdminAccess;
import com.faforever.clan_management.service.AdminService;
import com.faforever.clan_management.service.ClanAccessRefused;
import com.faforever.clan_management.service.ClanCard;
import com.faforever.clan_management.service.ClanRuleViolation;
import com.faforever.clan_management.service.MemberCard;
import com.faforever.clan_management.service.MembershipService;
import com.faforever.clan_management.service.ReportService;

import tools.jackson.databind.ObjectMapper;

// The admin console in the browser, the twin of /api/admin: every clan whatever its
// visibility, one clan's dossier with its moderation history and the actions, and the
// report queue. SecurityConfig closes /admin to everyone without the admin role and
// AdminAccess asks again here through admin(), outside every try: ClanAccessRefused is a
// ClanRuleViolation, which the actions otherwise turn into a flash message, and a refusal
// has to stay a 403.
@Controller
@RequestMapping("/admin")
public class AdminController {

    static final int pageSize = 20;
    static final int maxPage = 10_000;

    private final AdminAccess access;
    private final AdminService admins;
    private final ReportService reports;
    private final MembershipService memberships;
    private final ObjectMapper json;

    public AdminController(AdminAccess access, AdminService admins, ReportService reports, MembershipService memberships, ObjectMapper json) {
        this.access = access;
        this.admins = admins;
        this.reports = reports;
        this.memberships = memberships;
        this.json = json;
    }

    @GetMapping
    public String console() {
        return "redirect:/admin/clans";
    }

    @GetMapping("/clans")
    public String clans(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "all") String filter, @RequestParam(defaultValue = "0") int page,
                        Authentication auth, Model model, RedirectAttributes flash) {
        requireAdmin(auth);
        String wanted = filter.isBlank() ? "all" : filter.trim().toLowerCase(Locale.ROOT);
        Page<ClanCard> found;
        try {
            found = admins.clans(q, wanted, PageRequest.of(clamp(page), pageSize));
        } catch (ClanRuleViolation e) { // an unknown filter, only reachable by editing the address
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/clans";
        }
        model.addAttribute("overview", admins.overview());
        model.addAttribute("clans", found.getContent());
        model.addAttribute("q", q);
        model.addAttribute("filter", wanted);
        paging(model, found);
        model.addAttribute("totalClans", found.getTotalElements());
        return "admin/clans";
    }

    @GetMapping("/clans/{id}")
    public String clan(@PathVariable Long id, Authentication auth, Model model) {
        requireAdmin(auth);
        Clan clan = admins.clan(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        List<MemberCard> roster = memberships.roster(clan);
        model.addAttribute("overview", admins.overview());
        model.addAttribute("clan", clan);
        model.addAttribute("roster", roster);
        model.addAttribute("history", admins.history(id).stream().map(a -> ModerationEntry.from(a, json)).toList());
        model.addAttribute("openReports", reports.openFor(id));
        model.addAttribute("deleted", clan.getDeletedAt() != null);
        model.addAttribute("suspended", "SUSPENDED".equals(clan.getStatus()));
        return "admin/clan";
    }

    @GetMapping("/reports")
    public String reportQueue(@RequestParam(defaultValue = "OPEN") String state, @RequestParam(defaultValue = "0") int page,
                              Authentication auth, Model model, RedirectAttributes flash) {
        requireAdmin(auth);
        String wanted = state.isBlank() ? "OPEN" : state.trim().toUpperCase(Locale.ROOT);
        Page<ClanReport> found;
        try {
            found = reports.byState(wanted, PageRequest.of(clamp(page), pageSize));
        } catch (ClanRuleViolation e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/reports";
        }
        model.addAttribute("overview", admins.overview());
        model.addAttribute("reports", found.getContent());
        model.addAttribute("state", wanted);
        paging(model, found);
        model.addAttribute("totalReports", found.getTotalElements());
        return "admin/reports";
    }

    // The actions. Each goes back to the clan's page with what happened, or with the
    // service's sentence when it refused (already suspended, a taken tag, a report about
    // another clan). AdminService locks the clan, so two administrators are serialised.
    @PostMapping("/clans/{id}/suspend")
    public String suspend(@PathVariable Long id, @RequestParam(required = false) String reason, @RequestParam(required = false) Long reportId,
                          Authentication auth, RedirectAttributes flash) {
        AppUser admin = requireAdmin(auth);
        return act(id, flash, "Clan suspended. It takes no new members until the suspension is lifted.", () -> admins.suspend(admin, id, reason, reportId));
    }

    @PostMapping("/clans/{id}/unsuspend")
    public String unsuspend(@PathVariable Long id, @RequestParam(required = false) String reason, Authentication auth, RedirectAttributes flash) {
        AppUser admin = requireAdmin(auth);
        return act(id, flash, "Suspension lifted.", () -> admins.unsuspend(admin, id, reason));
    }

    @PostMapping("/clans/{id}/rename")
    public String rename(@PathVariable Long id, @RequestParam(required = false) String name, @RequestParam(required = false) String reason,
                         @RequestParam(required = false) Long reportId, Authentication auth, RedirectAttributes flash) {
        AppUser admin = requireAdmin(auth);
        return act(id, flash, "Clan renamed.", () -> admins.rename(admin, id, name, reason, reportId));
    }

    @PostMapping("/clans/{id}/retag")
    public String retag(@PathVariable Long id, @RequestParam(required = false) String tag, @RequestParam(required = false) String reason,
                        @RequestParam(required = false) Long reportId, Authentication auth, RedirectAttributes flash) {
        AppUser admin = requireAdmin(auth);
        return act(id, flash, "Clan tag changed.", () -> admins.retag(admin, id, tag, reason, reportId));
    }

    @PostMapping("/clans/{id}/delete")
    public String delete(@PathVariable Long id, @RequestParam(required = false) String reason, @RequestParam(required = false) Long reportId,
                         Authentication auth, RedirectAttributes flash) {
        AppUser admin = requireAdmin(auth);
        return act(id, flash, "Clan deleted. Its members were released and its open reports settled.", () -> admins.delete(admin, id, reason, reportId));
    }

    // Closes a report with no action on the clan. clanId says the dismissal came from that
    // clan's page and goes back there; it is a number, so it can only ever name an admin page.
    @PostMapping("/reports/{id}/dismiss")
    public String dismiss(@PathVariable Long id, @RequestParam(required = false) Long clanId, Authentication auth, RedirectAttributes flash) {
        AppUser admin = requireAdmin(auth);
        try {
            reports.close(admin, id, true);
            flash.addFlashAttribute("notice", "Report #" + id + " dismissed.");
        } catch (ClanAccessRefused e) {
            throw forbidden(e);
        } catch (ClanRuleViolation e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return clanId == null ? "redirect:/admin/reports" : "redirect:/admin/clans/" + clanId;
    }

    private String act(Long clanId, RedirectAttributes flash, String done, Runnable action) {
        try {
            action.run();
            flash.addFlashAttribute("notice", done);
        } catch (ClanAccessRefused e) {
            throw forbidden(e);
        } catch (ClanRuleViolation e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/clans/" + clanId;
    }

    // AdminAccess asks again what SecurityConfig already checked. Should the two ever
    // disagree the answer is still a 403, not the 500 an unhandled exception would give
    private AppUser requireAdmin(Authentication auth) {
        try {
            return access.require(auth);
        } catch (ClanAccessRefused e) {
            throw forbidden(e);
        }
    }

    private static ResponseStatusException forbidden(ClanAccessRefused e) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage(), e);
    }

    private static void paging(Model model, Page<?> page) {
        model.addAttribute("currentPage", page.getNumber());
        model.addAttribute("totalPages", Math.max(page.getTotalPages(), 1));
        model.addAttribute("hasPrevious", page.hasPrevious());
        model.addAttribute("hasNext", page.hasNext());
    }

    //a crafted ?page cannot ask the database for an enormous offset
    private static int clamp(int page) {
        return Math.min(Math.max(page, 0), maxPage);
    }
}
