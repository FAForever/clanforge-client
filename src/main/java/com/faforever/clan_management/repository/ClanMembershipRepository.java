package com.faforever.clan_management.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.faforever.clan_management.entity.ClanMembership;

public interface ClanMembershipRepository extends JpaRepository<ClanMembership, Long> {
}