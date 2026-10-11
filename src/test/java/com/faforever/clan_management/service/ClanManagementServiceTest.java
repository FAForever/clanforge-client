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
import com.faforever.clan_management.repository.ClanLogoRepository;
import com.faforever.clan_management.repository.ClanMembershipRepository;
import com.faforever.clan_management.repository.ClanRepository;

import java.util.Arrays;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.springframework.test.util.ReflectionTestUtils;

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
    private ClanLogoRepository logos;
    @Mock
    private MembershipService membershipService;
    @Mock
    private ClanEventService events;

    private ClanManagementService service;
    private final AppUser kai = Fixtures.user(1L, "kai");
    private final AppUser sam = Fixtures.user(2L, "sam");

    @BeforeEach
    void setUp() {
        service = new ClanManagementService(clans, memberships, invitations, logos, membershipService, events, new TagRule("^[A-Za-z0-9]{1,3}$", "1 to 3 letters or numbers"), new Regions("EU/NA,EU,NA,Asia,Oceania,Global"));
    }

    private ClanForm form(String name, String tag) {
        ClanForm f = new ClanForm();
        f.setName(name);
        f.setTag(tag);
        f.setPublicClan(false);
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
        given(membershipService.manager(eq(kai),anyString())).willThrow(new ClanAccessRefused("Only the owner and moderators can edit the clan."));
        assertThatThrownBy(() -> service.update(kai, form("New", "NEW")))
                .isInstanceOf(ClanRuleViolation.class)
                .hasMessageContaining("Only the owner and moderators");
    }

    @Test
    @DisplayName("the API path cannot be used to edit another clan")
    void updateRefusesDifferentPathClan() {
        AppUser testOwner = Fixtures.user(10L, "test-owner");
        Clan clan = Fixtures.clan(7L, "Bad Company", "BC", testOwner, false);
        given(membershipService.manager(eq(testOwner), anyString()))
                .willReturn(Fixtures.membership(5L, clan, testOwner, "OWNER"));

        assertThatThrownBy(() ->
                service.update(testOwner, 9L, form("New Name", "NEW")))
                .isInstanceOf(ClanAccessRefused.class)
                .hasMessage("You can only edit your own clan.");
        assertThat(clan.getName()).isEqualTo("Bad Company");
    }

    @Test
    @DisplayName("a partial update preserves fields that were not changed")
    void patchPreservesUnchangedFields() {
        AppUser testOwner = Fixtures.user(10L, "test-owner");
        Clan clan = new Clan(
                "Bad Company",
                "BC",
                "Original description",
                testOwner,
                "PRIVATE",
                true
        );
        ReflectionTestUtils.setField(clan, "id", 7L);
        clan.setRegion("Oceania");
        given(clans.existsByIdAndDeletedAtIsNull(7L)).willReturn(true); // the clan being patched exists
        given(membershipService.manager(eq(testOwner), anyString()))
                .willReturn(Fixtures.membership(5L, clan, testOwner, "OWNER"));

        service.patch(
                testOwner,
                7L,
                new ClanPatch(
                        null,
                        null,
                        "Updated description",
                        null,
                        null,
                        null
                )
        );

        assertThat(clan.getName()).isEqualTo("Bad Company");
        assertThat(clan.getTag()).isEqualTo("BC");
        assertThat(clan.getDescription()).isEqualTo("Updated description");
        assertThat(clan.getRegion()).isEqualTo("Oceania");
        assertThat(clan.getVisibility()).isEqualTo("PRIVATE");
        assertThat(clan.isRequiresInvitation()).isTrue();
    }

    @Test
    @DisplayName("a moderator cannot delete the clan")
    void deleteRefusesModerator() {
        Clan clan = Fixtures.clan(7L, "Bad Company","BC",sam, false);
        given(membershipService.lockedMembership(kai)).willReturn(Optional.of(Fixtures.membership(5L,clan, kai,"MODERATOR")));
        assertThatThrownBy(() -> service.delete(kai))
                .isInstanceOf(ClanAccessRefused.class)
                .hasMessageContaining("Only the owner can delete");
        assertThat(clan.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("the API path cannot be used to delete another clan")
    void deleteRefusesDifferentPathClan() {
        AppUser testOwner = Fixtures.user(10L, "test-owner");
        Clan clan = Fixtures.clan(7L, "Bad Company", "BC", testOwner, false);
        given(membershipService.lockedMembership(testOwner)).willReturn(Optional.of(
                Fixtures.membership(5L, clan, testOwner, "OWNER")
        ));
        given(clans.existsByIdAndDeletedAtIsNull(9L)).willReturn(true); // clan 9L exists but belongs to someone else, so the refusal is about ownership, not existence

        assertThatThrownBy(() -> service.delete(testOwner, 9L))
                .isInstanceOf(ClanAccessRefused.class)
                .hasMessage("You can only delete your own clan.");
        assertThat(clan.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("someone in no clan cannot delete one, and that is an access refusal")
    void deleteRefusesOutsider() {
        AppUser zed = Fixtures.user(9L, "zed");
        given(membershipService.lockedMembership(zed)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(zed)).isInstanceOf(ClanAccessRefused.class).hasMessage("You are not in a clan.");
        verify(clans, never()).save(any());
    }

    @Test
    @DisplayName("deleting closes every membership, revokes open invites and writes ClanDeleted")
    void deleteClosesMembershipsAndRevokesInvites() {
        Clan clan = Fixtures.clan(7L, "Bad Company", "BC", kai, false);
        ClanMembership mine = Fixtures.membership(5L, clan, kai, "OWNER");
        ClanMembership theirs = Fixtures.membership(6L, clan, sam, "MEMBER");
        ClanInvitation open = Fixtures.invitation(8L, clan,Fixtures.user(3L,"lee"), kai, java.time.OffsetDateTime.now().plusDays(3));
        given(membershipService.lockedMembership(kai)).willReturn(Optional.of(mine));
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
    @DisplayName("an owner whose clan an administrator deleted while they waited for the lock can neither delete it again nor hand it over")
    void ownerRefusedOnceTheClanIsGone() {
        Clan clan = Fixtures.clan(7L, "Bad Company", "BC", kai, false);
        given(membershipService.lockedMembership(kai)).willReturn(Optional.empty()); //the clan went while the lock was awaited
        assertThatThrownBy(() -> service.delete(kai)).isInstanceOf(ClanAccessRefused.class).hasMessage("You are not in a clan.");
        assertThatThrownBy(() -> service.transferOwnership(kai, 2L)).isInstanceOf(ClanAccessRefused.class).hasMessage("You are not in a clan.");
        assertThat(clan.getDeletedAt()).isNull();
        assertThat(clan.getOwner()).isSameAs(kai);
        verify(memberships,never()).findRoster(any());
        verify(memberships, never()).findActiveByUserId(any()); //the owner row is only ever read under the lock
        verify(events, never()).record(any(),any(), any(), any(),any());
    }

    @Test
    @DisplayName("ownership can only go to a current member")
    void transferRefusesNonMember() {
        Clan clan = Fixtures.clan(7L, "Bad Company", "BC",kai, false);
        given(membershipService.lockedMembership(kai)).willReturn(Optional.of(Fixtures.membership(5L, clan, kai,"OWNER")));
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
        given(membershipService.lockedMembership(kai)).willReturn(Optional.of(mine));
        given(memberships.findActiveByClanIdAndUserId(7L, 2L)).willReturn(Optional.of(theirs));
        given(events.clanPayload(clan)).willReturn(new LinkedHashMap<>());
        service.transferOwnership(kai, 2L);
        assertThat(clan.getOwner()).isSameAs(sam);
        assertThat(theirs.getRole()).isEqualTo("OWNER");
        assertThat(mine.getRole()).isEqualTo("MODERATOR");
        verify(events).record(eq("clan"), eq(7L), eq("OwnershipTransferred"), any(), eq(kai));
    }

    @Test
    @DisplayName("an image is known by its first bytes, and a gif or an oversized file is refused")
    void imagesAreSniffed() {
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 13, 'I', 'H', 'D', 'R'};
        byte[] jpeg = new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 0, 16};
        byte[] gif = "GIF89a....".getBytes();
        byte[] huge = new byte[524289];
        Arrays.fill(huge, (byte) 1);
        huge[0] = (byte) 0x89; huge[1] = 'P'; huge[2] = 'N'; huge[3] = 'G';
        assertThat(service.checkImage(png)).isEqualTo("image/png");
        assertThat(service.checkImage(jpeg)).isEqualTo("image/jpeg");
        assertThatThrownBy(() -> service.checkImage(gif)).isInstanceOf(ClanRuleViolation.class).hasMessageContaining("PNG, JPG or WEBP");
        assertThatThrownBy(() -> service.checkImage(huge)).isInstanceOf(ClanRuleViolation.class).hasMessageContaining("512 KB");
        assertThatThrownBy(() -> service.checkImage(new byte[0])).isInstanceOf(ClanRuleViolation.class).hasMessageContaining("Choose an image");
    }

    @Test
    @DisplayName("the message of the day is for the owner and moderators, capped at 500 characters")
    void motdRules() {
        given(membershipService.manager(eq(sam), anyString())).willThrow(new ClanAccessRefused("Only the owner and moderators can change the message of the day."));
        assertThatThrownBy(() -> service.updateMotd(sam, "hello")).isInstanceOf(ClanRuleViolation.class).hasMessageContaining("Only the owner and moderators");
        Clan clan = Fixtures.clan(7L, "Bad Company", "BC", kai, false);
        given(membershipService.manager(eq(kai), anyString())).willReturn(Fixtures.membership(5L, clan, kai, "OWNER"));
        assertThatThrownBy(() -> service.updateMotd(kai, "x".repeat(501))).isInstanceOf(ClanRuleViolation.class).hasMessageContaining("500 characters");
        service.updateMotd(kai, "  Finals this weekend.  ");
        assertThat(clan.getMotd()).isEqualTo("Finals this weekend.");
        assertThat(clan.getMotdAuthor()).isSameAs(kai);
        assertThat(clan.getMotdUpdatedAt()).isNotNull();
        verify(events).record(eq("clan"), eq(7L), eq("ClanMotdUpdated"), any(), eq(kai));
    }

    @Test
    @DisplayName("a region outside the configured list is refused")
    void regionMustBeListed() {
        given(memberships.findActiveByUserId(1L)).willReturn(Optional.empty());
        ClanForm f = form("Bad Company", "BC");
        f.setRegion("Mars");
        assertThatThrownBy(() -> service.create(kai, f)).isInstanceOf(ClanRuleViolation.class).hasMessageContaining("region");
        verify(clans, never()).save(any());
    }

    @Test
    @DisplayName("the name and tag checks an administrator uses refuse a taken name or tag and let the clan keep its own")
    void checkNameAndTagOnTheirOwn() {
        Clan mine = Fixtures.clan(7L,"Bad Company", "BC",kai, false);
        Clan theirs = Fixtures.clan(8L, "Good Company", "GC",sam, false);
        given(clans.findByNameIgnoreCaseAndDeletedAtIsNull("good company")).willReturn(Optional.of(theirs));
        given(clans.findByNameIgnoreCaseAndDeletedAtIsNull("bad company")).willReturn(Optional.of(mine));
        given(clans.findByTagIgnoreCaseAndDeletedAtIsNull("gc")).willReturn(Optional.of(theirs));
        given(clans.findByTagIgnoreCaseAndDeletedAtIsNull("bc")).willReturn(Optional.of(mine));
        assertThatThrownBy(() -> service.checkName("good company", mine)).isInstanceOf(ClanRuleViolation.class).hasMessageContaining("already in use");
        assertThatThrownBy(() -> service.checkTag("gc", mine)).isInstanceOf(ClanRuleViolation.class).hasMessageContaining("already in use");
        assertThatThrownBy(() -> service.checkName("", mine)).isInstanceOf(ClanRuleViolation.class).hasMessageContaining("Give the clan a name");
        assertThatThrownBy(() -> service.checkTag("TOOLONG", mine)).isInstanceOf(ClanRuleViolation.class).hasMessageContaining("1 to 3");
        service.checkName("bad company",mine);
        service.checkTag("bc", mine);
    }
}
