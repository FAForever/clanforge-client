package com.faforever.clan_management.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.faforever.clan_management.entity.Clan;

public interface ClanRepository extends JpaRepository<Clan, Long> {

    // Filtering happens in the query, not after paging, so page counts and
    // offsets stay correct once private or deleted clans are excluded.
    Page<Clan> findByVisibilityAndDeletedAtIsNull(String visibility, Pageable pageable);

    Optional<Clan> findByIdAndVisibilityAndDeletedAtIsNull(Long id, String visibility);

    long countByVisibilityAndDeletedAtIsNull(String visibility);

    long countByVisibilityAndRequiresInvitationFalseAndDeletedAtIsNull(String visibility);

    Optional<Clan> findByIdAndDeletedAtIsNull(Long id);

    // Live rows only, so a deleted clan's name or tag can be used again, which
    // is exactly what the partial unique indexes in V1 allow.
    Optional<Clan> findByNameIgnoreCaseAndDeletedAtIsNull(String name);

    Optional<Clan> findByTagIgnoreCaseAndDeletedAtIsNull(String tag);

    // Search by name or tag. The pattern arrives lower-cased and wildcarded from
    // the service, with ! as the escape so a typed % or _ matches itself. A null
    // requiresInvitation means both open and invite-only clans.
    @Query(value = """
           select c from Clan c
           where c.visibility = :visibility and c.deletedAt is null
             and (lower(c.name) like :pattern escape '!' or lower(c.tag) like :pattern escape '!')
             and (:requiresInvitation is null or c.requiresInvitation = :requiresInvitation)
           """,
           countQuery = """
           select count(c) from Clan c
           where c.visibility = :visibility and c.deletedAt is null
             and (lower(c.name) like :pattern escape '!' or lower(c.tag) like :pattern escape '!')
             and (:requiresInvitation is null or c.requiresInvitation = :requiresInvitation)
           """)
    Page<Clan> searchVisible(@Param("visibility") String visibility, @Param("pattern") String pattern,
                             @Param("requiresInvitation") Boolean requiresInvitation, Pageable pageable);

    // The same search ordered by active member count, which a Sort on a
    // property cannot express. Pass an unsorted Pageable.
    @Query(value = """
           select c from Clan c
           where c.visibility = :visibility and c.deletedAt is null
             and (lower(c.name) like :pattern escape '!' or lower(c.tag) like :pattern escape '!')
             and (:requiresInvitation is null or c.requiresInvitation = :requiresInvitation)
           order by (select count(m.id) from ClanMembership m where m.clan = c and m.leftAt is null) desc, lower(c.name) asc
           """,
           countQuery = """
           select count(c) from Clan c
           where c.visibility = :visibility and c.deletedAt is null
             and (lower(c.name) like :pattern escape '!' or lower(c.tag) like :pattern escape '!')
             and (:requiresInvitation is null or c.requiresInvitation = :requiresInvitation)
           """)
    Page<Clan> searchVisibleByMembers(@Param("visibility") String visibility, @Param("pattern") String pattern,
                                      @Param("requiresInvitation") Boolean requiresInvitation, Pageable pageable);
}
