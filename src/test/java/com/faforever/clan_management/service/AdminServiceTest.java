//SPDX-License-Identifier: MIT
package com.faforever.clan_management.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.entity.ClanEvent;
import com.faforever.clan_management.entity.ClanReport;
import com.faforever.clan_management.entity.ModerationAction;
import com.faforever.clan_management.repository.ClanEventRepository;
import com.faforever.clan_management.repository.ClanReportRepository;
import com.faforever.clan_management.repository.ClanRepository;
import com.faforever.clan_management.repository.ModerationActionRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

//each admin action: what it changes, the moderation row it leaves, the history event it writes, and what it refuses
@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private ClanRepository clans;
    @Mock
    private ClanReportRepository reports;
    @Mock
    private ModerationActionRepository actions;
    @Mock
    private ClanService clanService;
    @Mock
    private ClanManagementService management;
    @Mock
    private ClanEventRepository eventRows;

    private final ObjectMapper json = new ObjectMapper();
    private AdminService service;
    private final AppUser admin = Fixtures.user(1L, "admin");
    private final AppUser owner = Fixtures.user(2L,"sam");
    private final Clan clan = Fixtures.clan(7L, "Bad Company", "BC", owner, true);

    @BeforeEach
    void setUp() {
        service = new AdminService(clans, reports,actions,clanService, management, new ClanEventService(eventRows, json), json);
    }

    private void saving() {
        given(clans.lockLive(7L)).willReturn(Optional.of(clan));
        given(actions.save(any(ModerationAction.class))).willAnswer(a -> {
            ModerationAction saved = a.getArgument(0); //the id the database would hand out
            ReflectionTestUtils.setField(saved, "id", 31L);
            return saved;
        });
    }

    //the two history rows one action writes, the clan event a consumer already follows and then the moderation event
    private List<ClanEvent> eventRows(String clanEvent) {
        ArgumentCaptor<ClanEvent> ev = ArgumentCaptor.forClass(ClanEvent.class);
        verify(eventRows,org.mockito.Mockito.times(2)).save(ev.capture());
        List<ClanEvent> rows = ev.getAllValues();
        assertThat(rows).extracting(ClanEvent::getEventType).containsExactly(clanEvent,"ModerationActionPerformed");
        for (ClanEvent e : rows) {
            assertThat(e.getEntityType()).isEqualTo("clan");
            assertThat(e.getEntityId()).isEqualTo(7L);
            assertThat(e.getActor()).isSameAs(admin);
        }
        //System.out.println("events " + rows.get(1).getPayload());
        return rows;
    }

    private JsonNode eventPayload() {
        return eventPayload("ClanUpdated");
    }

    private JsonNode eventPayload(String clanEvent) {
        return json.readTree(eventRows(clanEvent).get(1).getPayload());
    }

    @Test
    @DisplayName("suspending sets the status, keeps the old one in the moderation row and writes the history event")
    void suspendRecordsBeforeAndAfter() {
        saving();
        ModerationAction row = service.suspend(admin, 7L, "  offensive name  ",null);
        assertThat(clan.getStatus()).isEqualTo("SUSPENDED");
        assertThat(row.getAction()).isEqualTo("SUSPEND");
        assertThat(row.getReason()).isEqualTo("offensive name");
        assertThat(row.getModerator()).isSameAs(admin);
        assertThat(row.getReport()).isNull();
        assertThat(json.readTree(row.getPrevious()).path("status").asText()).isEqualTo("ACTIVE");
        JsonNode payload = eventPayload();
        assertThat(payload.path("status").asText()).isEqualTo("SUSPENDED");
        assertThat(payload.path("action").asText()).isEqualTo("SUSPEND");
        assertThat(payload.path("reason").asText()).isEqualTo("offensive name");
        assertThat(payload.path("moderationActionId").asLong()).isEqualTo(31L);
        assertThat(payload.path("reportId").isNull()).isTrue();
    }

    @Test
    @DisplayName("a clan is suspended once and lifted only while suspended")
    void suspendAndUnsuspendFollowTheStatus() {
        saving();
        assertThatThrownBy(() -> service.unsuspend(admin, 7L, null)).isInstanceOf(ClanRuleViolation.class).hasMessageContaining("not suspended");
        service.suspend(admin, 7L, null, null);
        assertThatThrownBy(() -> service.suspend(admin, 7L, null, null)).isInstanceOf(ClanRuleViolation.class).hasMessageContaining("already suspended");
        ModerationAction lifted = service.unsuspend(admin, 7L, " ");
        //System.out.println("lifted " + lifted.getAction() + " " + clan.getStatus());
        assertThat(clan.getStatus()).isEqualTo("ACTIVE");
        assertThat(lifted.getAction()).isEqualTo("UNSUSPEND");
        assertThat(lifted.getReason()).isNull();
        assertThat(json.readTree(lifted.getPrevious()).path("status").asText()).isEqualTo("SUSPENDED");
    }

    @Test
    @DisplayName("renaming runs the name rules against the clan and keeps the old name in the moderation row")
    void renameChecksAndRecords() {
        saving();
        ModerationAction row = service.rename(admin, 7L, "  Good Company ", "offensive name", null);
        verify(management).checkName("Good Company", clan);
        assertThat(clan.getName()).isEqualTo("Good Company");
        assertThat(row.getAction()).isEqualTo("RENAME");
        assertThat(json.readTree(row.getPrevious()).path("name").asText()).isEqualTo("Bad Company");
        assertThat(eventPayload().path("name").asText()).isEqualTo("Good Company");
    }

    @Test
    @DisplayName("a refused name or the same name changes nothing and writes nothing")
    void renameRefusedLeavesTheClanAlone() {
        given(clans.lockLive(7L)).willReturn(Optional.of(clan));
        willThrow(new ClanRuleViolation("The clan name 'Taken' is already in use.")).given(management).checkName("Taken",clan);
        assertThatThrownBy(() -> service.rename(admin, 7L, "Taken", null, null)).isInstanceOf(ClanRuleViolation.class).hasMessageContaining("already in use");
        assertThatThrownBy(() -> service.rename(admin,7L," Bad Company ",null, null)).isInstanceOf(ClanRuleViolation.class).hasMessageContaining("already the clan's name");
        assertThat(clan.getName()).isEqualTo("Bad Company");
        verify(actions,never()).save(any());
        verify(eventRows, never()).save(any());
    }

    @Test
    @DisplayName("retagging runs the tag rules and keeps the old tag in the moderation row")
    void retagChecksAndRecords() {
        saving();
        ModerationAction row = service.retag(admin,7L, " GC ", null, null);
        verify(management).checkTag("GC", clan);
        assertThat(clan.getTag()).isEqualTo("GC");
        assertThat(row.getAction()).isEqualTo("RETAG");
        assertThat(json.readTree(row.getPrevious()).path("tag").asText()).isEqualTo("BC");
        willThrow(new ClanRuleViolation("Clan tag must be 1 to 3 letters or numbers.")).given(management).checkTag("TOOLONG", clan);
        assertThatThrownBy(() -> service.retag(admin, 7L, "TOOLONG", null, null)).isInstanceOf(ClanRuleViolation.class).hasMessageContaining("1 to 3");
        assertThat(clan.getTag()).isEqualTo("GC");
    }

    @Test
    @DisplayName("deleting hands the clan to the shared soft delete and records DELETE")
    void deleteUsesTheSharedClose() {
        saving();
        given(management.close(clan)).willReturn(4);
        ModerationAction row = service.delete(admin, 7L, "abandoned and offensive", null);
        verify(management).close(clan);
        assertThat(row.getAction()).isEqualTo("DELETE");
        assertThat(json.readTree(row.getPrevious()).path("deletedAt").isNull()).isTrue();
        List<ClanEvent> rows = eventRows("ClanDeleted");
        assertThat(json.readTree(rows.get(0).getPayload()).path("membersRemoved").asInt()).isEqualTo(4);
        assertThat(json.readTree(rows.get(0).getPayload()).path("moderationAction").asText()).isEqualTo("DELETE");
        assertThat(json.readTree(rows.get(1).getPayload()).path("action").asText()).isEqualTo("DELETE");
    }

    @Test
    @DisplayName("an action that answers a report closes that report as resolved by the administrator")
    void linkedReportIsResolved() {
        saving();
        ClanReport report = Fixtures.report(5L, clan,Fixtures.user(3L, "lee"), "OFFENSIVE_NAME");
        given(reports.lockById(5L)).willReturn(Optional.of(report));
        ModerationAction row = service.suspend(admin, 7L, null,5L);
        assertThat(row.getReport()).isSameAs(report);
        assertThat(report.getState()).isEqualTo("RESOLVED");
        assertThat(report.getResolvedBy()).isSameAs(admin);
        assertThat(eventPayload().path("reportId").asLong()).isEqualTo(5L);
    }

    @Test
    @DisplayName("a report about another clan, a closed one or a missing one stops the action before anything changes")
    void badReportStopsTheAction() {
        given(clans.lockLive(7L)).willReturn(Optional.of(clan));
        Clan other = Fixtures.clan(8L, "Other", "OTH", owner, false);
        ClanReport elsewhere = Fixtures.report(5L, other, Fixtures.user(3L, "lee"), "OFFENSIVE_TAG");
        ClanReport closed = Fixtures.report(6L, clan, Fixtures.user(3L,"lee"),"OFFENSIVE_TAG");
        closed.close("DISMISSED", admin);
        given(reports.lockById(5L)).willReturn(Optional.of(elsewhere));
        given(reports.lockById(6L)).willReturn(Optional.of(closed));
        given(reports.lockById(9L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.suspend(admin, 7L,null, 5L)).isInstanceOf(ClanRuleViolation.class).hasMessageContaining("another clan");
        assertThatThrownBy(() -> service.delete(admin, 7L, null,6L)).isInstanceOf(ClanRuleViolation.class).hasMessageContaining("already closed");
        assertThatThrownBy(() -> service.retag(admin, 7L, "GC", null, 9L)).isInstanceOf(ClanRuleViolation.class).hasMessageContaining("report does not exist");
        assertThat(clan.getStatus()).isEqualTo("ACTIVE");
        assertThat(clan.getTag()).isEqualTo("BC");
        assertThat(elsewhere.getState()).isEqualTo("OPEN");
        verify(management, never()).close(any());
        verify(actions, never()).save(any());
        verify(eventRows, never()).save(any());
    }

    @Test
    @DisplayName("a missing or already deleted clan and an overlong reason are refused")
    void missingClanAndLongReason() {
        given(clans.lockLive(99L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.suspend(admin, 99L, null, null)).isInstanceOf(ClanRuleViolation.class).hasMessage("That clan does not exist.");
        given(clans.lockLive(7L)).willReturn(Optional.of(clan));
        assertThatThrownBy(() -> service.suspend(admin,7L,"x".repeat(501), null)).isInstanceOf(ClanRuleViolation.class).hasMessageContaining("500 characters");
        assertThat(clan.getStatus()).isEqualTo("ACTIVE");
        verify(actions, never()).save(any());
    }

    @Test
    @DisplayName("the listing picks its query from the filter, escapes the search and refuses a filter it does not know")
    void listingFollowsTheFilter() {
        Pageable page = PageRequest.of(0,20);
        Page<Clan> every = new PageImpl<>(List.of(clan), page, 1);
        Page<Clan> active = new PageImpl<>(java.util.Collections.nCopies(2, clan),page, 2); //a different size per query, so the page shows which query ran
        Page<Clan> suspended = new PageImpl<>(java.util.Collections.nCopies(3, clan), page, 3);
        Page<Clan> deleted = new PageImpl<>(java.util.Collections.nCopies(4, clan),page, 4);
        given(clans.searchEvery("%bad!_co%", page)).willReturn(every);
        given(clans.searchLiveByStatus("%%", "ACTIVE", page)).willReturn(active);
        given(clans.searchLiveByStatus("%%","SUSPENDED", page)).willReturn(suspended);
        given(clans.searchDeleted("%%", page)).willReturn(deleted);
        given(clanService.withCounts(any())).willAnswer(a -> ((Page<Clan>) a.getArgument(0)).map(c -> new ClanCard(c, 3L)));
        assertThat(service.clans(" Bad_Co ", null, page).getTotalElements()).isEqualTo(1);
        assertThat(service.clans(" Bad_Co ", null, page).getContent().get(0).getMemberCount()).isEqualTo(3L);
        assertThat(service.clans(null,"active", page).getTotalElements()).isEqualTo(2);
        assertThat(service.clans("", " Suspended ", page).getTotalElements()).isEqualTo(3);
        assertThat(service.clans("","deleted", page).getTotalElements()).isEqualTo(4);
        assertThatThrownBy(() -> service.clans("", "banned",page)).isInstanceOf(ClanRuleViolation.class).hasMessageContaining("all, active, suspended or deleted");
        //System.out.println("listing ok");
    }

    @Test
    @DisplayName("the filter and the search fold case the same way whatever the machine's language is")
    void listingIgnoresTheDefaultLocale() {
        Locale was = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR")); //turkish is where an i folds to a dotless one
        try {
            Pageable page = PageRequest.of(0, 20);
            given(clans.searchLiveByStatus("%iron%", "ACTIVE", page)).willReturn(new PageImpl<>(List.of(clan), page, 1));
            given(clanService.withCounts(any())).willAnswer(a -> ((Page<Clan>) a.getArgument(0)).map(c -> new ClanCard(c, 1L)));
            assertThat(service.clans("IRON","ACTIVE", page).getTotalElements()).isEqualTo(1);
        } finally {
            Locale.setDefault(was);
        }
    }

    @Test
    @DisplayName("every action writes the ClanUpdated a consumer of the history follows, with the clan as it is now")
    void actionsWriteClanUpdated() {
        saving();
        service.suspend(admin, 7L, null, null);
        List<ClanEvent> rows = eventRows("ClanUpdated");
        assertThat(json.readTree(rows.get(0).getPayload()).path("status").asText()).isEqualTo("SUSPENDED");
        assertThat(json.readTree(rows.get(0).getPayload()).has("action")).isFalse();
        assertThat(json.readTree(rows.get(0).getPayload()).path("moderationAction").asText()).isEqualTo("SUSPEND"); //so the bell leaves it to the moderation event
    }

    @Test
    @DisplayName("an action moves updated_at on the clan")
    void actionTouchesTheClan() {
        saving();
        OffsetDateTime old = OffsetDateTime.now().minusDays(3);
        ReflectionTestUtils.setField(clan,"updatedAt", old);
        service.rename(admin, 7L, "Good Company", null, null);
        assertThat(clan.getUpdatedAt()).isAfter(old);
    }

    @Test
    @DisplayName("rename, retag and delete each close the report they answer")
    void everyActionClosesItsReport() {
        saving();
        AppUser lee = Fixtures.user(3L, "lee");
        ClanReport forName = Fixtures.report(5L,clan, lee, "OFFENSIVE_NAME");
        ClanReport forTag = Fixtures.report(6L, clan,lee, "OFFENSIVE_TAG");
        ClanReport forContent = Fixtures.report(8L, clan, lee, "INAPPROPRIATE_CONTENT");
        given(reports.lockById(5L)).willReturn(Optional.of(forName));
        given(reports.lockById(6L)).willReturn(Optional.of(forTag));
        given(reports.lockById(8L)).willReturn(Optional.of(forContent));
        given(reports.lockOpenForClan(7L)).willReturn(List.of(forContent));
        assertThat(service.rename(admin,7L, "Good Company",null,5L).getReport()).isSameAs(forName);
        assertThat(service.retag(admin, 7L,"GC", null, 6L).getReport()).isSameAs(forTag);
        assertThat(service.delete(admin, 7L, null, 8L).getReport()).isSameAs(forContent);
        assertThat(List.of(forName, forTag, forContent)).extracting(ClanReport::getState).containsOnly("RESOLVED");
    }

    @Test
    @DisplayName("an admin delete settles every report still open on the clan, since nothing is left to act on")
    void deleteSettlesTheOtherReports() {
        saving();
        AppUser lee = Fixtures.user(3L, "lee");
        ClanReport one = Fixtures.report(5L, clan, lee, "OFFENSIVE_NAME");
        ClanReport two = Fixtures.report(6L, clan, Fixtures.user(4L, "kim"),"OFFENSIVE_TAG");
        given(reports.lockOpenForClan(7L)).willReturn(List.of(one, two));
        service.delete(admin, 7L, "abandoned", null);
        assertThat(one.getState()).isEqualTo("RESOLVED");
        assertThat(two.getState()).isEqualTo("RESOLVED");
        assertThat(two.getResolvedBy()).isSameAs(admin);
    }

    @Test
    @DisplayName("a reason of exactly 500 characters is kept and the same tag again is refused")
    void reasonLimitAndSameTag() {
        saving();
        assertThat(service.suspend(admin, 7L, "x".repeat(500), null).getReason()).hasSize(500);
        assertThatThrownBy(() -> service.retag(admin, 7L, " BC ", null, null)).isInstanceOf(ClanRuleViolation.class).hasMessageContaining("already the clan's tag");
        verify(management,never()).checkTag(any(),any());
    }
}
