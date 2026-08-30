package com.faforever.clan_management.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.faforever.clan_management.entity.ClanReport;

public interface ClanReportRepository extends JpaRepository<ClanReport, Long> {
}