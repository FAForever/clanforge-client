package com.faforever.clan_management.service;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.entity.ClanEvent;
import com.faforever.clan_management.repository.ClanEventRepository;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

//the bell turns history rows into sentences, and stays quiet about rows nobody needs to hear about
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private ClanEventRepository events;

    private final AppUser sam = Fixtures.user(2L, "Sam");
    private final Clan clan = Fixtures.clan(7L, "Bad Company", "BC", Fixtures.user(1L, "kai"), false);

    private NotificationService service() {
        return new NotificationService(events, new ObjectMapper());
    }

    @Test
    @DisplayName("each event type reads as one plain sentence naming who did it")
    void sentences() {
        NotificationService s = service();
        assertThat(s.sentence(new ClanEvent("clan_invitation",3L,"InvitationAccepted", "{}", sam))).isEqualTo("Sam accepted their invite and joined the clan.");
        assertThat(s.sentence(new ClanEvent("clan_membership",3L, "MemberLeft", "{}", sam))).isEqualTo("Sam left the clan.");
        assertThat(s.sentence(new ClanEvent("clan", 7L, "ClanMotdUpdated","{}",sam))).isEqualTo("Sam posted a new message of the day.");
        assertThat(s.sentence(new ClanEvent("clan_calendar_event", 9L,"CalendarEventAdded","{\"title\": \"Clan Ladder Night\", \"clanId\": 7}", sam))).isEqualTo("Clan Ladder Night was added to the events calendar.");
        assertThat(s.sentence(new ClanEvent("clan",7L,"ClanFounderRecorded","{}", null))).isNull();
        //System.out.println("sentences ok");
    }

    @Test
    @DisplayName("the bell lists the clan's latest rows in order and skips the silent ones")
    void forClanSkipsSilentRows() {
        given(events.findLatestForClan(7L, 5)).willReturn(List.of(
                new ClanEvent("clan_membership", 3L, "MemberJoined", "{\"clanId\": 7}",sam),
                new ClanEvent("clan", 7L, "ClanFounderRecorded", "{}", null),
                new ClanEvent("clan", 7L, "ClanLogoUpdated","{}", sam)));
        List<Notice> notices = service().forClan(clan,5);
        assertThat(notices).extracting(Notice::getText).containsExactly("Sam joined the clan.", "Sam changed the clan image.");
        assertThat(notices.get(0).getLink()).isEqualTo("/clanhome");
        assertThat(notices.get(0).getWhen()).isEqualTo("just now");
    }
}
