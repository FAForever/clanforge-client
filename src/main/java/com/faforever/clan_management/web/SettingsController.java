package com.faforever.clan_management.web;

import java.io.IOException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.service.AppUserService;
import com.faforever.clan_management.service.CalendarService;
import com.faforever.clan_management.service.ClanForm;
import com.faforever.clan_management.service.ClanManagementService;
import com.faforever.clan_management.service.ClanRuleViolation;
import com.faforever.clan_management.service.MembershipService;
import com.faforever.clan_management.service.Regions;
import com.faforever.clan_management.service.SignedInUser;
import com.faforever.clan_management.service.TagRule;

@Controller
public class SettingsController {

    private final AppUserService users;
    private final ClanManagementService clans;
    private final MembershipService memberships;
    private final CalendarService calendar;
    private final TagRule tagRule;
    private final Regions regions;

    public SettingsController(AppUserService users,ClanManagementService clans,MembershipService memberships, CalendarService calendar,TagRule tagRule,Regions regions) {
        this.users = users;
        this.clans = clans;
        this.memberships = memberships;
        this.calendar = calendar;
        this.tagRule = tagRule;
        this.regions = regions;
    }

    @GetMapping("/settings")
    public String settings(Authentication auth,Model model) {
        SignedInUser me = users.signedIn(auth).orElseThrow();//account details for everyone, the clan form and danger zone only when they run a clan
        if (me.getMembership() != null) {
            model.addAttribute("form", ClanForm.of(me.getMembership().getClan()));
            clanPanels(me, model);
        }
        model.addAttribute("tagHint",tagRule.getHint());
        model.addAttribute("regions", regions.getNames());
        return "settings";
    }

    @PostMapping("/settings/clan")
    public String save(@ModelAttribute("form") ClanForm form,Authentication auth,Model model, RedirectAttributes flash) {
        try {
            clans.update(users.require(auth), form);
            flash.addFlashAttribute("notice", "Settings saved.");
            return "redirect:/settings";
        } catch (ClanRuleViolation e) {
            model.addAttribute("error", e.getMessage());
        } catch (DataIntegrityViolationException e) {
            model.addAttribute("error", "That clan name or tag was taken a moment ago. Try another.");
        }
        SignedInUser me = users.signedIn(auth).orElseThrow();
        if (me.getMembership() != null) clanPanels(me, model);
        model.addAttribute("tagHint", tagRule.getHint());
        model.addAttribute("regions", regions.getNames());
        return "settings";
    }

    //the image has its own form, so a big upload never travels with every other save
    @PostMapping("/settings/logo")
    public String logo(@RequestParam("image") MultipartFile image, Authentication auth, RedirectAttributes flash) {
        try {
            clans.setLogo(users.require(auth), image.isEmpty() ? null : image.getBytes());
            flash.addFlashAttribute("notice", "Clan image updated.");
        } catch (ClanRuleViolation e) {
            flash.addFlashAttribute("error", e.getMessage());
        } catch (IOException e) {
            flash.addFlashAttribute("error", "The image could not be read. Try another file.");
        }
        return "redirect:/settings";
    }

    @PostMapping("/settings/logo/remove")
    public String removeLogo(Authentication auth,RedirectAttributes flash) {
        try {
            clans.removeLogo(users.require(auth));
            flash.addFlashAttribute("notice","Clan image removed.");
        } catch (ClanRuleViolation e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/settings";
    }

    @PostMapping("/settings/motd")
    public String motd(@RequestParam String motd, Authentication auth, RedirectAttributes flash) {
        try {
            clans.updateMotd(users.require(auth), motd);
            flash.addFlashAttribute("notice", "Message updated.");
        } catch (ClanRuleViolation e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/settings#motd-section";
    }

    @PostMapping("/settings/events")
    public String addEvent(@RequestParam String title, @RequestParam String date,@RequestParam String time, Authentication auth,RedirectAttributes flash) {
        try {
            calendar.add(users.require(auth),title,date, time);
            flash.addFlashAttribute("notice", "Event added.");
        } catch (ClanRuleViolation e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/settings#events-section";
    }

    @PostMapping("/settings/events/{id}/remove")
    public String removeEvent(@PathVariable Long id, Authentication auth, RedirectAttributes flash) {
        try {
            calendar.remove(users.require(auth), id);
            flash.addFlashAttribute("notice","Event removed.");
        } catch (ClanRuleViolation e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/settings#events-section";
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
    public String delete(Authentication auth, RedirectAttributes flash) {
        try {
            clans.delete(users.require(auth));
            flash.addFlashAttribute("notice", "The clan has been deleted.");
            return "redirect:/clans";
        } catch (ClanRuleViolation e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/settings";
        }
    }

    // the clan, its roster for the transfer list and its calendar, shared by the page and the refused save
    private void clanPanels(SignedInUser me, Model model) {
        Clan clan = me.getMembership().getClan();
        model.addAttribute("clan", clan);
        model.addAttribute("roster",memberships.roster(clan));
        model.addAttribute("events",calendar.upcoming(clan, 20));
    }
}
