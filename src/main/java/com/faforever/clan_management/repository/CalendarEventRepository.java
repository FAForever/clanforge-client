package com.faforever.clan_management.repository;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.faforever.clan_management.entity.CalendarEvent;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

    //soonest first, from the start of today so an event later today still shows
    @Query("select e from CalendarEvent e where e.clan.id = :clanId and e.startsAt >= :from order by e.startsAt")
    List<CalendarEvent> findUpcoming(@Param("clanId") Long clanId, @Param("from") OffsetDateTime from, Pageable pageable);
}
