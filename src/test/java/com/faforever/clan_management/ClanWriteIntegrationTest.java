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
import com.faforever.clan_management.service.ClanCard;
import com.faforever.clan_management.service.ClanForm;
import com.faforever.clan_management.service.ClanManagementService;
import com.faforever.clan_management.service.ClanRuleViolation;
import com.faforever.clan_management.service.ClanService;
import com.faforever.clan_management.service.InvitationService;
import com.faforever.clan_management.service.MembershipService;

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
        ClanInvitation inv = invitations.invite(owner, guest.getDisplayName());
        assertThat(invitations.inbox(guest)).hasSize(1);
        assertThat(invitations.sent(clan)).hasSize(1);
        invitations.accept(guest, inv.getId());
        assertThat(memberships.roster(clan)).hasSize(2);
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
}
