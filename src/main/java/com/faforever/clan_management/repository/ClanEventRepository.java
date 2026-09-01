package com.faforever.clan_management.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.faforever.clan_management.entity.ClanEvent;

public interface ClanEventRepository extends JpaRepository<ClanEvent, Long> {
}