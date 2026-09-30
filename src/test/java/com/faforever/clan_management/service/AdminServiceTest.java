//SPDX-License-Identifier: MIT
package com.faforever.clan_management.service;

import java.util.List;
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
        given(clans.findByIdAndDeletedAtIsNull(7L)).willReturn(Optional.of(clan));
        given(actions.save(any(ModerationAction.class))).willAnswer(a -> {
            ModerationAction saved = a.getArgument(0); //the id the database would hand out
            ReflectionTestUtils.setField(saved, "id", 31L);
            return saved;
        });
    }

    private JsonNode eventPayload() {
        ArgumentCaptor<ClanEvent> ev = ArgumentCaptor.forClass(ClanEvent.class);
        verify(eventRows).save(ev.capture());
        assertThat(ev.getValue().getEventType()).isEqualTo("ModerationActionPerformed");
        assertThat(ev.getValue().getEntityType()).isEqualTo("clan");
        assertThat(ev.getValue().getEntityId()).isEqualTo(7L);
        assertThat(ev.getValue().getActor()).isSameAs(admin);
        //System.out.println("event " + ev.getValue().getPayload());
        return json.readTree(ev.getValue().getPayload());
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
        given(clans.findByIdAndDeletedAtIsNull(7L)).willReturn(Optional.of(clan));
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
        assertThat(eventPayload().path("action").asText()).isEqualTo("DELETE");
    }

    @Test
    @DisplayName("an action that answers a report closes that report as resolved by the administrator")
    void linkedReportIsResolved() {
        saving();
        ClanReport report = Fixtures.report(5L, clan,Fixtures.user(3L, "lee"), "OFFENSIVE_NAME");
        given(reports.findById(5L)).willReturn(Optional.of(report));
        ModerationAction row = service.suspend(admin, 7L, null,5L);
        assertThat(row.getReport()).isSameAs(report);
        assertThat(report.getState()).isEqualTo("RESOLVED");
        assertThat(report.getResolvedBy()).isSameAs(admin);
        assertThat(eventPayload().path("reportId").asLong()).isEqualTo(5L);
    }

    @Test
    @DisplayName("a report about another clan, a closed one or a missing one stops the action before anything changes")
    void badReportStopsTheAction() {
        given(clans.findByIdAndDeletedAtIsNull(7L)).willReturn(Optional.of(clan));
        Clan other = Fixtures.clan(8L, "Other", "OTH", owner, false);
        ClanReport elsewhere = Fixtures.report(5L, other, Fixtures.user(3L, "lee"), "OFFENSIVE_TAG");
        ClanReport closed = Fixtures.report(6L, clan, Fixtures.user(3L,"lee"),"OFFENSIVE_TAG");
        closed.close("DISMISSED", admin);
        given(reports.findById(5L)).willReturn(Optional.of(elsewhere));
        given(reports.findById(6L)).willReturn(Optional.of(closed));
        given(reports.findById(9L)).willReturn(Optional.empty());
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
        given(clans.findByIdAndDeletedAtIsNull(99L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.suspend(admin, 99L, null, null)).isInstanceOf(ClanRuleViolation.class).hasMessage("That clan does not exist.");
        given(clans.findByIdAndDeletedAtIsNull(7L)).willReturn(Optional.of(clan));
        assertThatThrownBy(() -> service.suspend(admin,7L,"x".repeat(501), null)).isInstanceOf(ClanRuleViolation.class).hasMessageContaining("500 characters");
        assertThat(clan.getStatus()).isEqualTo("ACTIVE");
        verify(actions, never()).save(any());
    }

    @Test
    @DisplayName("the listing picks its query from the filter, escapes the search and refuses a filter it does not know")
    void listingFollowsTheFilter() {
        Pageable page = PageRequest.of(0,20);
        Page<Clan> one = new PageImpl<>(List.of(clan), page, 1);
        Page<ClanCard> cards = new PageImpl<>(List.of(new ClanCard(clan, 3L)), page, 1);
        given(clans.searchEvery("%bad!_co%",page)).willReturn(one);
        given(clans.searchLiveByStatus("%%","ACTIVE", page)).willReturn(one);
        given(clans.searchLiveByStatus("%%","SUSPENDED",page)).willReturn(one);
        given(clans.searchDeleted("%%", page)).willReturn(one);
        given(clanService.withCounts(one)).willReturn(cards);
        assertThat(service.clans(" Bad_Co ",null,page).getContent().get(0).getMemberCount()).isEqualTo(3L);
        assertThat(service.clans(null,"active", page).getTotalElements()).isEqualTo(1);
        assertThat(service.clans("", " Suspended ", page).getTotalElements()).isEqualTo(1);
        assertThat(service.clans("", "deleted", page).getTotalElements()).isEqualTo(1);
        assertThatThrownBy(() -> service.clans("","banned", page)).isInstanceOf(ClanRuleViolation.class).hasMessageContaining("all, active, suspended or deleted");
        //System.out.println("listing ok");
    }
}
