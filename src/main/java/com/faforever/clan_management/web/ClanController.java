package com.faforever.clan_management.web;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.service.ClanCard;
import com.faforever.clan_management.service.ClanService;

@Controller
public class ClanController {

    private static final int PAGE_SIZE = 20;
    // Cap the page index so a crafted ?page=100000000 cannot ask the database
    // for an enormous offset.
    private static final int MAX_PAGE = 10_000;

    private final ClanService clanService;

    public ClanController(ClanService clanService) {
        this.clanService = clanService;
    }

    @GetMapping("/clans")
    public String listClans(@RequestParam(defaultValue = "0") int page, Model model) {
        int safePage = Math.min(Math.max(page, 0), MAX_PAGE);
        Pageable pageable = PageRequest.of(safePage, PAGE_SIZE, Sort.by("name").ascending());

        Page<ClanCard> clans = clanService.findPublicClanCards(pageable);

        model.addAttribute("clans", clans.getContent());
        model.addAttribute("currentPage", clans.getNumber());
        model.addAttribute("totalPages", clans.getTotalPages());
        model.addAttribute("totalClans", clans.getTotalElements());
        model.addAttribute("hasPrevious", clans.hasPrevious());
        model.addAttribute("hasNext", clans.hasNext());
        model.addAttribute("statClans", clanService.countPublicClans());
        model.addAttribute("statCommanders", clanService.countCommanders());
        model.addAttribute("statRecruiting", clanService.countRecruitingClans());
        return "clans/list";
    }

    @GetMapping("/clans/{id}")
    public String showClan(@PathVariable Long id, Model model) {
        Clan clan = clanService.findVisibleClan(id)
                // 404 for private clans as well as missing ones - see ClanService
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        model.addAttribute("clan", clan);
        return "clans/detail";
    }
}
