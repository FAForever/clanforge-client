package com.faforever.clan_management.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.faforever.clan_management.entity.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
}