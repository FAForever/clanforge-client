package com.faforever.clan_management.service;

import java.util.LinkedHashMap;
import java.util.List;
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
import com.faforever.clan_management.repository.ClanInvitationRepository;
import com.faforever.clan_management.repository.ClanMembershipRepository;
import com.faforever.clan_management.repository.ClanRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

//the rules around creating, editing, deleting and handing over a clan, with the database mocked away
@ExtendWith(MockitoExtension.class)
class ClanManagementServiceTest {

    @Mock
    private ClanRepository clans;
    @Mock
    private ClanMembershipRepository memberships;
    @Mock
    private ClanInvitationRepository invitations;
    @Mock
    private MembershipService membershipService;
    @Mock
    private ClanEventService events;

    private ClanManagementService service;
    private final AppUser kai = Fixtures.user(1L, "kai");
    private final AppUser sam = Fixtures.user(2L, "sam");

    @BeforeEach
    void setUp() {
        service = new ClanManagementService(clans,memberships, invitations, membershipService, events, new TagRule("^[A-Za-z0-9]{1,3}$", "1 to 3 letters or numbers"));
    }

    private ClanForm form(String name, String tag) {
        ClanForm f = new ClanForm();
        f.setName(name);
        f.setTag(tag);
        f.setPublicClan(false);
        //System.out.println("form " + name + " " + tag);
        return f;
    }

    @Test
    @DisplayName("someone already in a clan cannot create another")
    void createRefusesWhenAlreadyInAClan() {
        Clan other = Fixtures.clan(9L, "Other", "OTH", sam, false);
        given(memberships.findActiveByUserId(1L)).willReturn(Optional.of(Fixtures.membership(5L, other, kai, "MEMBER")));
        assertThatThrownBy(() -> service.create(kai, form("Bad Company", "BC")))
                .isInstanceOf(ClanRuleViolation.class)
                .hasMessageContaining("already in a clan");
        verify(clans, never()).save(any());
    }

    @Test
    @DisplayName("a tag outside the configured rule is refused before anything is saved")
    void createRefusesBadTag() {
        given(memberships.findActiveByUserId(1L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(kai, form("Bad Company", "BCDE")))
                .isInstanceOf(ClanRuleViolation.class)
                .hasMessageContaining("Clan tag must be 1 to 3 letters or numbers");
        verify(clans,never()).save(any());
    }

    @Test
    @DisplayName("a tag that differs only in case from a live clan is taken")
    void createRefusesTakenTagIgnoringCase() {
        given(memberships.findActiveByUserId(1L)).willReturn(Optional.empty());
        given(clans.findByNameIgnoreCaseAndDeletedAtIsNull("Bad Company")).willReturn(Optional.empty());
        given(clans.findByTagIgnoreCaseAndDeletedAtIsNull("bc")).willReturn(Optional.of(Fixtures.clan(3L,"Old", "BC", sam, false)));
        assertThatThrownBy(() -> service.create(kai,form("Bad Company","bc")))
                .isInstanceOf(ClanRuleViolation.class)
                .hasMessageContaining("tag 'bc' is already in use");
        verify(clans, never()).save(any());
    }

    @Test
    @DisplayName("creating saves the clan, makes the creator its owner and writes two events")
    void createSavesClanOwnerAndEvents() {
        given(memberships.findActiveByUserId(1L)).willReturn(Optional.empty());
        given(clans.findByNameIgnoreCaseAndDeletedAtIsNull("Bad Company")).willReturn(Optional.empty());
        given(clans.findByTagIgnoreCaseAndDeletedAtIsNull("BC")).willReturn(Optional.empty());
        given(clans.save(any(Clan.class))).willAnswer(a -> a.getArgument(0));
        given(memberships.save(any(ClanMembership.class))).willAnswer(a -> a.getArgument(0));
        Clan clan = service.create(kai, form("  Bad Company ", "BC"));
        assertThat(clan.getName()).isEqualTo("Bad Company");
        assertThat(clan.getTag()).isEqualTo("BC");
        assertThat(clan.getVisibility()).isEqualTo("PRIVATE");
        assertThat(clan.getOwner()).isSameAs(kai);
        ArgumentCaptor<ClanMembership> saved = ArgumentCaptor.forClass(ClanMembership.class);
        verify(memberships).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo("OWNER");
        assertThat(saved.getValue().getUser()).isSameAs(kai);
        verify(events).record(eq("clan"),any(), eq("ClanCreated"), any(), eq(kai));
        verify(events).record(eq("clan_membership"), any(), eq("MemberJoined"), any(), eq(kai));
    }

    @Test
    @DisplayName("a plain member cannot edit the clan")
    void updateRefusesMember() {
        given(membershipService.manager(eq(kai),anyString())).willThrow(new ClanRuleViolation("Only the owner and moderators can edit the clan."));
        assertThatThrownBy(() -> service.update(kai, form("New", "NEW")))
                .isInstanceOf(ClanRuleViolation.class)
                .hasMessageContaining("Only the owner and moderators");
    }

    @Test
    @DisplayName("a moderator cannot delete the clan")
    void deleteRefusesModerator() {
        Clan clan = Fixtures.clan(7L, "Bad Company","BC",sam, false);
        given(memberships.findActiveByUserId(1L)).willReturn(Optional.of(Fixtures.membership(5L,clan, kai,"MODERATOR")));
        assertThatThrownBy(() -> service.delete(kai))
                .isInstanceOf(ClanRuleViolation.class)
                .hasMessageContaining("Only the owner can delete");
        assertThat(clan.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("deleting closes every membership, revokes open invites and writes ClanDeleted")
    void deleteClosesMembershipsAndRevokesInvites() {
        Clan clan = Fixtures.clan(7L, "Bad Company", "BC", kai, false);
        ClanMembership mine = Fixtures.membership(5L, clan, kai, "OWNER");
        ClanMembership theirs = Fixtures.membership(6L, clan, sam, "MEMBER");
        ClanInvitation open = Fixtures.invitation(8L, clan,Fixtures.user(3L,"lee"), kai, java.time.OffsetDateTime.now().plusDays(3));
        given(memberships.findActiveByUserId(1L)).willReturn(Optional.of(mine));
        given(memberships.findRoster(7L)).willReturn(List.of(mine, theirs));
        given(invitations.findByClanIdAndState(7L,"PENDING")).willReturn(List.of(open));
        given(events.clanPayload(clan)).willReturn(new LinkedHashMap<>());
        service.delete(kai);
        assertThat(clan.getDeletedAt()).isNotNull();
        assertThat(mine.getLeftAt()).isNotNull();
        assertThat(theirs.getLeftAt()).isNotNull();
        assertThat(open.getState()).isEqualTo("REVOKED");
        verify(events).record(eq("clan"), eq(7L), eq("ClanDeleted"), any(), eq(kai));
    }

    @Test
    @DisplayName("ownership can only go to a current member")
    void transferRefusesNonMember() {
        Clan clan = Fixtures.clan(7L, "Bad Company", "BC",kai, false);
        given(memberships.findActiveByUserId(1L)).willReturn(Optional.of(Fixtures.membership(5L, clan, kai,"OWNER")));
        given(memberships.findActiveByClanIdAndUserId(7L, 2L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.transferOwnership(kai,2L))
                .isInstanceOf(ClanRuleViolation.class)
                .hasMessageContaining("current member");
        assertThat(clan.getOwner()).isSameAs(kai);
    }

    @Test
    @DisplayName("a transfer swaps the roles and records who owned it before")
    void transferSwapsRoles() {
        Clan clan = Fixtures.clan(7L, "Bad Company", "BC", kai, false);
        ClanMembership mine = Fixtures.membership(5L, clan, kai,"OWNER");
        ClanMembership theirs = Fixtures.membership(6L, clan,sam, "MEMBER");
        given(memberships.findActiveByUserId(1L)).willReturn(Optional.of(mine));
        given(memberships.findActiveByClanIdAndUserId(7L, 2L)).willReturn(Optional.of(theirs));
        given(events.clanPayload(clan)).willReturn(new LinkedHashMap<>());
        service.transferOwnership(kai, 2L);
        //System.out.println("owner now " + clan.getOwner().getDisplayName());
        assertThat(clan.getOwner()).isSameAs(sam);
        assertThat(theirs.getRole()).isEqualTo("OWNER");
        assertThat(mine.getRole()).isEqualTo("MODERATOR");
        verify(events).record(eq("clan"), eq(7L), eq("OwnershipTransferred"), any(), eq(kai));
    }
}
