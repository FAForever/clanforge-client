package com.faforever.clan_management.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.entity.ClanEvent;
import com.faforever.clan_management.repository.ClanEventRepository;

import tools.jackson.databind.ObjectMapper;

@Service
public class NotificationService {

    private final ClanEventRepository events;
    private final ObjectMapper json;

    public NotificationService(ClanEventRepository events, ObjectMapper json) {
        this.events = events;
        this.json = json;
    }

    // the bell reads the history table, so there is no second store of what happened
    public List<Notice> forClan(Clan clan,int limit) {
        List<Notice> out = new ArrayList<>();
        for (ClanEvent e : events.findLatestForClan(clan.getId(), limit)) {
            String text = sentence(e);
            if (text != null) out.add(new Notice(text, e.getOccurredAt(), link(e)));
        }
        //System.out.println("notices " + out.size());
        return out;
    }

    // one plain sentence per event type, null for the ones nobody needs to hear about
    String sentence(ClanEvent e) {
        String who = e.getActor() == null ? "Someone" : e.getActor().getDisplayName();
        switch (e.getEventType()) {
            case "ClanCreated": return who + " founded the clan.";
            case "ClanUpdated": return who + " updated the clan details.";
            case "ClanMotdUpdated": return who + " posted a new message of the day.";
            case "ClanLogoUpdated": return who + " changed the clan image.";
            case "ClanLogoRemoved": return who + " removed the clan image.";
            case "OwnershipTransferred": return who + " handed the clan to a new owner.";
            case "MemberJoined": return who + " joined the clan.";
            case "MemberLeft": return who + " left the clan.";
            case "MemberRemoved": return who + " removed a member.";
            case "MemberRoleChanged": return who + " changed a member's role.";
            case "InvitationCreated": return who + " invited a player.";
            case "InvitationAccepted": return who+" accepted their invite and joined the clan.";
            case "InvitationDeclined": return who + " declined their invite.";
            case "InvitationRevoked": return who+" revoked an invite.";
            case "CalendarEventAdded": return title(e) + " was added to the events calendar.";
            case "CalendarEventRemoved": return title(e) + " was taken off the events calendar.";
            default: return null;
        }
    }

    private String title(ClanEvent e) {
        try {
            return json.readTree(e.getPayload()).path("title").asText("An event");
        } catch (RuntimeException ex) {
            //System.out.println("bad payload " + e.getId());
            return "An event";
        }
    }

    private String link(ClanEvent e) {
        switch (e.getEventType()) {
            case "InvitationCreated":
            case "InvitationAccepted":
            case "InvitationDeclined":
            case "InvitationRevoked": return "/invitations";
            case "CalendarEventAdded":
            case "CalendarEventRemoved": return "/settings#events-section";
            default: return "/clanhome";
        }
    }
}
