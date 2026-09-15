package com.faforever.clan_management.web;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.service.AppUserService;
import com.faforever.clan_management.service.ClanForm;
import com.faforever.clan_management.service.ClanManagementService;
import com.faforever.clan_management.service.ClanRuleViolation;
import com.faforever.clan_management.service.MembershipService;
import com.faforever.clan_management.service.SignedInUser;
import com.faforever.clan_management.service.TagRule;

@Controller
public class SettingsController {

    private final AppUserService users;
    private final ClanManagementService clans;
    private final MembershipService memberships;
    private final TagRule tagRule;

    public SettingsController(AppUserService users, ClanManagementService clans, MembershipService memberships,TagRule tagRule) {
        this.users = users;
        this.clans = clans;
        this.memberships = memberships;
        this.tagRule = tagRule;
    }

    @GetMapping("/settings")
    public String settings(Authentication auth, Model model) {
        SignedInUser me = users.signedIn(auth).orElseThrow();//account details for everyone, the clan form and danger zone only when they run a clan
        if (me.getMembership() != null) {
            Clan clan = me.getMembership().getClan();
            model.addAttribute("clan", clan);
            model.addAttribute("form", ClanForm.of(clan));
            model.addAttribute("roster",memberships.roster(clan));
        }
        model.addAttribute("tagHint", tagRule.getHint());
        //System.out.println("settings " + me.getUser().getDisplayName());
        return "settings";
    }

    @PostMapping("/settings/clan")
    public String save(@ModelAttribute("form") ClanForm form, Authentication auth, Model model,RedirectAttributes flash) {
        try {
            clans.update(users.require(auth), form);
            flash.addFlashAttribute("notice", "Settings saved.");
            return "redirect:/settings";
        } catch (ClanRuleViolation e) {
            model.addAttribute("error",e.getMessage());
        } catch (DataIntegrityViolationException e) {
            model.addAttribute("error", "That clan name or tag was taken a moment ago. Try another.");
        }
        SignedInUser me = users.signedIn(auth).orElseThrow();
        if (me.getMembership() != null) {
            model.addAttribute("clan", me.getMembership().getClan());
            model.addAttribute("roster", memberships.roster(me.getMembership().getClan()));
        }
        model.addAttribute("tagHint", tagRule.getHint());
        return "settings";
    }

    @PostMapping("/settings/transfer")
    public String transfer(@RequestParam Long userId, Authentication auth, RedirectAttributes flash) {
        try {
            clans.transferOwnership(users.require(auth), userId);
            flash.addFlashAttribute("notice", "Ownership transferred. You are now a moderator.");
            return "redirect:/clanhome";
        } catch (ClanRuleViolation e) {
            flash.addFlashAttribute("error",e.getMessage());
            return "redirect:/settings";
        }
    }

    @PostMapping("/settings/delete")
    public String delete(Authentication auth,RedirectAttributes flash) {
        try {
            clans.delete(users.require(auth));
            flash.addFlashAttribute("notice", "The clan has been deleted.");
            return "redirect:/clans";
        } catch (ClanRuleViolation e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/settings";
        }
    }
}
