package com.faforever.clan_management.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.faforever.clan_management.entity.ModerationAction;

public interface ModerationActionRepository extends JpaRepository<ModerationAction, Long> {

    // everything an administrator has done to one clan, latest first
    List<ModerationAction> findByClanIdOrderByPerformedAtDesc(Long clanId);
}
