package com.faforever.clan_management.web;

import java.io.IOException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.service.AppUserService;
import com.faforever.clan_management.service.ClanForm;
import com.faforever.clan_management.service.ClanManagementService;
import com.faforever.clan_management.service.ClanRuleViolation;
import com.faforever.clan_management.service.Regions;
import com.faforever.clan_management.service.TagRule;

@Controller
public class CreateClanController {

    private final AppUserService users;
    private final ClanManagementService clans;
    private final TagRule tagRule;
    private final Regions regions;

    public CreateClanController(AppUserService users, ClanManagementService clans, TagRule tagRule, Regions regions) {
        this.users = users;
        this.clans = clans;
        this.tagRule = tagRule;
        this.regions = regions;
    }

    @GetMapping("/create-clan")
    public String form(Model model) {
        model.addAttribute("form", new ClanForm());
        model.addAttribute("tagHint", tagRule.getHint());
        model.addAttribute("regions", regions.getNames());
        return "create-clan";
    }

    @PostMapping("/create-clan")
    public String create(@ModelAttribute("form") ClanForm form, @RequestParam(value = "image",required = false) MultipartFile image, Authentication auth, Model model, RedirectAttributes flash) {
        try {
            byte[] bytes = image == null || image.isEmpty() ? null : image.getBytes();
            if (bytes != null) clans.checkImage(bytes); //a bad image is refused before the clan exists
            AppUser owner = users.require(auth);
            clans.create(owner, form);
            if (bytes != null) clans.setLogo(owner, bytes);
            flash.addFlashAttribute("notice", "Your clan is live.");
            return "redirect:/clanhome";
        } catch (ClanRuleViolation e) {
            model.addAttribute("error", e.getMessage());//a refused form comes straight back with the sentence the service gave
        } catch (DataIntegrityViolationException e) {
            model.addAttribute("error", "That clan name or tag was taken a moment ago. Try another."); // a race on the unique index gets a plainer one
        } catch (IOException e) {
            model.addAttribute("error", "The image could not be read. Try another file.");
        }
        model.addAttribute("tagHint", tagRule.getHint());
        model.addAttribute("regions", regions.getNames());
        //System.out.println("create refused " + form.getTag());
        return "create-clan";
    }
}
