package com.faforever.clan_management.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.entity.CalendarEvent;
import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.entity.ClanMembership;
import com.faforever.clan_management.repository.CalendarEventRepository;

@Service
public class CalendarService {

    static final int titleMax = 80;

    private final CalendarEventRepository calendar;
    private final MembershipService membershipService;
    private final ClanEventService events;

    public CalendarService(CalendarEventRepository calendar, MembershipService membershipService, ClanEventService events) {
        this.calendar = calendar;
        this.membershipService = membershipService;
        this.events = events;
    }

    public List<CalendarEvent> upcoming(Clan clan, int limit) {
        OffsetDateTime from = LocalDate.now(ZoneOffset.UTC).atStartOfDay().atOffset(ZoneOffset.UTC); //from the start of today in utc, the times are entered as utc
        return calendar.findUpcoming(clan.getId(), from, PageRequest.of(0,limit));
    }

    // title, date and time from the settings form, the time is utc as the form says
    @Transactional
    public CalendarEvent add(AppUser actor, String title, String date, String time) {
        ClanMembership me = membershipService.manager(actor,"Only the owner and moderators can add events.");
        String name = title == null ? "" : title.trim();
        if (name.isEmpty()) throw new ClanRuleViolation("Give the event a title.");
        if (name.length() > titleMax) throw new ClanRuleViolation("Event titles are " + titleMax + " characters at most.");
        OffsetDateTime startsAt;
        try {
            startsAt = LocalDate.parse(date).atTime(LocalTime.parse(time)).atOffset(ZoneOffset.UTC);
        } catch (RuntimeException e) {
            throw new ClanRuleViolation("Give the event a date and a time.");
        }
        CalendarEvent ev = calendar.save(new CalendarEvent(me.getClan(), name, startsAt, actor));
        events.record("clan_calendar_event", ev.getId(), "CalendarEventAdded", events.calendarPayload(ev), actor);
        //System.out.println("event " + name + " " + startsAt);
        return ev;
    }

    @Transactional
    public void remove(AppUser actor,Long id) {
        ClanMembership me = membershipService.manager(actor, "Only the owner and moderators can remove events.");
        CalendarEvent ev = calendar.findById(id).orElseThrow(() -> new ClanRuleViolation("That event does not exist."));
        if (!ev.getClan().getId().equals(me.getClan().getId())) throw new ClanAccessRefused("That event belongs to another clan.");
        Map<String, Object> payload = events.calendarPayload(ev); // taken before the row goes, the history keeps the title
        calendar.delete(ev);
        events.record("clan_calendar_event", ev.getId(), "CalendarEventRemoved", payload, actor);
    }
}
