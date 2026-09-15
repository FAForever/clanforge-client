package com.faforever.clan_management.web;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.HttpStatus;

import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.service.AppUserService;
import com.faforever.clan_management.service.ClanCard;
import com.faforever.clan_management.service.ClanRuleViolation;
import com.faforever.clan_management.service.ClanService;
import com.faforever.clan_management.service.MemberCard;
import com.faforever.clan_management.service.MembershipService;
import com.faforever.clan_management.service.SignedInUser;

@Controller
public class ClanController {

    private static final int PAGE_SIZE = 20;
    // Cap the page index so a crafted ?page=100000000 cannot ask the database
    // for an enormous offset.
    private static final int MAX_PAGE = 10_000;

    private final ClanService clanService;
    private final MembershipService membershipService;
    private final AppUserService userService;

    public ClanController(ClanService clanService, MembershipService membershipService, AppUserService userService) {
        this.clanService = clanService;
        this.membershipService = membershipService;
        this.userService = userService;
    }

    @GetMapping("/clans")
    public String listClans(@RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "") String q,
                            @RequestParam(defaultValue = "all") String recruiting,
                            @RequestParam(defaultValue = "name") String sort,
                            Model model) {
        int safePage = Math.min(Math.max(page, 0), MAX_PAGE);
        // The members sort is written into its query, so the Pageable carries no
        // sort of its own for that case.
        Pageable pageable = "members".equals(sort)
                ? PageRequest.of(safePage, PAGE_SIZE)
                : PageRequest.of(safePage, PAGE_SIZE, Sort.by("name").ascending());

        Page<ClanCard> clans = clanService.searchPublicClanCards(q, recruiting, sort, pageable);

        model.addAttribute("clans", clans.getContent());
        model.addAttribute("currentPage", clans.getNumber());
        model.addAttribute("totalPages", clans.getTotalPages());
        model.addAttribute("totalClans", clans.getTotalElements());
        model.addAttribute("hasPrevious", clans.hasPrevious());
        model.addAttribute("hasNext", clans.hasNext());
        model.addAttribute("q", q);
        model.addAttribute("recruiting", recruiting);
        model.addAttribute("sort", sort);
        model.addAttribute("statClans", clanService.countPublicClans());
        model.addAttribute("statCommanders", clanService.countCommanders());
        model.addAttribute("statRecruiting", clanService.countRecruitingClans());
        return "clans/list";
    }

    @GetMapping("/clans/{id}")
    public String showClan(@PathVariable Long id, Authentication auth, Model model) {
        Clan clan = clanService.findVisibleClan(id)
                // 404 for private clans as well as missing ones - see ClanService
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        List<MemberCard> roster = membershipService.roster(clan);
        Optional<SignedInUser> me = userService.signedIn(auth);
        // Join is offered only to someone signed in, in no clan, looking at a
        // clan that takes members without an invitation.
        boolean canJoin = me.isPresent() && me.get().getMembership() == null
                && !clan.isRequiresInvitation() && "ACTIVE".equals(clan.getStatus());

        model.addAttribute("clan", clan);
        model.addAttribute("roster", roster);
        model.addAttribute("memberCount", roster.size());
        model.addAttribute("canJoin", canJoin);
        model.addAttribute("signedIn", me.isPresent());
        return "clans/detail";
    }

    @PostMapping("/clans/{id}/join")
    public String join(@PathVariable Long id, Authentication auth, RedirectAttributes flash) {
        try {
            membershipService.join(id, userService.require(auth));
            flash.addFlashAttribute("notice", "You have joined the clan.");
            return "redirect:/clanhome";
        } catch (ClanRuleViolation e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/clans/" + id;
        }
    }
}
