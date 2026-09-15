package com.faforever.clan_management.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.faforever.clan_management.entity.ClanInvitation;

public interface ClanInvitationRepository extends JpaRepository<ClanInvitation, Long> {

    //open means pending and not yet past its expiry, the view clan_invitation_effective draws the same line
    @Query("""
           select i from ClanInvitation i join fetch i.clan join fetch i.inviter
           where i.invitee.id = :userId and i.state = 'PENDING' and i.expiresAt > :now
           order by i.createdAt desc
           """)
    List<ClanInvitation> findOpenForInvitee(@Param("userId") Long userId, @Param("now") OffsetDateTime now);

    @Query("select count(i) from ClanInvitation i where i.invitee.id = :userId and i.state = 'PENDING' and i.expiresAt > :now")
    long countOpenForInvitee(@Param("userId") Long userId, @Param("now") OffsetDateTime now);

    @Query("""
           select i from ClanInvitation i join fetch i.invitee join fetch i.inviter
           where i.clan.id = :clanId and i.state = 'PENDING' and i.expiresAt > :now
           order by i.createdAt desc
           """)
    List<ClanInvitation> findOpenForClan(@Param("clanId") Long clanId, @Param("now") OffsetDateTime now);

    List<ClanInvitation> findByClanIdAndState(Long clanId, String state);

    Optional<ClanInvitation> findByClanIdAndInviteeIdAndState(Long clanId, Long inviteeId, String state);
}
