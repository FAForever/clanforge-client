package com.faforever.clan_management.api;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.entity.ClanMembership;
import com.faforever.clan_management.service.AppUserService;
import com.faforever.clan_management.service.ClanCard;
import com.faforever.clan_management.service.ClanManagementService;
import com.faforever.clan_management.service.ClanService;
import com.faforever.clan_management.service.MembershipService;

@RestController
@RequestMapping("/api/clans")
public class ClanApiController {

    private static final int PAGE_SIZE = 20;
    private static final int MAX_PAGE = 10_000;
    private final ClanService clanService;
    private final MembershipService membershipService;
    private final ClanManagementService clanManagementService;
    private final AppUserService appUserService;

    public ClanApiController(
            ClanService clanService,
            MembershipService membershipService,
            ClanManagementService clanManagementService,
            AppUserService appUserService
    ) {
        this.clanService = clanService;
        this.membershipService = membershipService;
        this.clanManagementService = clanManagementService;
        this.appUserService = appUserService;
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
        Clan clan = visibleClan(id);

        return ClanDetailResponse.from(
                clan,
                membershipService.roster(clan)
        );
    }

    @GetMapping("/{id}/members")
    public List<MemberResponse> getMembers(@PathVariable Long id) {
        return membershipService.roster(visibleClan(id)).stream()
                .map(MemberResponse::from)
                .toList();
    }

    @PostMapping("/{id}/join")
    public MembershipResponse joinClan(
            @PathVariable Long id,
            Authentication authentication
    ) {
        ClanMembership membership = membershipService.join(
                id,
                appUserService.require(authentication)
        );
        return MembershipResponse.from(membership);
    }

    @PostMapping("/{id}/leave")
    public ResponseEntity<Void> leaveClan(
            @PathVariable Long id,
            Authentication authentication
    ) {
        membershipService.leave(id, appUserService.require(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<ClanDetailResponse> createClan(
            @RequestBody ClanWriteRequest request,
            Authentication authentication
    ) {
        AppUser actor = appUserService.require(authentication);
        Clan clan = clanManagementService.create(actor, request.toForm());
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ClanDetailResponse.from(clan, membershipService.roster(clan))
        );
    }

    @PatchMapping("/{id}")
    public ClanDetailResponse updateClan(
            @PathVariable Long id,
            @RequestBody ClanWriteRequest request,
            Authentication authentication
    ) {
        AppUser actor = appUserService.require(authentication);
        Clan clan = clanManagementService.update(actor, id, request.toForm());
        return ClanDetailResponse.from(clan, membershipService.roster(clan));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteClan(
            @PathVariable Long id,
            Authentication authentication
    ) {
        clanManagementService.delete(appUserService.require(authentication), id);
        return ResponseEntity.noContent().build();
    }

    private Clan visibleClan(Long id) {
        return clanService.findVisibleClan(id)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
