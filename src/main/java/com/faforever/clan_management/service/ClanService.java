package com.faforever.clan_management.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.repository.ClanMembershipRepository;
import com.faforever.clan_management.repository.ClanMembershipRepository.ClanMemberCount;
import com.faforever.clan_management.repository.ClanRepository;

/**
 * Read access to clans for anonymous and signed-in users.
 *
 * Visibility is enforced here rather than in the controller, so every route that
 * reaches clan data goes through the same rule.
 */
@Service
public class ClanService {

    static final String PUBLIC = "PUBLIC";
    static final String ACTIVE = "ACTIVE"; // the public views show active clans only, never a suspended one

    private final ClanRepository clanRepository;
    private final ClanMembershipRepository membershipRepository;

    public ClanService(ClanRepository clanRepository, ClanMembershipRepository membershipRepository) {
        this.clanRepository = clanRepository;
        this.membershipRepository = membershipRepository;
    }

    public Page<Clan> findPublicClans(Pageable pageable) {
        return clanRepository.findByVisibilityAndDeletedAtIsNull(PUBLIC, pageable);
    }

    /**
     * A page of public clans with their active member counts.
     *
     * Two queries regardless of page size: one for the clans, one for the counts
     * of exactly those clans.
     */
    public Page<ClanCard> findPublicClanCards(Pageable pageable) {
        return withCounts(findPublicClans(pageable));
    }

    /**
     * The browse page's search, run in the database rather than over the one
     * page the browser holds. q matches name or tag, case-insensitive;
     * recruiting is "open", "closed" or anything else for both; sort is
     * "members" for most members first, otherwise the Pageable's own sort.
     */
    public Page<ClanCard> searchPublicClanCards(String q, String recruiting, String sort, Pageable pageable) {
        String pattern = "%" + escapeLike(q == null ? "" : q.trim().toLowerCase(Locale.ROOT)) + "%";
        Boolean requiresInvitation = "open".equals(recruiting) ? Boolean.FALSE
                : "closed".equals(recruiting) ? Boolean.TRUE
                : null;
        Page<Clan> clans = "members".equals(sort)
                ? clanRepository.searchVisibleByMembers(PUBLIC, pattern, requiresInvitation, pageable)
                : clanRepository.searchVisible(PUBLIC, pattern, requiresInvitation, pageable);
        return withCounts(clans);
    }

    Page<ClanCard> withCounts(Page<Clan> clans) {
        List<Long> ids = clans.getContent().stream().map(Clan::getId).toList();
        Map<Long, Long> counts = ids.isEmpty()
                ? Map.of()
                : membershipRepository.countActiveMembersByClanIds(ids).stream()
                    .collect(Collectors.toMap(ClanMemberCount::getClanId,
                                              ClanMemberCount::getMemberCount));

        return clans.map(clan -> new ClanCard(clan, counts.getOrDefault(clan.getId(), 0L)));
    }

    // A typed % or _ would otherwise act as a wildcard. ! is the escape
    // character the repository queries declare.
    static String escapeLike(String s) {
        return s.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    /**
     * Look up a clan that the caller is allowed to see.
     *
     * Returns empty for a private clan as well as for one that does not exist,
     * so the two are indistinguishable from outside. Answering 403 for private
     * and 404 for missing would let anyone walk /clans/1, /clans/2, ... and map
     * exactly which private clans exist and how many there are. Hiding
     * existence is the deliberate choice here, the same reason a private
     * repository on GitHub returns 404 rather than 403.
     */
    public Optional<Clan> findVisibleClan(Long id) {
        return clanRepository.findByIdAndVisibilityAndStatusAndDeletedAtIsNull(id, PUBLIC, ACTIVE);
    }

    public long countPublicClans() {
        return clanRepository.countByVisibilityAndStatusAndDeletedAtIsNull(PUBLIC, ACTIVE);
    }

    public long countRecruitingClans() {
        return clanRepository.countByVisibilityAndRequiresInvitationFalseAndStatusAndDeletedAtIsNull(PUBLIC, ACTIVE);
    }

    public long countCommanders() {
        // players currently in a clan (active memberships), not every membership row ever —
        // memberships are soft-closed (left_at set) on leave/remove, so count() would never go down
        return membershipRepository.countByLeftAtIsNull();
    }

    public long countActiveMembers(Long clanId) {
        return membershipRepository.countActiveMembersByClanIds(List.of(clanId)).stream()
                .findFirst()
                .map(ClanMemberCount::getMemberCount)
                .orElse(0L);
    }
}
