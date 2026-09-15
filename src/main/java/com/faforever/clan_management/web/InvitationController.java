package com.faforever.clan_management.web;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.faforever.clan_management.entity.ClanMembership;
import com.faforever.clan_management.service.AppUserService;
import com.faforever.clan_management.service.ClanRuleViolation;
import com.faforever.clan_management.service.InvitationService;
import com.faforever.clan_management.service.SignedInUser;

@Controller
public class InvitationController {

    private final AppUserService users;
    private final InvitationService invitations;

    public InvitationController(AppUserService users,InvitationService invitations) {
        this.users = users;
        this.invitations = invitations;
    }

    @GetMapping("/invitations")
    public String list(Authentication auth,Model model) {
        SignedInUser me = users.signedIn(auth).orElseThrow(); //two lists: invites waiting for this person, and if they run a clan, the invites that clan has out
        model.addAttribute("inbox", invitations.inbox(me.getUser()));
        if (me.isCanManage()) {
            model.addAttribute("sent",invitations.sent(me.getMembership().getClan()));
        }
        //System.out.println("invitations for " + me.getUser().getDisplayName());
        return "invitations";
    }

    @PostMapping("/invitations")
    public String invite(@RequestParam String username,Authentication auth, RedirectAttributes flash) {
        try {
            invitations.invite(users.require(auth), username);
            flash.addFlashAttribute("notice", "Invite sent to "+username.trim()+".");
        } catch (ClanRuleViolation e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/invitations";
    }

    @PostMapping("/invitations/{id}/accept")
    public String accept(@PathVariable Long id, Authentication auth, RedirectAttributes flash) {
        try {
            ClanMembership m = invitations.accept(users.require(auth),id);
            flash.addFlashAttribute("notice", "Welcome to " + m.getClan().getName()+".");
            return "redirect:/clanhome";
        } catch (ClanRuleViolation e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/invitations";
        }
    }

    @PostMapping("/invitations/{id}/decline")
    public String decline(@PathVariable Long id,Authentication auth, RedirectAttributes flash) {
        try {
            invitations.decline(users.require(auth), id);
            flash.addFlashAttribute("notice","Invitation declined.");
        } catch (ClanRuleViolation e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        //System.out.println("decline " + id);
        return "redirect:/invitations";
    }

    @PostMapping("/invitations/{id}/revoke")
    public String revoke(@PathVariable Long id, Authentication auth, RedirectAttributes flash) {
        try {
            invitations.revoke(users.require(auth), id);
            flash.addFlashAttribute("notice","Invitation revoked.");
        } catch (ClanRuleViolation e) {
            flash.addFlashAttribute("error",e.getMessage());
        }
        return "redirect:/invitations";
    }
}
