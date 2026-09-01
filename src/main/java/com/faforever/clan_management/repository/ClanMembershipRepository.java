package com.faforever.clan_management.repository;

import java.util.Collection;
import java.util.List;

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
}
