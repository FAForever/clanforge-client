package com.faforever.clan_management.service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.entity.CalendarEvent;
import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.entity.ClanEvent;
import com.faforever.clan_management.entity.ClanInvitation;
import com.faforever.clan_management.entity.ClanMembership;
import com.faforever.clan_management.repository.ClanEventRepository;

import tools.jackson.databind.ObjectMapper;

@Service
public class ClanEventService {

    private final ClanEventRepository events;
    private final ObjectMapper json;

    public ClanEventService(ClanEventRepository events,ObjectMapper json) {
        this.events = events;
        this.json = json;
    }

    //one row per state change, saved in the same transaction as the change so a consumer never sees one without the other
    public ClanEvent record(String entityType, Long entityId, String eventType, Map<String,Object> payload, AppUser actor) {
        String text = json.writeValueAsString(payload); //jackson 3 throws unchecked, so no try here
        //System.out.println("event " + eventType + " " + text);
        return events.save(new ClanEvent(entityType,entityId, eventType,text, actor));
    }

    public Map<String, Object> clanPayload(Clan c) {
        Map<String, Object> p = new LinkedHashMap<>(); // the whole clan row after the change, so a consumer that missed earlier events still knows its current shape
        p.put("id", c.getId());
        p.put("name", c.getName());
        p.put("tag",c.getTag());
        p.put("description", c.getDescription());
        p.put("ownerId", c.getOwner().getId());
        p.put("visibility", c.getVisibility());
        p.put("requiresInvitation", c.isRequiresInvitation());
        p.put("status", c.getStatus());
        p.put("region", c.getRegion());
        p.put("motd", c.getMotd());
        p.put("motdUpdatedAt", text(c.getMotdUpdatedAt()));
        p.put("logoUpdatedAt", text(c.getLogoUpdatedAt()));
        p.put("createdAt",text(c.getCreatedAt()));
        p.put("updatedAt", text(c.getUpdatedAt()));
        p.put("deletedAt", text(c.getDeletedAt()));
        return p;
    }

    public Map<String, Object> membershipPayload(ClanMembership m) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", m.getId());
        p.put("clanId", m.getClan().getId());
        p.put("userId", m.getUser().getId());
        p.put("role",m.getRole());
        p.put("joinedAt", text(m.getJoinedAt()));
        p.put("leftAt", text(m.getLeftAt()));
        //System.out.println("membership payload " + p);
        return p;
    }

    public Map<String, Object> invitationPayload(ClanInvitation i) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", i.getId());
        p.put("clanId", i.getClan().getId());
        p.put("inviteeId", i.getInvitee().getId());
        p.put("inviterId",i.getInviter().getId());
        p.put("state", i.getState());
        p.put("role", i.getRole());
        p.put("createdAt", text(i.getCreatedAt()));
        p.put("expiresAt", text(i.getExpiresAt()));
        p.put("respondedAt", text(i.getRespondedAt()));
        return p;
    }

    public Map<String, Object> calendarPayload(CalendarEvent e) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", e.getId());
        p.put("clanId", e.getClan().getId());
        p.put("title", e.getTitle());
        p.put("startsAt", text(e.getStartsAt()));
        p.put("createdBy", e.getCreatedBy() == null ? null : e.getCreatedBy().getId());
        //System.out.println("calendar payload " + p);
        return p;
    }

    // iso text rather than a jackson date setting, so the payload reads the same whatever the mapper is configured to do
    private String text(OffsetDateTime t) {
        return t == null ? null : t.toString();
    }
}
