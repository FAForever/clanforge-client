package com.faforever.clan_management.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.faforever.clan_management.entity.ModerationAction;

public interface ModerationActionRepository extends JpaRepository<ModerationAction, Long> {
}