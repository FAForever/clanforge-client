package com.faforever.clan_management.service;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @Test
    @DisplayName("an invitation-only clan cannot be joined from its page")
    void joinRefusesInviteOnlyClan() {
        given(clans.findByIdAndDeletedAtIsNull(7L)).willReturn(Optional.of(Fixtures.clan(7L, "Bad Company", "BC", sam, true)));
        assertThatThrownBy(() -> service.join(7L,kai))
                .isInstanceOf(ClanRuleViolation.class)
                .hasMessageContaining("by invitation");
        verify(memberships, never()).save(any());
    }

    @Test
    @DisplayName("one active membership per person, so joining while in a clan is refused")
    void joinRefusesWhenAlreadyInAClan() {
        Clan clan = Fixtures.clan(7L, "Bad Company","BC", sam,false);
        given(clans.findByIdAndDeletedAtIsNull(7L)).willReturn(Optional.of(clan));
        given(memberships.findActiveByUserId(1L)).willReturn(Optional.of(Fixtures.membership(5L, Fixtures.clan(8L, "Other", "OTH",lee, false), kai, "MEMBER")));
        assertThatThrownBy(() -> service.join(7L,kai))
                .isInstanceOf(ClanRuleViolation.class)
                .hasMessageContaining("Leave your current clan");
        verify(memberships, never()).save(any());
    }

    @Test
    @DisplayName("joining an open clan saves a member row and writes MemberJoined")
    void joinSavesMemberAndEvent() {
        Clan clan = Fixtures.clan(7L, "Bad Company","BC", sam, false);
        given(clans.findByIdAndDeletedAtIsNull(7L)).willReturn(Optional.of(clan));
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
        given(memberships.findActiveByUserId(1L)).willReturn(Optional.of(mine));
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
        given(memberships.findActiveByUserId(1L)).willReturn(Optional.of(mine));
        service.leave(kai);
        assertThat(mine.getLeftAt()).isNotNull();
        verify(events).record(eq("clan_membership"), eq(5L), eq("MemberLeft"), any(), eq(kai));
    }

    @Test
    @DisplayName("a moderator cannot remove another moderator")
    void moderatorCannotRemoveModerator() {
        Clan clan = Fixtures.clan(7L,"Bad Company", "BC", lee,false);
        given(memberships.findActiveByUserId(1L)).willReturn(Optional.of(Fixtures.membership(5L, clan,kai,"MODERATOR")));
        ClanMembership target = Fixtures.membership(6L, clan, sam, "MODERATOR");
        given(memberships.findActiveByClanIdAndUserId(7L,2L)).willReturn(Optional.of(target));
        assertThatThrownBy(() -> service.removeMember(kai, 2L))
                .isInstanceOf(ClanRuleViolation.class)
                .hasMessageContaining("cannot remove another moderator");
        assertThat(target.getLeftAt()).isNull();
    }

    @Test
    @DisplayName("the owner removes a member and MemberRemoved names the owner as actor")
    void ownerRemovesMember() {
        Clan clan = Fixtures.clan(7L, "Bad Company", "BC", kai,false);
        given(memberships.findActiveByUserId(1L)).willReturn(Optional.of(Fixtures.membership(5L, clan,kai, "OWNER")));
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
        given(memberships.findActiveByUserId(1L)).willReturn(Optional.of(Fixtures.membership(5L, clan, kai, "MODERATOR")));
        assertThatThrownBy(() -> service.setRole(kai, 2L, "MODERATOR"))
                .isInstanceOf(ClanRuleViolation.class)
                .hasMessageContaining("Only the owner can change roles");
    }

    @Test
    @DisplayName("the owner promotes a member to moderator")
    void ownerPromotesMember() {
        Clan clan = Fixtures.clan(7L, "Bad Company", "BC", kai, false);
        given(memberships.findActiveByUserId(1L)).willReturn(Optional.of(Fixtures.membership(5L, clan, kai,"OWNER")));
        ClanMembership target = Fixtures.membership(6L, clan, sam, "MEMBER");
        given(memberships.findActiveByClanIdAndUserId(7L, 2L)).willReturn(Optional.of(target));
        service.setRole(kai, 2L, "MODERATOR");
        //System.out.println("role now " + target.getRole());
        assertThat(target.getRole()).isEqualTo("MODERATOR");
        verify(events).record(eq("clan_membership"), eq(6L), eq("MemberRoleChanged"),any(), eq(kai));
    }
}
