package com.faforever.clan_management.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.faforever.clan_management.entity.ClanMembership;

public interface ClanMembershipRepository extends JpaRepository<ClanMembership, Long> {

    /**
     * Active member counts for a set of clans, in one query.
     *
     * Counting per clan while rendering a page of results would issue one query
     * per card; this fetches the whole page's counts at once.
     */
    @Query("""
           select m.clan.id as clanId, count(m.id) as memberCount
           from ClanMembership m
           where m.clan.id in :ids and m.leftAt is null
           group by m.clan.id
           """)
    List<ClanMemberCount> countActiveMembersByClanIds(@Param("ids") Collection<Long> ids);

    interface ClanMemberCount {
        Long getClanId();
        long getMemberCount();
    }

    /**
     * The one membership a user currently holds, with its clan loaded, since
     * open-in-view is off and the caller will read the clan after the query.
     */
    @Query("select m from ClanMembership m join fetch m.clan where m.user.id = :userId and m.leftAt is null")
    Optional<ClanMembership> findActiveByUserId(@Param("userId") Long userId);

    @Query("select m from ClanMembership m join fetch m.user where m.clan.id = :clanId and m.user.id = :userId and m.leftAt is null")
    Optional<ClanMembership> findActiveByClanIdAndUserId(@Param("clanId") Long clanId, @Param("userId") Long userId);

    /**
     * Everyone currently in a clan, owner first, then moderators, then members
     * in the order they joined.
     */
    @Query("""
           select m from ClanMembership m join fetch m.user
           where m.clan.id = :clanId and m.leftAt is null
           order by case m.role when 'OWNER' then 0 when 'MODERATOR' then 1 else 2 end, m.joinedAt
           """)
    List<ClanMembership> findRoster(@Param("clanId") Long clanId);
}
