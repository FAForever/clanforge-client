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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/clans")
@Tag(name = "Clans", description = "Browse clans and manage clan membership and settings.")
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
    @Operation(summary = "List public clans")
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
    @Operation(summary = "Get a public clan")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Clan found"),
            @ApiResponse(responseCode = "404", description = "Clan is private, deleted or missing")
    })
    public ClanDetailResponse getClan(@PathVariable Long id) {
        Clan clan = visibleClan(id);

        return ClanDetailResponse.from(
                clan,
                membershipService.roster(clan)
        );
    }

    @GetMapping("/{id}/members")
    @Operation(summary = "List members of a public clan")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Members returned"),
            @ApiResponse(responseCode = "404", description = "Clan is private, deleted or missing")
    })
    public List<MemberResponse> getMembers(@PathVariable Long id) {
        return membershipService.roster(visibleClan(id)).stream()
                .map(MemberResponse::from)
                .toList();
    }

    @PostMapping("/{id}/join")
    @Operation(
            summary = "Join a clan",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Membership created"),
            @ApiResponse(responseCode = "400", description = "Membership rule rejected the request"),
            @ApiResponse(responseCode = "401", description = "Bearer token is missing or invalid"),
            @ApiResponse(responseCode = "404", description = "Clan is private, deleted or missing")
    })
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
    @Operation(
            summary = "Leave a clan",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Membership ended"),
            @ApiResponse(responseCode = "400", description = "Membership rule rejected the request"),
            @ApiResponse(responseCode = "401", description = "Bearer token is missing or invalid"),
            @ApiResponse(responseCode = "403", description = "The caller cannot leave this clan"),
            @ApiResponse(responseCode = "404", description = "Clan is deleted or missing")
    })
    public ResponseEntity<Void> leaveClan(
            @PathVariable Long id,
            Authentication authentication
    ) {
        membershipService.leave(id, appUserService.require(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    @Operation(
            summary = "Create a clan",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Clan created"),
            @ApiResponse(responseCode = "400", description = "Clan data breaks a validation rule"),
            @ApiResponse(responseCode = "401", description = "Bearer token is missing or invalid")
    })
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
    @Operation(
            summary = "Update the caller's clan",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Clan updated"),
            @ApiResponse(responseCode = "400", description = "Clan data breaks a validation rule"),
            @ApiResponse(responseCode = "401", description = "Bearer token is missing or invalid"),
            @ApiResponse(responseCode = "403", description = "The caller cannot manage this clan"),
            @ApiResponse(responseCode = "404", description = "Clan is deleted or missing")
    })
    public ClanDetailResponse updateClan(
            @PathVariable Long id,
            @RequestBody ClanWriteRequest request,
            Authentication authentication
    ) {
        AppUser actor = appUserService.require(authentication);
        Clan clan = clanManagementService.patch(actor, id, request.toPatch());
        return ClanDetailResponse.from(clan, membershipService.roster(clan));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete the caller's clan",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Clan deleted"),
            @ApiResponse(responseCode = "401", description = "Bearer token is missing or invalid"),
            @ApiResponse(responseCode = "403", description = "The caller cannot manage this clan"),
            @ApiResponse(responseCode = "404", description = "Clan is deleted or missing")
    })
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
