package com.faforever.clan_management.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.entity.CalendarEvent;
import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.entity.ClanMembership;
import com.faforever.clan_management.repository.CalendarEventRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

//the clan calendar: who may add and remove, and that a date and time typed as utc stay utc
@ExtendWith(MockitoExtension.class)
class CalendarServiceTest {

    @Mock
    private CalendarEventRepository calendar;
    @Mock
    private MembershipService membershipService;
    @Mock
    private ClanEventService events;
    @InjectMocks
    private CalendarService service;

    private final AppUser kai = Fixtures.user(1L, "kai");
    private final Clan clan = Fixtures.clan(7L, "Bad Company", "BC", kai,false);
    private final ClanMembership ownerRow = Fixtures.membership(5L, clan, kai, "OWNER");

    @Test
    @DisplayName("a plain member cannot add an event")
    void memberCannotAdd() {
        given(membershipService.manager(eq(kai), anyString())).willThrow(new ClanAccessRefused("Only the owner and moderators can add events."));
        assertThatThrownBy(() -> service.add(kai,"Ladder Night","2026-10-03","20:00"))
                .isInstanceOf(ClanRuleViolation.class)
                .hasMessageContaining("Only the owner and moderators");
        verify(calendar, never()).save(any());
    }

    @Test
    @DisplayName("a missing or malformed date is refused before anything is saved")
    void badDateRefused() {
        given(membershipService.manager(eq(kai),anyString())).willReturn(ownerRow);
        assertThatThrownBy(() -> service.add(kai, "Ladder Night","next friday", "20:00"))
                .isInstanceOf(ClanRuleViolation.class)
                .hasMessageContaining("date and a time");
        verify(calendar, never()).save(any());
    }

    @Test
    @DisplayName("an event is saved at the typed utc time and written to the history")
    void addSavesUtcAndEvent() {
        given(membershipService.manager(eq(kai), anyString())).willReturn(ownerRow);
        given(calendar.save(any(CalendarEvent.class))).willAnswer(a -> a.getArgument(0));
        CalendarEvent ev = service.add(kai, "  Ladder Night ", "2026-10-03", "20:00");
        //System.out.println("starts " + ev.getStartsAt());
        assertThat(ev.getTitle()).isEqualTo("Ladder Night");
        assertThat(ev.getStartsAt()).isEqualTo(OffsetDateTime.of(2026, 10, 3, 20, 0, 0, 0, ZoneOffset.UTC));
        assertThat(ev.getClan()).isSameAs(clan);
        verify(events).record(eq("clan_calendar_event"),any(), eq("CalendarEventAdded"), any(), eq(kai));
    }

    @Test
    @DisplayName("a manager can only remove events of their own clan")
    void removeRefusesOtherClan() {
        given(membershipService.manager(eq(kai), anyString())).willReturn(ownerRow);
        Clan other = Fixtures.clan(9L,"Other", "OTH", Fixtures.user(2L, "sam"), false);
        CalendarEvent theirs = new CalendarEvent(other, "Their night", OffsetDateTime.now().plusDays(1), kai);
        ReflectionTestUtils.setField(theirs, "id", 4L);
        given(calendar.findById(4L)).willReturn(Optional.of(theirs));
        assertThatThrownBy(() -> service.remove(kai, 4L))
                .isInstanceOf(ClanAccessRefused.class)
                .hasMessageContaining("another clan");
        verify(calendar,never()).delete(any());
    }

    @Test
    @DisplayName("removing deletes the row and keeps the title in the history")
    void removeDeletesAndRecords() {
        given(membershipService.manager(eq(kai),anyString())).willReturn(ownerRow);
        CalendarEvent mine = new CalendarEvent(clan, "Co-op Night", OffsetDateTime.now().plusDays(2),kai);
        ReflectionTestUtils.setField(mine, "id", 5L);
        given(calendar.findById(5L)).willReturn(Optional.of(mine));
        service.remove(kai, 5L);
        verify(calendar).delete(mine);
        verify(events).record(eq("clan_calendar_event"),eq(5L), eq("CalendarEventRemoved"), any(), eq(kai));
    }
}
