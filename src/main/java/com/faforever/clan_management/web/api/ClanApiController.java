package com.faforever.clan_management.web.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.service.ClanService;
import com.faforever.clan_management.service.MembershipService;

@RestController
@RequestMapping("/api/clans")
public class ClanApiController {

    private final ClanService clanService;
    private final MembershipService membershipService;

    public ClanApiController(
            ClanService clanService,
            MembershipService membershipService
    ) {
        this.clanService = clanService;
        this.membershipService = membershipService;
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