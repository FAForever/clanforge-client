package com.faforever.clan_management.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.faforever.clan_management.entity.ClanReport;

public interface ClanReportRepository extends JpaRepository<ClanReport, Long> {

    boolean existsByClanIdAndReporterIdAndState(Long clanId, Long reporterId, String state);

    //newest first, with the clan and the reporter loaded since the caller reads both after the query
    @Query(value = "select r from ClanReport r join fetch r.clan join fetch r.reporter where r.state = :state order by r.createdAt desc",
           countQuery = "select count(r) from ClanReport r where r.state = :state")
    Page<ClanReport> findByStateNewestFirst(@Param("state") String state, Pageable pageable);

    @Query(value = "select r from ClanReport r join fetch r.clan join fetch r.reporter order by r.createdAt desc",
           countQuery = "select count(r) from ClanReport r")
    Page<ClanReport> findAllNewestFirst(Pageable pageable);
}
