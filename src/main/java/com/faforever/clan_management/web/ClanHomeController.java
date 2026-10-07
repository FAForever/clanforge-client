package com.faforever.clan_management.web;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.service.AppUserService;
import com.faforever.clan_management.service.CalendarService;
import com.faforever.clan_management.service.ClanRuleViolation;
import com.faforever.clan_management.service.MemberCard;
import com.faforever.clan_management.service.MembershipService;
import com.faforever.clan_management.service.SignedInUser;

@Controller
public class ClanHomeController {

    private final AppUserService users;
    private final MembershipService memberships;
    private final CalendarService calendar;

    public ClanHomeController(AppUserService users, MembershipService memberships, CalendarService calendar) {
        this.users = users;
        this.memberships = memberships;
        this.calendar = calendar;
    }

    @GetMapping("/clanhome")
    public String home(Authentication auth,Model model) {
        SignedInUser me = users.signedIn(auth).orElseThrow(); //the dashboard for whichever clan the signed-in person is in, or the empty state when they are in none
        if (me.getMembership() == null) return "clanhome";
        Clan clan = me.getMembership().getClan();
        List<MemberCard> roster = memberships.roster(clan);
        model.addAttribute("clan", clan);
        model.addAttribute("roster", roster);
        model.addAttribute("memberCount", roster.size());
        model.addAttribute("upcoming", calendar.upcoming(clan, 5));
        return "clanhome";
    }

    @PostMapping("/clanhome/leave")
    public String leave(Authentication auth, RedirectAttributes flash) {
        try {
            memberships.leave(users.require(auth));
            flash.addFlashAttribute("notice", "You have left the clan.");
            return "redirect:/clans";
        } catch (ClanRuleViolation e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/clanhome";
        }
    }

    @PostMapping("/clanhome/members/{userId}/remove")
    public String remove(@PathVariable Long userId,Authentication auth, RedirectAttributes flash) {
        try {
            memberships.removeMember(users.require(auth), userId);
            flash.addFlashAttribute("notice","Member removed.");
        } catch (ClanRuleViolation e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/clanhome";
    }

    @PostMapping("/clanhome/members/{userId}/role")
    public String role(@PathVariable Long userId, @RequestParam String role, Authentication auth, RedirectAttributes flash) {
        try {
            memberships.setRole(users.require(auth),userId, role);
            flash.addFlashAttribute("notice", "Role updated.");
        } catch (ClanRuleViolation e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/clanhome";
    }
}
