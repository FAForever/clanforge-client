package com.faforever.clan_management.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.faforever.clan_management.entity.Clan;

public interface ClanRepository extends JpaRepository<Clan, Long> {

    // Filtering happens in the query, not after paging, so page counts and
    // offsets stay correct once private or deleted clans are excluded.
    Page<Clan> findByVisibilityAndDeletedAtIsNull(String visibility, Pageable pageable);

    Optional<Clan> findByIdAndVisibilityAndDeletedAtIsNull(Long id, String visibility);

    long countByVisibilityAndDeletedAtIsNull(String visibility);

    long countByVisibilityAndRequiresInvitationFalseAndDeletedAtIsNull(String visibility);
}
