package com.faforever.clan_management.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.faforever.clan_management.entity.ClanEvent;

public interface ClanEventRepository extends JpaRepository<ClanEvent, Long> {

    //the latest happenings in one clan for the bell, membership and invitation rows carry the clan id in their payload
    @Query(value = """
           select * from clan_event e
           where (e.entity_type = 'clan' and e.entity_id = :clanId)
              or (e.entity_type in ('clan_membership', 'clan_invitation', 'clan_calendar_event')
                  and cast(e.payload ->> 'clanId' as bigint) = :clanId)
           order by e.occurred_at desc
           limit :limit
           """, nativeQuery = true)
    List<ClanEvent> findLatestForClan(@Param("clanId") Long clanId, @Param("limit") int limit);
}
