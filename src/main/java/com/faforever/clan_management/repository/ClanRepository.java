package com.faforever.clan_management.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.faforever.clan_management.entity.Clan;

import jakarta.persistence.LockModeType;

public interface ClanRepository extends JpaRepository<Clan, Long> {

    // Filtering happens in the query, not after paging, so page counts and
    // offsets stay correct once private or deleted clans are excluded.
    Page<Clan> findByVisibilityAndDeletedAtIsNull(String visibility, Pageable pageable);

    Optional<Clan> findByIdAndVisibilityAndDeletedAtIsNull(Long id, String visibility);

    long countByVisibilityAndDeletedAtIsNull(String visibility);

    long countByVisibilityAndRequiresInvitationFalseAndDeletedAtIsNull(String visibility);

    // The public-facing views hide a suspended clan the same way they hide a deleted one, so
    // the detail page and the browse stats take the status too. The browse search itself does
    // the same filter inside searchVisible / searchVisibleByMembers below.
    Optional<Clan> findByIdAndVisibilityAndStatusAndDeletedAtIsNull(Long id, String visibility, String status);

    long countByVisibilityAndStatusAndDeletedAtIsNull(String visibility, String status);

    long countByVisibilityAndRequiresInvitationFalseAndStatusAndDeletedAtIsNull(String visibility, String status);

    // the admin console's figures: live clans by status, and the deleted ones
    long countByStatusAndDeletedAtIsNull(String status);

    long countByDeletedAtIsNotNull();

    Optional<Clan> findByIdAndDeletedAtIsNull(Long id);

    // Yes or no questions about a live clan that load no entity, so a request
    // that will be refused is refused before it waits on the clan's lock, and
    // the clan the lock then loads is read fresh.
    boolean existsByIdAndDeletedAtIsNull(Long id);

    boolean existsByIdAndVisibilityAndDeletedAtIsNull(Long id,String visibility);

    boolean existsByIdAndVisibilityAndRequiresInvitationFalseAndDeletedAtIsNull(Long id, String visibility);

    boolean existsByIdAndVisibilityAndStatusAndRequiresInvitationFalseAndDeletedAtIsNull(Long id, String visibility, String status);

    // The same live clan, locked until the transaction ends, so two
    // administrators acting on one clan at once take turns instead of each
    // checking the status the other is about to change.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Clan c where c.id = :id and c.deletedAt is null")
    Optional<Clan> lockLive(@Param("id") Long id);

    // Live rows only, so a deleted clan's name or tag can be used again, which
    // is exactly what the partial unique indexes in V1 allow.
    Optional<Clan> findByNameIgnoreCaseAndDeletedAtIsNull(String name);

    Optional<Clan> findByTagIgnoreCaseAndDeletedAtIsNull(String tag);

    // Search by name or tag. The pattern arrives lower-cased and wildcarded from
    // the service, with ! as the escape so a typed % or _ matches itself. A null
    // requiresInvitation means both open and invite-only clans.
    // owner is join-fetched here even though the card does not show it: owner is an
    // eager @ManyToOne, so without the fetch a page of clans issues one owner lookup
    // per row (an N+1). Keep the join fetch.
    @Query(value = """
           select c from Clan c join fetch c.owner
           where c.visibility = :visibility and c.deletedAt is null and c.status = 'ACTIVE'
             and (lower(c.name) like :pattern escape '!' or lower(c.tag) like :pattern escape '!')
             and (:requiresInvitation is null or c.requiresInvitation = :requiresInvitation)
           """,
           countQuery = """
           select count(c) from Clan c
           where c.visibility = :visibility and c.deletedAt is null and c.status = 'ACTIVE'
             and (lower(c.name) like :pattern escape '!' or lower(c.tag) like :pattern escape '!')
             and (:requiresInvitation is null or c.requiresInvitation = :requiresInvitation)
           """)
    Page<Clan> searchVisible(@Param("visibility") String visibility, @Param("pattern") String pattern,
                             @Param("requiresInvitation") Boolean requiresInvitation, Pageable pageable);

    // The same search ordered by active member count, which a Sort on a
    // property cannot express. Pass an unsorted Pageable.
    @Query(value = """
           select c from Clan c join fetch c.owner
           where c.visibility = :visibility and c.deletedAt is null and c.status = 'ACTIVE'
             and (lower(c.name) like :pattern escape '!' or lower(c.tag) like :pattern escape '!')
             and (:requiresInvitation is null or c.requiresInvitation = :requiresInvitation)
           order by (select count(m.id) from ClanMembership m where m.clan = c and m.leftAt is null) desc, lower(c.name) asc
           """,
           countQuery = """
           select count(c) from Clan c
           where c.visibility = :visibility and c.deletedAt is null and c.status = 'ACTIVE'
             and (lower(c.name) like :pattern escape '!' or lower(c.tag) like :pattern escape '!')
             and (:requiresInvitation is null or c.requiresInvitation = :requiresInvitation)
           """)
    Page<Clan> searchVisibleByMembers(@Param("visibility") String visibility, @Param("pattern") String pattern,
                                      @Param("requiresInvitation") Boolean requiresInvitation, Pageable pageable);

    // The administration listing. Unlike the searches above these ignore
    // visibility, so private clans are included, and the owner is fetched in the
    // same query because every row of that listing shows it. Pass an unsorted
    // Pageable: the order is part of each query.
    @Query(value = """
           select c from Clan c join fetch c.owner
           where (lower(c.name) like :pattern escape '!' or lower(c.tag) like :pattern escape '!')
           order by lower(c.name), c.id
           """,
           countQuery = """
           select count(c) from Clan c
           where (lower(c.name) like :pattern escape '!' or lower(c.tag) like :pattern escape '!')
           """)
    Page<Clan> searchEvery(@Param("pattern") String pattern, Pageable pageable);

    @Query(value = """
           select c from Clan c join fetch c.owner
           where c.deletedAt is null and c.status = :status
             and (lower(c.name) like :pattern escape '!' or lower(c.tag) like :pattern escape '!')
           order by lower(c.name), c.id
           """,
           countQuery = """
           select count(c) from Clan c
           where c.deletedAt is null and c.status = :status
             and (lower(c.name) like :pattern escape '!' or lower(c.tag) like :pattern escape '!')
           """)
    Page<Clan> searchLiveByStatus(@Param("pattern") String pattern, @Param("status") String status, Pageable pageable);

    @Query(value = """
           select c from Clan c join fetch c.owner
           where c.deletedAt is not null
             and (lower(c.name) like :pattern escape '!' or lower(c.tag) like :pattern escape '!')
           order by lower(c.name), c.id
           """,
           countQuery = """
           select count(c) from Clan c
           where c.deletedAt is not null
             and (lower(c.name) like :pattern escape '!' or lower(c.tag) like :pattern escape '!')
           """)
    Page<Clan> searchDeleted(@Param("pattern") String pattern, Pageable pageable);
}
