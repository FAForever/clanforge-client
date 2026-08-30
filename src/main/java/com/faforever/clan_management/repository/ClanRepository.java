package com.faforever.clan_management.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.faforever.clan_management.entity.Clan;

public interface ClanRepository extends JpaRepository<Clan, Long> {
}