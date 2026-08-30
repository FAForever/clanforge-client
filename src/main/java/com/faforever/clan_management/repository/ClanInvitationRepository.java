package com.faforever.clan_management.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.faforever.clan_management.entity.ClanInvitation;

public interface ClanInvitationRepository extends JpaRepository<ClanInvitation, Long> {
}