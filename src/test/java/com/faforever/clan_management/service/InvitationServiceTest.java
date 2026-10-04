package com.faforever.clan_management.service;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.entity.ClanInvitation;
import com.faforever.clan_management.entity.ClanMembership;
import com.faforever.clan_management.repository.AppUserRepository;
import com.faforever.clan_management.repository.ClanInvitationRepository;
import com.faforever.clan_management.repository.ClanMembershipRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

//the invitation rules from both ends, the clan that sends and the person who answers
@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    @Mock
    private ClanInvitationRepository invitations;
    @Mock
    private ClanMembershipRepository memberships;
    @Mock
    private AppUserRepository users;
    @Mock
    private MembershipService membershipService;
    @Mock
    private ClanEventService events;

    private InvitationService service;
    private final AppUser kai = Fixtures.user(1L, "kai");
    private final AppUser sam = Fixtures.user(2L, "sam");
    private final Clan clan = Fixtures.clan(7L, "Bad Company", "BC", kai, true);
    private final ClanMembership ownerRow = Fixtures.membership(5L, clan, kai, "OWNER");

    @BeforeEach
    void setUp() {
        service = new InvitationService(invitations, memberships, users, membershipService, events, 7);
    }

    @Test
    @DisplayName("a plain member cannot send invites")
    void inviteRefusedForMember() {
        given(membershipService.manager(eq(sam), anyString())).willThrow(new ClanAccessRefused("Only the owner and moderators can send invites."));
        assertThatThrownBy(() -> service.invite(sam, "lee", null))
                .isInstanceOf(ClanRuleViolation.class)
                .hasMessageContaining("Only the owner and moderators");
        verify(invitations, never()).save(any());
    }

    @Test
    @DisplayName("an unknown name is refused with the name echoed back")
    void inviteUnknownName() {
        given(membershipService.manager(eq(kai), anyString())).willReturn(ownerRow);
        given(users.findByDisplayNameIgnoreCase("nobody")).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.invite(kai, " nobody ", null))
                .isInstanceOf(ClanRuleViolation.class)
                .hasMessageContaining("No player named 'nobody'");
    }

    @Test
    @DisplayName("someone already in the clan is not invited again")
    void inviteRefusesExistingMember() {
        given(membershipService.manager(eq(kai), anyString())).willReturn(ownerRow);
        given(users.findByDisplayNameIgnoreCase("sam")).willReturn(Optional.of(sam));
        given(memberships.findActiveByClanIdAndUserId(7L,2L)).willReturn(Optional.of(Fixtures.membership(6L,clan,sam,"MEMBER")));
        assertThatThrownBy(() -> service.invite(kai, "sam", null))
                .isInstanceOf(ClanRuleViolation.class)
                .hasMessageContaining("already a member");
    }

    @Test
    @DisplayName("one open invite per person per clan")
    void inviteRefusesDuplicatePending() {
        given(membershipService.manager(eq(kai),anyString())).willReturn(ownerRow);
        given(users.findByDisplayNameIgnoreCase("sam")).willReturn(Optional.of(sam));
        given(memberships.findActiveByClanIdAndUserId(7L, 2L)).willReturn(Optional.empty());
        given(invitations.findByClanIdAndInviteeIdAndState(7L, 2L,"PENDING")).willReturn(Optional.of(Fixtures.invitation(8L, clan, sam, kai, OffsetDateTime.now().plusDays(1))));
        assertThatThrownBy(() -> service.invite(kai, "sam", null))
                .isInstanceOf(ClanRuleViolation.class)
                .hasMessageContaining("already has a pending invite");
    }

    @Test
    @DisplayName("a new invite expires after the configured days and writes InvitationCreated")
    void inviteSavesWithExpiryAndEvent() {
        given(membershipService.manager(eq(kai), anyString())).willReturn(ownerRow);
        given(users.findByDisplayNameIgnoreCase("sam")).willReturn(Optional.of(sam));
        given(memberships.findActiveByClanIdAndUserId(7L, 2L)).willReturn(Optional.empty());
        given(invitations.findByClanIdAndInviteeIdAndState(7L,2L,"PENDING")).willReturn(Optional.empty());
        given(invitations.save(any(ClanInvitation.class))).willAnswer(a -> a.getArgument(0));
        ClanInvitation inv = service.invite(kai, "sam", "MEMBER");
        //System.out.println("expires " + inv.getExpiresAt());
        assertThat(inv.getState()).isEqualTo("PENDING");
        assertThat(inv.getInvitee()).isSameAs(sam);
        assertThat(inv.getInviter()).isSameAs(kai);
        assertThat(inv.getExpiresAt()).isAfter(OffsetDateTime.now().plusDays(6).plusHours(23));
        assertThat(inv.getExpiresAt()).isBefore(OffsetDateTime.now().plusDays(7).plusHours(1));
        verify(events).record(eq("clan_invitation"), any(), eq("InvitationCreated"),any(), eq(kai));
    }

    @Test
    @DisplayName("only the invited person can answer")
    void acceptRefusesWrongPerson() {
        given(invitations.findById(8L)).willReturn(Optional.of(Fixtures.invitation(8L, clan, sam,kai, OffsetDateTime.now().plusDays(1))));
        assertThatThrownBy(() -> service.accept(Fixtures.user(3L, "lee"), 8L))
                .isInstanceOf(ClanRuleViolation.class)
                .isNotInstanceOf(ClanAccessRefused.class)
                .hasMessage("That invitation does not exist."); //the same answer as a missing one, so ids cannot be probed
        verify(memberships, never()).save(any());
    }

    @Test
    @DisplayName("an invite past its expiry cannot be accepted")
    void acceptRefusesExpired() {
        given(invitations.findById(8L)).willReturn(Optional.of(Fixtures.invitation(8L, clan, sam, kai, OffsetDateTime.now().minusMinutes(1))));
        assertThatThrownBy(() -> service.accept(sam, 8L))
                .isInstanceOf(ClanRuleViolation.class)
                .hasMessageContaining("expired");
        verify(memberships,never()).save(any());
    }

    @Test
    @DisplayName("accepting while in another clan is refused, leave first")
    void acceptRefusesWhenInAClan() {
        given(invitations.findById(8L)).willReturn(Optional.of(Fixtures.invitation(8L, clan, sam,kai,OffsetDateTime.now().plusDays(1))));
        given(memberships.findActiveByUserId(2L)).willReturn(Optional.of(Fixtures.membership(9L, Fixtures.clan(3L,"Other","OTH", sam, false), sam,"OWNER")));
        assertThatThrownBy(() -> service.accept(sam, 8L))
                .isInstanceOf(ClanRuleViolation.class)
                .hasMessageContaining("Leave your current clan");
    }

    @Test
    @DisplayName("accepting joins the clan and writes InvitationAccepted then MemberJoined")
    void acceptJoinsAndWritesTwoEvents() {
        ClanInvitation inv = Fixtures.invitation(8L,clan, sam, kai, OffsetDateTime.now().plusDays(1));
        given(invitations.findById(8L)).willReturn(Optional.of(inv));
        given(memberships.findActiveByUserId(2L)).willReturn(Optional.empty());
        given(memberships.save(any(ClanMembership.class))).willAnswer(a -> a.getArgument(0));
        ClanMembership m = service.accept(sam, 8L);
        assertThat(inv.getState()).isEqualTo("ACCEPTED");
        assertThat(inv.getRespondedAt()).isNotNull();
        assertThat(m.getRole()).isEqualTo("MEMBER");
        assertThat(m.getClan()).isSameAs(clan);
        verify(events).record(eq("clan_invitation"),eq(8L), eq("InvitationAccepted"), any(), eq(sam));
        verify(events).record(eq("clan_membership"), any(),eq("MemberJoined"), any(),eq(sam));
    }

    @Test
    @DisplayName("declining closes the invite without a membership")
    void declineMarksDeclined() {
        ClanInvitation inv = Fixtures.invitation(8L, clan,sam, kai,OffsetDateTime.now().plusDays(1));
        given(invitations.findById(8L)).willReturn(Optional.of(inv));
        service.decline(sam, 8L);
        //System.out.println("state " + inv.getState());
        assertThat(inv.getState()).isEqualTo("DECLINED");
        verify(memberships,never()).save(any());
        verify(events).record(eq("clan_invitation"), eq(8L), eq("InvitationDeclined"), any(), eq(sam));
    }

    @Test
    @DisplayName("a manager can only revoke their own clan's invites")
    void revokeRefusesOtherClan() {
        given(membershipService.manager(eq(kai),anyString())).willReturn(ownerRow);
        Clan other = Fixtures.clan(3L,"Other", "OTH",sam, false);
        given(invitations.findById(8L)).willReturn(Optional.of(Fixtures.invitation(8L, other, Fixtures.user(3L, "lee"), sam, OffsetDateTime.now().plusDays(1))));
        assertThatThrownBy(() -> service.revoke(kai,8L))
                .isInstanceOf(ClanRuleViolation.class)
                .isNotInstanceOf(ClanAccessRefused.class)
                .hasMessage("That invitation does not exist.");
    }

    @Test
    @DisplayName("revoking marks the invite revoked and records the actor")
    void revokeMarksRevoked() {
        given(membershipService.manager(eq(kai), anyString())).willReturn(ownerRow);
        ClanInvitation inv = Fixtures.invitation(8L, clan, sam, kai, OffsetDateTime.now().plusDays(1));
        given(invitations.findById(8L)).willReturn(Optional.of(inv));
        service.revoke(kai,8L);
        assertThat(inv.getState()).isEqualTo("REVOKED");
        ArgumentCaptor<String> type = ArgumentCaptor.forClass(String.class);
        verify(events).record(eq("clan_invitation"), eq(8L), type.capture(), any(), eq(kai));
        assertThat(type.getValue()).isEqualTo("InvitationRevoked");
    }

    @Test
    @DisplayName("only the owner can invite as moderator, a moderator hands out member")
    void moderatorRoleIsOwnerOnly() {
        given(membershipService.manager(eq(sam), anyString())).willReturn(Fixtures.membership(6L, clan, sam, "MODERATOR"));
        assertThatThrownBy(() -> service.invite(sam, "lee", "MODERATOR"))
                .isInstanceOf(ClanAccessRefused.class)
                .hasMessageContaining("Only the owner can invite as moderator");
        verify(invitations, never()).save(any());
    }

    @Test
    @DisplayName("accepting an invite that offered moderator makes a moderator")
    void acceptAppliesOfferedRole() {
        ClanInvitation inv = new ClanInvitation(clan, sam, kai, OffsetDateTime.now().plusDays(1), "MODERATOR");
        org.springframework.test.util.ReflectionTestUtils.setField(inv, "id", 8L);
        given(invitations.findById(8L)).willReturn(Optional.of(inv));
        given(memberships.findActiveByUserId(2L)).willReturn(Optional.empty());
        given(memberships.save(any(ClanMembership.class))).willAnswer(a -> a.getArgument(0));
        ClanMembership m = service.accept(sam, 8L);
        //System.out.println("role " + m.getRole());
        assertThat(m.getRole()).isEqualTo("MODERATOR");
    }

    @Test
    @DisplayName("a lapsed pending invite is marked expired and a fresh one goes out")
    void lapsedInviteIsReplaced() {
        given(membershipService.manager(eq(kai), anyString())).willReturn(ownerRow);
        given(users.findByDisplayNameIgnoreCase("sam")).willReturn(Optional.of(sam));
        given(memberships.findActiveByClanIdAndUserId(7L, 2L)).willReturn(Optional.empty());
        ClanInvitation lapsed = Fixtures.invitation(8L, clan, sam, kai, OffsetDateTime.now().minusDays(1));
        given(invitations.findByClanIdAndInviteeIdAndState(7L, 2L, "PENDING")).willReturn(Optional.of(lapsed));
        given(invitations.save(any(ClanInvitation.class))).willAnswer(a -> a.getArgument(0));
        ClanInvitation fresh = service.invite(kai, "sam", null);
        //System.out.println("lapsed " + lapsed.getState());
        assertThat(lapsed.getState()).isEqualTo("EXPIRED");
        assertThat(fresh.getState()).isEqualTo("PENDING");
        verify(invitations).saveAndFlush(lapsed);
    }

    @Test
    @DisplayName("an invite sent through another clan's path is refused before the player is looked up")
    void invitePathMustBeTheCallersClan() {
        given(membershipService.manager(eq(kai),anyString())).willReturn(ownerRow);
        assertThatThrownBy(() -> service.invite(kai, 9L,"sam", null))
                .isInstanceOf(ClanAccessRefused.class)
                .hasMessageContaining("your own clan");
        verify(users, never()).findByDisplayNameIgnoreCase(any());
        verify(invitations,never()).save(any());
    }

    @Test
    @DisplayName("an invite sent through the caller's own clan path goes out like any other")
    void invitePathOfOwnClanSends() {
        Clan big = Fixtures.clan(1000L, "Big Id", "BIG", kai, true); //an id past the small boxed range, so comparing the two by reference would refuse this
        given(membershipService.manager(eq(kai), anyString())).willReturn(Fixtures.membership(5L, big, kai, "OWNER"));
        given(users.findByDisplayNameIgnoreCase("sam")).willReturn(Optional.of(sam));
        given(memberships.findActiveByClanIdAndUserId(1000L,2L)).willReturn(Optional.empty());
        given(invitations.findByClanIdAndInviteeIdAndState(1000L,2L, "PENDING")).willReturn(Optional.empty());
        given(invitations.save(any(ClanInvitation.class))).willAnswer(a -> a.getArgument(0));
        ClanInvitation inv = service.invite(kai,Long.valueOf(1000L), "sam", "MEMBER");
        //System.out.println("sent to " + inv.getInvitee().getDisplayName());
        assertThat(inv.getState()).isEqualTo("PENDING");
        assertThat(inv.getClan()).isSameAs(big);
        verify(events).record(eq("clan_invitation"), any(), eq("InvitationCreated"), any(),eq(kai));
    }

    @Test
    @DisplayName("accepting and declining lock the invitation's clan before the invitation is read, so a delete already running is finished first")
    void answersLockTheClanFirst() {
        ClanInvitation first = Fixtures.invitation(8L, clan, sam, kai, OffsetDateTime.now().plusDays(1));
        ClanInvitation second = Fixtures.invitation(9L, clan, sam,kai, OffsetDateTime.now().plusDays(1));
        given(invitations.findClanIdByIdAndInviteeId(eq(8L), eq(2L), any())).willReturn(Optional.of(7L));
        given(invitations.existsByIdAndClanStatusAndClanDeletedAtIsNull(8L, "ACTIVE")).willReturn(true);
        given(invitations.findClanIdByIdAndInviteeId(eq(9L), eq(2L), any())).willReturn(Optional.of(7L));
        given(invitations.findById(8L)).willReturn(Optional.of(first));
        given(invitations.findById(9L)).willReturn(Optional.of(second));
        given(memberships.findActiveByUserId(2L)).willReturn(Optional.empty());
        given(memberships.save(any(ClanMembership.class))).willAnswer(a -> a.getArgument(0));
        service.accept(sam, 8L);
        service.decline(sam,9L);
        InOrder order = inOrder(membershipService, invitations);
        order.verify(membershipService).lockClan(7L);
        order.verify(invitations).findById(8L);
        order.verify(membershipService).lockClan(7L);
        order.verify(invitations).findById(9L);
        //System.out.println("states " + first.getState() + " " + second.getState());
        assertThat(first.getState()).isEqualTo("ACCEPTED");
        assertThat(second.getState()).isEqualTo("DECLINED");
    }

    @Test
    @DisplayName("an invitation a clan delete revoked while the answer waited reads as no longer open, and nobody joins")
    void revokedWhileWaitingIsNotAccepted() {
        ClanInvitation revoked = Fixtures.invitation(8L, clan, sam,kai, OffsetDateTime.now().plusDays(1));
        revoked.setState("REVOKED");
        given(invitations.findClanIdByIdAndInviteeId(eq(8L), eq(2L), any())).willReturn(Optional.of(7L));
        given(invitations.existsByIdAndClanStatusAndClanDeletedAtIsNull(8L, "ACTIVE")).willReturn(true);
        given(invitations.findById(8L)).willReturn(Optional.of(revoked));
        assertThatThrownBy(() -> service.accept(sam, 8L)).isInstanceOf(ClanRuleViolation.class).hasMessageContaining("no longer open");
        assertThatThrownBy(() -> service.decline(sam, 8L)).isInstanceOf(ClanRuleViolation.class).hasMessageContaining("no longer open");
        assertThat(revoked.getState()).isEqualTo("REVOKED");
        verify(memberships,never()).save(any());
    }

    @Test
    @DisplayName("an answer to someone else's invitation is refused without waiting on that clan's lock")
    void someoneElsesInvitationTakesNoLock() {
        AppUser lee = Fixtures.user(3L,"lee");
        given(invitations.findById(8L)).willReturn(Optional.of(Fixtures.invitation(8L, clan, sam, kai, OffsetDateTime.now().plusDays(1))));
        assertThatThrownBy(() -> service.accept(lee, 8L)).isInstanceOf(ClanRuleViolation.class).hasMessage("That invitation does not exist.");
        assertThatThrownBy(() -> service.decline(lee, 8L)).isInstanceOf(ClanRuleViolation.class).hasMessage("That invitation does not exist.");
        verify(invitations, org.mockito.Mockito.times(2)).findClanIdByIdAndInviteeId(eq(8L), eq(3L), any()); //asked with the caller's own id, which matches no invitation of sam's
        verify(membershipService, never()).lockClan(any());
    }

    @Test
    @DisplayName("a clan suspended while the accept waited for the lock is still refused once the lock is held")
    void acceptChecksTheClanAgainUnderTheLock() {
        Clan suspended = Fixtures.clan(7L, "Bad Company", "BC",kai,true);
        suspended.setStatus("SUSPENDED"); //committed while the accept waited
        given(invitations.findClanIdByIdAndInviteeId(eq(8L), eq(2L), any())).willReturn(Optional.of(7L));
        given(invitations.existsByIdAndClanStatusAndClanDeletedAtIsNull(8L, "ACTIVE")).willReturn(true); //still active when asked
        given(invitations.findById(8L)).willReturn(Optional.of(Fixtures.invitation(8L,suspended, sam, kai, OffsetDateTime.now().plusDays(1))));
        assertThatThrownBy(() -> service.accept(sam, 8L)).isInstanceOf(ClanRuleViolation.class).hasMessage("That clan is no longer taking members.");
        verify(memberships,never()).save(any());
    }

    @Test
    @DisplayName("an accept refused for the clan or for the caller's own clan is refused before the lock, with the sentence it had before")
    void acceptRefusalsComeBeforeTheLock() {
        given(invitations.findClanIdByIdAndInviteeId(eq(8L), eq(2L), any())).willReturn(Optional.of(7L));
        given(invitations.existsByIdAndClanStatusAndClanDeletedAtIsNull(8L, "ACTIVE")).willReturn(false); //suspended or deleted
        assertThatThrownBy(() -> service.accept(sam, 8L)).isInstanceOf(ClanRuleViolation.class).hasMessage("That clan is no longer taking members.");
        given(invitations.findClanIdByIdAndInviteeId(eq(9L),eq(2L), any())).willReturn(Optional.of(7L));
        given(invitations.existsByIdAndClanStatusAndClanDeletedAtIsNull(9L,"ACTIVE")).willReturn(true);
        given(memberships.existsByUserIdAndLeftAtIsNull(2L)).willReturn(true); //already in a clan
        assertThatThrownBy(() -> service.accept(sam, 9L)).isInstanceOf(ClanRuleViolation.class).hasMessage("Leave your current clan before accepting an invite.");
        //System.out.println("no lock for either accept");
        verify(membershipService, never()).lockClan(any());
        verify(invitations, never()).findById(any());
    }

    @Test
    @DisplayName("a refusal about who is asking is an access refusal, a broken rule is not")
    void accessRefusalsAreToldApart() {
        given(membershipService.manager(eq(sam), anyString())).willReturn(Fixtures.membership(6L, clan,sam, "MODERATOR"));
        assertThatThrownBy(() -> service.invite(sam, "lee", "MODERATOR")).isInstanceOf(ClanAccessRefused.class);
        given(membershipService.manager(eq(kai), anyString())).willReturn(ownerRow);
        given(users.findByDisplayNameIgnoreCase("nobody")).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.invite(kai, "nobody", null)).isInstanceOf(ClanRuleViolation.class).isNotInstanceOf(ClanAccessRefused.class);
        assertThatThrownBy(() -> service.invite(kai, "sam", "ADMIRAL")).isInstanceOf(ClanRuleViolation.class).isNotInstanceOf(ClanAccessRefused.class).hasMessageContaining("role does not exist");
        given(invitations.findById(99L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.revoke(kai, 99L)).isInstanceOf(ClanRuleViolation.class).isNotInstanceOf(ClanAccessRefused.class).hasMessageContaining("does not exist");
        assertThatThrownBy(() -> service.accept(sam, 99L)).isInstanceOf(ClanRuleViolation.class).isNotInstanceOf(ClanAccessRefused.class).hasMessageContaining("does not exist");
    }
}
