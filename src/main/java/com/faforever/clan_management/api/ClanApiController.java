package com.faforever.clan_management.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.service.ClanCard;
import com.faforever.clan_management.service.ClanService;
import com.faforever.clan_management.service.MembershipService;

@RestController
@RequestMapping("/api/clans")
public class ClanApiController {

    private static final int PAGE_SIZE = 20;
    private static final int MAX_PAGE = 10_000;
    private final ClanService clanService;
    private final MembershipService membershipService;

    public ClanApiController(
            ClanService clanService,
            MembershipService membershipService
    ) {
        this.clanService = clanService;
        this.membershipService = membershipService;
    }

    @GetMapping
    public ClanPageResponse getClans(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "all") String recruiting,
            @RequestParam(defaultValue = "name") String sort
    ) {
        int safePage = Math.min(Math.max(page, 0), MAX_PAGE);

        Pageable pageable = "members".equals(sort)
                ? PageRequest.of(safePage, PAGE_SIZE)
                : PageRequest.of(
                        safePage,
                        PAGE_SIZE,
                        Sort.by("name").ascending()
                );

        Page<ClanCard> clans = clanService.searchPublicClanCards(
                q,
                recruiting,
                sort,
                pageable
        );

        return ClanPageResponse.from(clans);
    }

    @GetMapping("/{id}")
    public ClanDetailResponse getClan(@PathVariable Long id) {
        Clan clan = clanService.findVisibleClan(id)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND));

        return ClanDetailResponse.from(
                clan,
                membershipService.roster(clan)
        );
    }
}