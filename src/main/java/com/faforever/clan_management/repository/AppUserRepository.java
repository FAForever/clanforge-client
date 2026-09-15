package com.faforever.clan_management.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.faforever.clan_management.entity.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByOidcSubject(String oidcSubject);

    Optional<AppUser> findByDisplayNameIgnoreCase(String displayName);
}
