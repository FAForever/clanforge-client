package com.faforever.clan_management.web;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.faforever.clan_management.service.AppUserService;
import com.faforever.clan_management.service.ClanForm;
import com.faforever.clan_management.service.ClanManagementService;
import com.faforever.clan_management.service.ClanRuleViolation;
import com.faforever.clan_management.service.TagRule;

@Controller
public class CreateClanController {

    private final AppUserService users;
    private final ClanManagementService clans;
    private final TagRule tagRule;

    public CreateClanController(AppUserService users,ClanManagementService clans, TagRule tagRule) {
        this.users = users;
        this.clans = clans;
        this.tagRule = tagRule;
    }

    @GetMapping("/create-clan")
    public String form(Model model) {
        model.addAttribute("form", new ClanForm());
        model.addAttribute("tagHint",tagRule.getHint());
        return "create-clan";
    }

    @PostMapping("/create-clan")
    public String create(@ModelAttribute("form") ClanForm form,Authentication auth, Model model, RedirectAttributes flash) {
        try {
            clans.create(users.require(auth), form);
            flash.addFlashAttribute("notice","Your clan is live.");
            return "redirect:/clanhome";
        } catch (ClanRuleViolation e) {
            model.addAttribute("error", e.getMessage());//a refused form comes straight back with the sentence the service gave
        } catch (DataIntegrityViolationException e) {
            model.addAttribute("error", "That clan name or tag was taken a moment ago. Try another."); // a race on the unique index gets a plainer one
        }
        model.addAttribute("tagHint",tagRule.getHint());
        //System.out.println("create refused " + form.getTag());
        return "create-clan";
    }
}
