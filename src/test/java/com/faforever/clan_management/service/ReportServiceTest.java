//SPDX-License-Identifier: MIT
package com.faforever.clan_management.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.entity.ClanReport;
import com.faforever.clan_management.repository.ClanMembershipRepository;
import com.faforever.clan_management.repository.ClanReportRepository;
import com.faforever.clan_management.repository.ClanRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

//who may report which clan, what a report has to carry, and how an administrator closes one
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ClanReportRepository reports;
    @Mock
    private ClanRepository clans;
    @Mock
    private ClanMembershipRepository memberships;
    @InjectMocks
    private ReportService service;

    private final AppUser kai = Fixtures.user(1L, "kai");
    private final AppUser sam = Fixtures.user(2L,"sam");
    private final Clan clan = Fixtures.clan(7L, "Bad Company", "BC",kai, true);

    private Clan privateClan() {
        Clan c = Fixtures.clan(8L, "Quiet Ones", "QT", kai, true);
        c.setVisibility("PRIVATE");
        return c;
    }

    @Test
    @DisplayName("a report on a public clan is saved open, with the category in capitals and the reason trimmed")
    void fileSavesOpenReport() {
        given(clans.existsByIdAndVisibilityAndDeletedAtIsNull(7L, "PUBLIC")).willReturn(true);
        given(clans.lockLive(7L)).willReturn(Optional.of(clan));
        given(reports.existsByClanIdAndReporterIdAndState(7L, 2L, "OPEN")).willReturn(false);
        given(reports.save(any(ClanReport.class))).willAnswer(a -> a.getArgument(0));
        ClanReport r = service.file(sam, 7L, " offensive_name ", "  the name is a slur  ");
        assertThat(r.getState()).isEqualTo("OPEN");
        assertThat(r.getCategory()).isEqualTo("OFFENSIVE_NAME");
        assertThat(r.getReason()).isEqualTo("the name is a slur");
        assertThat(r.getClan()).isSameAs(clan);
        assertThat(r.getReporter()).isSameAs(sam);
        assertThat(r.getCreatedAt()).isNotNull();
        assertThat(r.getResolvedAt()).isNull();
    }

    @Test
    @DisplayName("a missing clan, no clan at all and a private one seen from outside all read as not existing")
    void fileHidesWhatTheReporterCannotSee() {
        given(clans.existsByIdAndVisibilityAndDeletedAtIsNull(99L, "PUBLIC")).willReturn(false);
        assertThatThrownBy(() -> service.file(sam, 99L, "OFFENSIVE_TAG","bad tag")).isInstanceOf(ClanNotFound.class).hasMessage("That clan does not exist.");
        assertThatThrownBy(() -> service.file(sam,null,"OFFENSIVE_TAG", "bad tag")).isInstanceOf(ClanNotFound.class).hasMessage("That clan does not exist.");
        given(clans.existsByIdAndVisibilityAndDeletedAtIsNull(8L, "PUBLIC")).willReturn(false);
        given(memberships.existsByClanIdAndUserIdAndLeftAtIsNull(8L,2L)).willReturn(false);
        assertThatThrownBy(() -> service.file(sam,8L, "OFFENSIVE_TAG", "bad tag")).isInstanceOf(ClanNotFound.class).hasMessage("That clan does not exist.");
        verify(reports,never()).save(any());
        verify(clans,never()).lockLive(any()); //turned away before the lock, so an outsider never waits on a clan it cannot see
    }

    @Test
    @DisplayName("a member of a private clan can report it")
    void memberCanReportTheirPrivateClan() {
        Clan quiet = privateClan();
        given(clans.existsByIdAndVisibilityAndDeletedAtIsNull(8L, "PUBLIC")).willReturn(false);
        given(memberships.existsByClanIdAndUserIdAndLeftAtIsNull(8L, 2L)).willReturn(true);
        given(clans.lockLive(8L)).willReturn(Optional.of(quiet));
        given(memberships.findActiveByClanIdAndUserId(8L, 2L)).willReturn(Optional.of(Fixtures.membership(6L, quiet, sam,"MEMBER")));
        given(reports.existsByClanIdAndReporterIdAndState(8L, 2L, "OPEN")).willReturn(false);
        given(reports.save(any(ClanReport.class))).willAnswer(a -> a.getArgument(0));
        assertThat(service.file(sam, 8L,"INAPPROPRIATE_CONTENT", "the description").getClan()).isSameAs(quiet);
    }

    @Test
    @DisplayName("a clan made private while the report waited for the lock reads as missing to an outsider once the lock is held")
    void reportChecksAgainAfterTheLock() {
        Clan quiet = privateClan();
        given(clans.existsByIdAndVisibilityAndDeletedAtIsNull(8L, "PUBLIC")).willReturn(true); //still public when it was asked
        given(clans.lockLive(8L)).willReturn(Optional.of(quiet));
        given(memberships.findActiveByClanIdAndUserId(8L, 2L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.file(sam, 8L, "OFFENSIVE_NAME", "too late")).isInstanceOf(ClanNotFound.class).hasMessage("That clan does not exist.");
        verify(reports, never()).save(any());
    }

    @Test
    @DisplayName("a report filed by the same person while this one waited for the lock is found once the lock is held")
    void duplicateCheckedAgainUnderTheLock() {
        given(clans.existsByIdAndVisibilityAndDeletedAtIsNull(7L, "PUBLIC")).willReturn(true);
        given(clans.lockLive(7L)).willReturn(Optional.of(clan));
        given(reports.existsByClanIdAndReporterIdAndState(7L, 2L, "OPEN")).willReturn(false, true); //none before the lock, one after it
        assertThatThrownBy(() -> service.file(sam, 7L, "OFFENSIVE_NAME","sent twice")).isInstanceOf(DuplicateReport.class).hasMessageContaining("already reported");
        verify(reports, never()).save(any());
    }

    @Test
    @DisplayName("the category has to be one of the three and the reason has to say something within the limit")
    void fileChecksCategoryAndReason() {
        assertThatThrownBy(() -> service.file(sam, 7L, "RUDE", "x")).isInstanceOf(ClanRuleViolation.class).hasMessageContaining("what the report is about");
        assertThatThrownBy(() -> service.file(sam, 7L, null, "x")).isInstanceOf(ClanRuleViolation.class).hasMessageContaining("what the report is about");
        assertThatThrownBy(() -> service.file(sam,7L, "OFFENSIVE_TAG", "   ")).isInstanceOf(ClanRuleViolation.class).hasMessageContaining("what is wrong");
        assertThatThrownBy(() -> service.file(sam,7L,"OFFENSIVE_TAG", "x".repeat(1001))).isInstanceOf(ClanRuleViolation.class).hasMessageContaining("1000 characters");
        verify(reports, never()).save(any());
        verify(clans, never()).lockLive(any()); //the report itself is checked before the clan is even looked at
    }

    @Test
    @DisplayName("one open report per person per clan")
    void fileRefusesASecondOpenReport() {
        given(clans.existsByIdAndVisibilityAndDeletedAtIsNull(7L, "PUBLIC")).willReturn(true);
        given(reports.existsByClanIdAndReporterIdAndState(7L, 2L, "OPEN")).willReturn(true);
        assertThatThrownBy(() -> service.file(sam, 7L, "OFFENSIVE_NAME", "still there")).isInstanceOf(DuplicateReport.class).hasMessageContaining("already reported");
        verify(reports, never()).save(any());
        verify(clans, never()).lockLive(any());
    }

    @Test
    @DisplayName("the queue is open reports unless another state or ALL is asked for, and an unknown state is refused")
    void byStatePicksTheQueue() {
        Pageable page = PageRequest.of(0,20);
        given(reports.findByStateNewestFirst("OPEN", page)).willReturn(new PageImpl<>(List.of(Fixtures.report(3L, clan, sam, "OFFENSIVE_NAME")), page, 1));
        given(reports.findByStateNewestFirst("DISMISSED", page)).willReturn(new PageImpl<>(List.of(),page, 0));
        given(reports.findAllNewestFirst(page)).willReturn(new PageImpl<>(List.of(), page, 0));
        assertThat(service.byState(null, page).getTotalElements()).isEqualTo(1);
        assertThat(service.byState(" ", page).getTotalElements()).isEqualTo(1);
        assertThat(service.byState("dismissed", page).getTotalElements()).isZero();
        assertThat(service.byState("all",page).getTotalElements()).isZero();
        assertThatThrownBy(() -> service.byState("LOST", page)).isInstanceOf(ClanRuleViolation.class).hasMessageContaining("open, resolved or dismissed");
    }

    @Test
    @DisplayName("closing marks who settled it and when, and a closed report cannot be closed again")
    void closeSettlesOnce() {
        ClanReport first = Fixtures.report(3L, clan, sam, "OFFENSIVE_NAME");
        ClanReport second = Fixtures.report(4L, clan,sam, "OFFENSIVE_TAG");
        given(reports.lockById(3L)).willReturn(Optional.of(first));
        given(reports.lockById(4L)).willReturn(Optional.of(second));
        given(reports.lockById(5L)).willReturn(Optional.empty());
        service.close(kai, 3L,false);
        service.close(kai, 4L, true);
        assertThat(first.getState()).isEqualTo("RESOLVED");
        assertThat(first.getResolvedBy()).isSameAs(kai);
        assertThat(first.getResolvedAt()).isNotNull();
        assertThat(second.getState()).isEqualTo("DISMISSED");
        assertThatThrownBy(() -> service.close(kai, 3L, true)).isInstanceOf(ClanRuleViolation.class).hasMessageContaining("already closed");
        assertThatThrownBy(() -> service.close(kai, 5L,false)).isInstanceOf(ClanRuleViolation.class).hasMessageContaining("does not exist");
    }

    @Test
    @DisplayName("a reason of exactly 1000 characters is kept, and the category folds the same way in any language")
    void reasonLimitAndLocale() {
        given(clans.existsByIdAndVisibilityAndDeletedAtIsNull(7L, "PUBLIC")).willReturn(true);
        given(clans.lockLive(7L)).willReturn(Optional.of(clan));
        given(reports.existsByClanIdAndReporterIdAndState(7L, 2L, "OPEN")).willReturn(false);
        given(reports.save(any(ClanReport.class))).willAnswer(a -> a.getArgument(0));
        assertThat(service.file(sam, 7L, "OFFENSIVE_NAME", "x".repeat(1000)).getReason()).hasSize(1000);
        Locale was = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            assertThat(service.file(sam, 7L, "inappropriate_content","the description").getCategory()).isEqualTo("INAPPROPRIATE_CONTENT");
            given(reports.findByStateNewestFirst("DISMISSED", PageRequest.of(0, 5))).willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 5), 0));
            assertThat(service.byState("dismissed", PageRequest.of(0, 5)).getTotalElements()).isZero();
        } finally {
            Locale.setDefault(was);
        }
    }
}
