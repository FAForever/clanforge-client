package com.faforever.clan_management.service;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
        given(membershipService.manager(eq(sam), anyString())).willThrow(new ClanRuleViolation("Only the owner and moderators can send invites."));
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
                .hasMessageContaining("not yours");
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
                .hasMessageContaining("another clan");
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
                .isInstanceOf(ClanRuleViolation.class)
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
}
