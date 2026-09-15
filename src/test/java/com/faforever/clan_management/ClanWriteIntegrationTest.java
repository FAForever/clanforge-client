package com.faforever.clan_management;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.entity.ClanEvent;
import com.faforever.clan_management.entity.ClanInvitation;
import com.faforever.clan_management.repository.AppUserRepository;
import com.faforever.clan_management.repository.ClanEventRepository;
import com.faforever.clan_management.repository.ClanRepository;
import com.faforever.clan_management.service.CalendarService;
import com.faforever.clan_management.service.ClanCard;
import com.faforever.clan_management.service.ClanForm;
import com.faforever.clan_management.service.ClanManagementService;
import com.faforever.clan_management.service.ClanRuleViolation;
import com.faforever.clan_management.service.ClanService;
import com.faforever.clan_management.service.InvitationService;
import com.faforever.clan_management.service.MembershipService;
import com.faforever.clan_management.service.NotificationService;
import com.faforever.clan_management.service.Notice;
import com.faforever.clan_management.entity.ClanLogo;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

//the whole write path against a real postgres, rolled back at the end so the loaded data is untouched
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
@Transactional
class ClanWriteIntegrationTest {

    @Autowired
    private AppUserRepository users;
    @Autowired
    private ClanRepository clans;
    @Autowired
    private ClanEventRepository events;
    @Autowired
    private ClanManagementService management;
    @Autowired
    private InvitationService invitations;
    @Autowired
    private MembershipService memberships;
    @Autowired
    private ClanService clanService;
    @Autowired
    private CalendarService calendar;
    @Autowired
    private NotificationService notifications;

    //a tag no loaded clan uses, tried in order so the test is not at the mercy of the dump
    private String freeTag() {
        for (String t : List.of("ZQ9", "ZQ8", "ZQ7", "XQ9", "XQ8")) {
            if (clans.findByTagIgnoreCaseAndDeletedAtIsNull(t).isEmpty()) return t;
        }
        throw new IllegalStateException("every candidate tag is taken");
    }

    @Test
    @DisplayName("create, invite, accept and leave each land in the database with an event")
    void createInviteAcceptLeave() {
        long before = events.count();
        String stamp = Long.toString(System.nanoTime());
        AppUser owner = users.save(new AppUser("it-owner-" + stamp, "it_owner_" + stamp, "local", null));
        AppUser guest = users.save(new AppUser("it-guest-"+stamp, "it_guest_"+stamp,"local", null));
        ClanForm form = new ClanForm();
        form.setName("Integration Clan " + stamp);
        form.setTag(freeTag());
        form.setDescription("written by the integration test");
        form.setPublicClan(true);
        form.setRequiresInvitation(true);
        Clan clan = management.create(owner,form);
        assertThat(clan.getId()).isNotNull();
        assertThat(memberships.roster(clan)).hasSize(1);
        assertThat(memberships.roster(clan).get(0).getRole()).isEqualTo("OWNER");
        ClanInvitation inv = invitations.invite(owner, guest.getDisplayName(), "MODERATOR");
        assertThat(invitations.inbox(guest)).hasSize(1);
        assertThat(invitations.sent(clan)).hasSize(1);
        invitations.accept(guest, inv.getId());
        assertThat(memberships.roster(clan)).hasSize(2);
        assertThat(memberships.roster(clan).get(1).getRole()).isEqualTo("MODERATOR"); //the role the invite offered
        assertThat(invitations.inbox(guest)).isEmpty();
        memberships.leave(guest);
        assertThat(memberships.roster(clan)).hasSize(1);
        assertThat(events.count() - before).isEqualTo(6);
        //System.out.println("events " + (events.count() - before));
        List<ClanEvent> mine = events.findAll().stream().filter(e -> e.getId() != null && e.getActor() != null && e.getActor().getId().equals(owner.getId())).toList();
        assertThat(mine).extracting(ClanEvent::getEventType).contains("ClanCreated","MemberJoined", "InvitationCreated");
        ClanEvent created = mine.stream().filter(e -> "ClanCreated".equals(e.getEventType())).findFirst().orElseThrow();
        assertThat(created.getPayload()).contains(form.getTag());
        assertThat(created.getPayload()).contains("requiresInvitation");
    }

    @Test
    @DisplayName("the search finds a clan by tag and the members sort runs against postgres")
    void searchRunsInTheDatabase() {
        String stamp = Long.toString(System.nanoTime());
        AppUser owner = users.save(new AppUser("it-owner-" + stamp,"it_owner_" + stamp, "local", null));
        ClanForm form = new ClanForm();
        form.setName("Searchable " + stamp);
        form.setTag(freeTag());
        management.create(owner, form);
        Page<ClanCard> byTag = clanService.searchPublicClanCards(form.getTag().toLowerCase(), "all", "name", PageRequest.of(0, 20, Sort.by("name")));
        assertThat(byTag.getContent()).extracting(c -> c.getClan().getTag()).contains(form.getTag());
        Page<ClanCard> byMembers = clanService.searchPublicClanCards("", "all", "members", PageRequest.of(0, 5));
        assertThat(byMembers.getContent()).hasSize(5);
        assertThat(byMembers.getContent().get(0).getMemberCount()).isGreaterThanOrEqualTo(byMembers.getContent().get(4).getMemberCount());
        //the dump has almost no open clans, faf created every clan invitation only, so this one is the clan just made
        Page<ClanCard> open = clanService.searchPublicClanCards("","open", "members", PageRequest.of(0, 5));
        assertThat(open.getContent()).extracting(c -> c.getClan().getTag()).contains(form.getTag());
        Page<ClanCard> wildcard = clanService.searchPublicClanCards("%", "all", "name", PageRequest.of(0, 20, Sort.by("name")));
        //System.out.println("wildcard hits " + wildcard.getTotalElements());
        assertThat(wildcard.getTotalElements()).isLessThan(byMembers.getTotalElements());
    }

    @Test
    @DisplayName("decline, role change, transfer and remove each land in the database with an event")
    void declineRoleTransferRemove() {
        long before = events.count();
        String stamp = Long.toString(System.nanoTime());
        AppUser owner = users.save(new AppUser("it-owner-" + stamp, "it_owner_" + stamp, "local", null));
        AppUser first = users.save(new AppUser("it-first-" + stamp, "it_first_" + stamp, "local", null));
        AppUser second = users.save(new AppUser("it-second-" + stamp, "it_second_" + stamp, "local", null));
        ClanForm form = new ClanForm();
        form.setName("Handover Clan " + stamp);
        form.setTag(freeTag());
        Clan clan = management.create(owner, form);
        ClanInvitation toFirst = invitations.invite(owner, first.getDisplayName(), null);
        ClanInvitation toSecond = invitations.invite(owner, second.getDisplayName(), "MEMBER");
        invitations.accept(first, toFirst.getId());
        invitations.decline(second, toSecond.getId());
        assertThat(invitations.inbox(second)).isEmpty();
        assertThat(invitations.sent(clan)).isEmpty();
        memberships.setRole(owner, first.getId(), "MODERATOR");
        assertThat(memberships.roster(clan)).extracting(m -> m.getRole()).containsExactly("OWNER", "MODERATOR");
        management.transferOwnership(owner, first.getId());
        Clan after = clans.findById(clan.getId()).orElseThrow();
        assertThat(after.getOwner().getId()).isEqualTo(first.getId());
        assertThat(memberships.roster(clan)).extracting(m -> m.getUser().getId() + ":" + m.getRole()).containsExactly(first.getId() + ":OWNER", owner.getId() + ":MODERATOR");
        memberships.removeMember(first, owner.getId());
        assertThat(memberships.roster(clan)).hasSize(1);
        //System.out.println("events " + (events.count() - before));
        assertThat(events.count() - before).isEqualTo(10);
        List<String> types = events.findAll().stream().filter(e -> e.getActor() != null && (e.getActor().getId().equals(owner.getId()) || e.getActor().getId().equals(first.getId()) || e.getActor().getId().equals(second.getId()))).map(ClanEvent::getEventType).toList();
        assertThat(types).contains("InvitationDeclined", "MemberRoleChanged", "OwnershipTransferred", "MemberRemoved");
    }

    @Test
    @DisplayName("a loaded owner cannot found a second clan")
    void loadedOwnerCannotCreateAnother() {
        Clan first = clans.findAll().get(0);
        ClanForm form = new ClanForm();
        form.setName("Second " + System.nanoTime());
        form.setTag(freeTag());
        assertThatThrownBy(() -> management.create(first.getOwner(),form))
                .isInstanceOf(ClanRuleViolation.class)
                .hasMessageContaining("already in a clan");
    }

    @Test
    @DisplayName("region, image, message of the day and calendar all land in the database and feed the bell")
    void designExtrasLandInTheDatabase() {
        String stamp = Long.toString(System.nanoTime());
        AppUser owner = users.save(new AppUser("it-owner-" + stamp, "it_owner_" + stamp, "local", null));
        ClanForm form = new ClanForm();
        form.setName("Extras Clan " + stamp);
        form.setTag(freeTag());
        form.setRegion("Oceania");
        Clan clan = management.create(owner, form);
        assertThat(clans.findById(clan.getId()).orElseThrow().getRegion()).isEqualTo("Oceania");
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 13, 'I', 'H', 'D', 'R'};
        management.setLogo(owner, png);
        Optional<ClanLogo> logo = management.logo(clan.getId(), Optional.empty());
        assertThat(logo).isPresent();
        assertThat(logo.get().getContentType()).isEqualTo("image/png");
        assertThat(logo.get().getContent()).isEqualTo(png);
        assertThat(clans.findById(clan.getId()).orElseThrow().isHasLogo()).isTrue();
        management.updateMotd(owner, "Finals this weekend.");
        assertThat(clans.findById(clan.getId()).orElseThrow().getMotd()).isEqualTo("Finals this weekend.");
        String tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1).toString();
        calendar.add(owner, "Clan Ladder Night", tomorrow, "20:00");
        assertThat(calendar.upcoming(clan, 5)).extracting(e -> e.getTitle()).containsExactly("Clan Ladder Night");
        assertThat(calendar.upcoming(clan, 5).get(0).getStartsAtUtc().getHour()).isEqualTo(20);
        List<Notice> bell = notifications.forClan(clan, 5);
        //System.out.println("bell " + bell.size());
        assertThat(bell).extracting(Notice::getText).contains("Clan Ladder Night was added to the events calendar.", owner.getDisplayName() + " posted a new message of the day.", owner.getDisplayName() + " changed the clan image.");
        calendar.remove(owner, calendar.upcoming(clan, 5).get(0).getId());
        assertThat(calendar.upcoming(clan, 5)).isEmpty();
        management.removeLogo(owner);
        assertThat(management.logo(clan.getId(), Optional.empty())).isEmpty();
        assertThat(events.findAll().stream().filter(e -> e.getActor() != null && e.getActor().getId().equals(owner.getId())).map(ClanEvent::getEventType).toList())
                .contains("ClanLogoUpdated", "ClanMotdUpdated", "CalendarEventAdded", "CalendarEventRemoved", "ClanLogoRemoved");
    }
}
