package com.faforever.clan_management.service;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.entity.ClanMembership;
import com.faforever.clan_management.repository.ClanMembershipRepository;
import com.faforever.clan_management.repository.ClanRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// joining, leaving, removing and role changes, each rule tried from the side that must be refused
@ExtendWith(MockitoExtension.class)
class MembershipServiceTest {

    @Mock
    private ClanRepository clans;
    @Mock
    private ClanMembershipRepository memberships;
    @Mock
    private ClanEventService events;
    @InjectMocks
    private MembershipService service;

    private final AppUser kai = Fixtures.user(1L,"kai");
    private final AppUser sam = Fixtures.user(2L, "sam");
    private final AppUser lee = Fixtures.user(3L,"lee");

    //the three reads lockedMembership makes, in its order: the clan id, the clan lock, then the row
    private void holds(AppUser user, ClanMembership row) {
        given(memberships.findActiveClanIdByUserId(user.getId())).willReturn(Optional.of(row.getClan().getId()));
        given(clans.lockLive(row.getClan().getId())).willReturn(Optional.of(row.getClan()));
        given(memberships.findActiveByUserId(user.getId())).willReturn(Optional.of(row));
    }

    @Test
    @DisplayName("an invitation-only clan cannot be joined from its page")
    void joinRefusesInviteOnlyClan() {
        given(clans.existsByIdAndVisibilityAndRequiresInvitationFalseAndDeletedAtIsNull(7L, "PUBLIC")).willReturn(false);
        given(clans.existsByIdAndDeletedAtIsNull(7L)).willReturn(true);
        assertThatThrownBy(() -> service.join(7L,kai))
                .isInstanceOf(ClanRuleViolation.class)
                .hasMessageContaining("by invitation");
        verify(memberships, never()).save(any());
    }

    @Test
    @DisplayName("one active membership per person, so joining while in a clan is refused")
    void joinRefusesWhenAlreadyInAClan() {
        given(clans.existsByIdAndVisibilityAndStatusAndRequiresInvitationFalseAndDeletedAtIsNull(7L,"PUBLIC", "ACTIVE")).willReturn(true);
        given(memberships.existsByUserIdAndLeftAtIsNull(1L)).willReturn(true); //already in another clan
        assertThatThrownBy(() -> service.join(7L,kai))
                .isInstanceOf(ClanRuleViolation.class)
                .hasMessageContaining("Leave your current clan");
        verify(memberships, never()).save(any());
        verify(clans, never()).lockLive(any());
    }

    @Test
    @DisplayName("joining an open clan saves a member row and writes MemberJoined")
    void joinSavesMemberAndEvent() {
        Clan clan = Fixtures.clan(7L, "Bad Company","BC", sam, false);
        given(clans.existsByIdAndVisibilityAndStatusAndRequiresInvitationFalseAndDeletedAtIsNull(7L,"PUBLIC", "ACTIVE")).willReturn(true);
        given(clans.lockLive(7L)).willReturn(Optional.of(clan));
        given(memberships.findActiveByUserId(1L)).willReturn(Optional.empty());
        given(memberships.save(any(ClanMembership.class))).willAnswer(a -> a.getArgument(0));
        ClanMembership m = service.join(7L, kai);
        //System.out.println("joined as " + m.getRole());
        assertThat(m.getRole()).isEqualTo("MEMBER");
        assertThat(m.getClan()).isSameAs(clan);
        verify(events).record(eq("clan_membership"), any(), eq("MemberJoined"), any(), eq(kai));
    }

    @Test
    @DisplayName("the owner cannot simply leave")
    void ownerCannotLeave() {
        Clan clan = Fixtures.clan(7L, "Bad Company","BC",kai, false);
        ClanMembership mine = Fixtures.membership(5L, clan, kai, "OWNER");
        holds(kai, mine);
        assertThatThrownBy(() -> service.leave(kai))
                .isInstanceOf(ClanRuleViolation.class)
                .hasMessageContaining("Transfer ownership or delete");
        assertThat(mine.getLeftAt()).isNull();
    }

    @Test
    @DisplayName("a member leaves by closing their row, the history keeps it")
    void memberLeaves() {
        Clan clan = Fixtures.clan(7L, "Bad Company", "BC", sam,false);
        ClanMembership mine = Fixtures.membership(5L, clan, kai, "MEMBER");
        holds(kai, mine);
        service.leave(kai);
        assertThat(mine.getLeftAt()).isNotNull();
        verify(events).record(eq("clan_membership"), eq(5L), eq("MemberLeft"), any(), eq(kai));
    }

    @Test
    @DisplayName("the API path cannot be used to leave another clan")
    void leaveRefusesDifferentPathClan() {
        AppUser testOwner = Fixtures.user(10L, "test-owner");
        AppUser testMember = Fixtures.user(11L, "test-member");
        Clan clan = Fixtures.clan(7L, "Bad Company", "BC", testOwner, false);
        ClanMembership mine = Fixtures.membership(
                5L,
                clan,
                testMember,
                "MEMBER"
        );
        given(memberships.findActiveByUserId(11L))
                .willReturn(Optional.of(mine));

        assertThatThrownBy(() -> service.leave(9L, testMember))
                .isInstanceOf(ClanAccessRefused.class)
                .hasMessage("You can only leave your own clan.");
        assertThat(mine.getLeftAt()).isNull();
    }

    @Test
    @DisplayName("a moderator cannot remove another moderator")
    void moderatorCannotRemoveModerator() {
        Clan clan = Fixtures.clan(7L,"Bad Company", "BC", lee,false);
        holds(kai, Fixtures.membership(5L, clan,kai,"MODERATOR"));
        ClanMembership target = Fixtures.membership(6L, clan, sam, "MODERATOR");
        given(memberships.findActiveByClanIdAndUserId(7L,2L)).willReturn(Optional.of(target));
        assertThatThrownBy(() -> service.removeMember(kai, 2L))
                .isInstanceOf(ClanAccessRefused.class)
                .hasMessageContaining("cannot remove another moderator");
        assertThat(target.getLeftAt()).isNull();
    }

    @Test
    @DisplayName("the owner removes a member and MemberRemoved names the owner as actor")
    void ownerRemovesMember() {
        Clan clan = Fixtures.clan(7L, "Bad Company", "BC", kai,false);
        holds(kai, Fixtures.membership(5L, clan,kai, "OWNER"));
        ClanMembership target = Fixtures.membership(6L, clan, sam, "MEMBER");
        given(memberships.findActiveByClanIdAndUserId(7L, 2L)).willReturn(Optional.of(target));
        service.removeMember(kai, 2L);
        assertThat(target.getLeftAt()).isNotNull();
        verify(events).record(eq("clan_membership"), eq(6L), eq("MemberRemoved"), any(), eq(kai));
    }

    @Test
    @DisplayName("only the owner changes roles")
    void onlyOwnerChangesRoles() {
        Clan clan = Fixtures.clan(7L, "Bad Company", "BC", lee, false);
        holds(kai, Fixtures.membership(5L, clan, kai, "MODERATOR"));
        assertThatThrownBy(() -> service.setRole(kai, 2L, "MODERATOR"))
                .isInstanceOf(ClanAccessRefused.class)
                .hasMessageContaining("Only the owner can change roles");
    }

    @Test
    @DisplayName("the owner promotes a member to moderator")
    void ownerPromotesMember() {
        Clan clan = Fixtures.clan(7L, "Bad Company", "BC", kai, false);
        holds(kai, Fixtures.membership(5L, clan, kai,"OWNER"));
        ClanMembership target = Fixtures.membership(6L, clan, sam, "MEMBER");
        given(memberships.findActiveByClanIdAndUserId(7L, 2L)).willReturn(Optional.of(target));
        service.setRole(kai, 2L, "MODERATOR");
        //System.out.println("role now " + target.getRole());
        assertThat(target.getRole()).isEqualTo("MODERATOR");
        verify(events).record(eq("clan_membership"), eq(6L), eq("MemberRoleChanged"),any(), eq(kai));
    }

    @Test
    @DisplayName("someone in no clan cannot change roles, and that is an access refusal")
    void outsiderCannotChangeRoles() {
        AppUser zed = Fixtures.user(9L, "zed");
        given(memberships.findActiveClanIdByUserId(9L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.setRole(zed,2L, "MODERATOR")).isInstanceOf(ClanAccessRefused.class).hasMessage("You are not in a clan.");
    }

    @Test
    @DisplayName("leaving while in no clan is a broken rule, not an access refusal, since leaving takes no role")
    void leavingWithNoClanIsARuleViolation() {
        AppUser zed = Fixtures.user(9L, "zed");
        given(memberships.findActiveClanIdByUserId(9L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.leave(zed)).isInstanceOf(ClanRuleViolation.class).isNotInstanceOf(ClanAccessRefused.class).hasMessage("You are not in a clan.");
    }

    @Test
    @DisplayName("nobody removes the owner, which is a rule about the clan and not about the caller")
    void ownerCannotBeRemoved() {
        Clan clan = Fixtures.clan(7L, "Bad Company","BC",lee, false);
        holds(kai, Fixtures.membership(5L,clan, kai, "MODERATOR"));
        ClanMembership owner = Fixtures.membership(4L, clan, lee,"OWNER");
        given(memberships.findActiveByClanIdAndUserId(7L, 3L)).willReturn(Optional.of(owner));
        assertThatThrownBy(() -> service.removeMember(kai, 3L)).isInstanceOf(ClanRuleViolation.class).isNotInstanceOf(ClanAccessRefused.class).hasMessageContaining("owner cannot be removed");
        //System.out.println("owner still in " + owner.getLeftAt());
        assertThat(owner.getLeftAt()).isNull();
    }

    @Test
    @DisplayName("a plain member or an outsider gets an access refusal, an owner or moderator gets their row")
    void managerRefusesPlainMembersAndOutsiders() {
        Clan clan = Fixtures.clan(7L, "Bad Company", "BC", sam,false);
        holds(kai, Fixtures.membership(5L,clan, kai, "MEMBER"));
        assertThatThrownBy(() -> service.manager(kai, "Only the owner and moderators can send invites."))
                .isInstanceOf(ClanAccessRefused.class)
                .hasMessage("Only the owner and moderators can send invites.");
        given(memberships.findActiveClanIdByUserId(3L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.manager(lee, "unused")).isInstanceOf(ClanAccessRefused.class).hasMessage("You are not in a clan.");
        holds(sam, Fixtures.membership(6L,clan, sam,"OWNER"));
        //System.out.println("owner row " + service.manager(sam, "unused").getRole());
        assertThat(service.manager(sam,"unused").getRole()).isEqualTo("OWNER");
        holds(kai, Fixtures.membership(5L,clan, kai, "MODERATOR"));
        assertThat(service.manager(kai, "unused").getRole()).isEqualTo("MODERATOR");
    }

    @Test
    @DisplayName("a clan an administrator deleted while the request waited for its lock refuses the owner, the moderator and the member leaving")
    void clanDeletedWhileWaitingForTheLock() {
        given(memberships.findActiveClanIdByUserId(1L)).willReturn(Optional.of(7L)); //read just before the delete committed
        given(memberships.findActiveClanIdByUserId(2L)).willReturn(Optional.of(7L));
        given(clans.lockLive(7L)).willReturn(Optional.empty()); //and gone by the time the lock was granted
        assertThatThrownBy(() -> service.manager(kai,"unused")).isInstanceOf(ClanAccessRefused.class).hasMessage("You are not in a clan.");
        assertThatThrownBy(() -> service.setRole(kai, 2L, "MODERATOR")).isInstanceOf(ClanAccessRefused.class).hasMessage("You are not in a clan.");
        assertThatThrownBy(() -> service.removeMember(kai, 2L)).isInstanceOf(ClanAccessRefused.class).hasMessage("You are not in a clan.");
        assertThatThrownBy(() -> service.leave(sam)).isInstanceOf(ClanRuleViolation.class).isNotInstanceOf(ClanAccessRefused.class).hasMessage("You are not in a clan.");
        //System.out.println("no row read once the lock came back empty");
        verify(memberships, never()).findActiveByUserId(any());
        verify(memberships, never()).findActiveByClanIdAndUserId(any(),any());
        verify(events,never()).record(any(), any(), any(),any(), any());
    }

    @Test
    @DisplayName("the role is read after the clan lock, so an owner who demoted this moderator while the request waited is the one that counts")
    void roleIsReadAfterTheLock() {
        Clan clan = Fixtures.clan(7L, "Bad Company", "BC", sam, false);
        given(memberships.findActiveClanIdByUserId(1L)).willReturn(Optional.of(7L));
        given(clans.lockLive(7L)).willReturn(Optional.of(clan));
        given(memberships.findActiveByUserId(1L)).willReturn(Optional.of(Fixtures.membership(5L, clan, kai,"MEMBER"))); //demoted by the time the row is read
        assertThatThrownBy(() -> service.removeMember(kai, 2L)).isInstanceOf(ClanAccessRefused.class).hasMessageContaining("Only the owner and moderators");
        InOrder order = inOrder(memberships, clans);
        order.verify(memberships).findActiveClanIdByUserId(1L);
        order.verify(clans).lockLive(7L);
        order.verify(memberships).findActiveByUserId(1L);
        given(memberships.findActiveByUserId(1L)).willReturn(Optional.of(Fixtures.membership(5L, Fixtures.clan(8L, "Other", "OTH", lee, false), kai, "OWNER"))); //moved to another clan while this waited
        assertThatThrownBy(() -> service.manager(kai, "unused")).isInstanceOf(ClanAccessRefused.class).hasMessage("You are not in a clan.");
        verify(memberships, never()).findActiveByClanIdAndUserId(any(), any());
    }

    @Test
    @DisplayName("joining takes the clan lock, so a clan deleted while the join waited reads as missing")
    void joinTakesTheClanLock() {
        given(clans.existsByIdAndVisibilityAndStatusAndRequiresInvitationFalseAndDeletedAtIsNull(7L, "PUBLIC","ACTIVE")).willReturn(true);
        given(clans.lockLive(7L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.join(7L,kai)).isInstanceOf(ClanRuleViolation.class).hasMessage("That clan does not exist.");
        verify(clans, never()).findByIdAndDeletedAtIsNull(any());
        verify(memberships, never()).save(any());
    }

    @Test
    @DisplayName("a clan made private while the join waited for the lock is still refused once the lock is held")
    void joinChecksAgainAfterTheLock() {
        Clan clan = Fixtures.clan(7L, "Bad Company", "BC", sam, false);
        clan.setVisibility("PRIVATE"); //the owner changed it between the question and the lock
        given(clans.existsByIdAndVisibilityAndStatusAndRequiresInvitationFalseAndDeletedAtIsNull(7L, "PUBLIC", "ACTIVE")).willReturn(true);
        given(clans.lockLive(7L)).willReturn(Optional.of(clan));
        assertThatThrownBy(() -> service.join(7L, kai)).isInstanceOf(ClanRuleViolation.class).hasMessage("That clan only takes new members by invitation.");
        verify(memberships, never()).save(any());
    }

    @Test
    @DisplayName("a clan suspended, or a joiner who joined elsewhere, while the join waited for the lock is still refused once it is held")
    void joinChecksStatusAndMembershipAgainUnderTheLock() {
        Clan clan = Fixtures.clan(7L, "Bad Company", "BC", sam, false);
        clan.setStatus("SUSPENDED"); //suspended between the question and the lock
        given(clans.existsByIdAndVisibilityAndStatusAndRequiresInvitationFalseAndDeletedAtIsNull(7L, "PUBLIC", "ACTIVE")).willReturn(true);
        given(clans.lockLive(7L)).willReturn(Optional.of(clan));
        assertThatThrownBy(() -> service.join(7L, kai)).isInstanceOf(ClanRuleViolation.class).hasMessage("That clan is suspended.");
        Clan open = Fixtures.clan(8L, "Open Door", "OD", sam, false);
        given(clans.existsByIdAndVisibilityAndStatusAndRequiresInvitationFalseAndDeletedAtIsNull(8L, "PUBLIC", "ACTIVE")).willReturn(true);
        given(clans.lockLive(8L)).willReturn(Optional.of(open));
        given(memberships.findActiveByUserId(1L)).willReturn(Optional.of(Fixtures.membership(5L, Fixtures.clan(9L, "Elsewhere","EW", lee, false), kai, "MEMBER"))); //joined another clan meanwhile
        assertThatThrownBy(() -> service.join(8L, kai)).isInstanceOf(ClanRuleViolation.class).hasMessage("Leave your current clan before joining another.");
        verify(memberships, never()).save(any());
    }

    @Test
    @DisplayName("a suspended clan and a joiner already in a clan are refused before the lock as well")
    void otherRefusalsNeverTakeTheLock() {
        given(clans.existsByIdAndDeletedAtIsNull(7L)).willReturn(true);
        given(clans.existsByIdAndVisibilityAndRequiresInvitationFalseAndDeletedAtIsNull(7L, "PUBLIC")).willReturn(true); //open to anyone, but suspended
        assertThatThrownBy(() -> service.join(7L, kai)).isInstanceOf(ClanRuleViolation.class).hasMessage("That clan is suspended.");
        given(clans.existsByIdAndVisibilityAndStatusAndRequiresInvitationFalseAndDeletedAtIsNull(8L, "PUBLIC", "ACTIVE")).willReturn(true);
        given(memberships.existsByUserIdAndLeftAtIsNull(1L)).willReturn(true);
        assertThatThrownBy(() -> service.join(8L,kai)).isInstanceOf(ClanRuleViolation.class).hasMessage("Leave your current clan before joining another.");
        //System.out.println("no lock for either");
        verify(clans, never()).lockLive(any());
    }

    @Test
    @DisplayName("a join that would be refused anyway is refused before the clan lock, so an outsider never waits on it")
    void refusedJoinNeverTakesTheLock() {
        given(clans.existsByIdAndVisibilityAndRequiresInvitationFalseAndDeletedAtIsNull(7L, "PUBLIC")).willReturn(false);
        given(clans.existsByIdAndDeletedAtIsNull(7L)).willReturn(true); //private or invitation only
        given(clans.existsByIdAndDeletedAtIsNull(99L)).willReturn(false);
        assertThatThrownBy(() -> service.join(7L,kai)).isInstanceOf(ClanRuleViolation.class).hasMessage("That clan only takes new members by invitation.");
        assertThatThrownBy(() -> service.join(99L, kai)).isInstanceOf(ClanRuleViolation.class).hasMessage("That clan does not exist.");
        //System.out.println("no lock for a refused join");
        verify(clans, never()).lockLive(any());
    }
}
