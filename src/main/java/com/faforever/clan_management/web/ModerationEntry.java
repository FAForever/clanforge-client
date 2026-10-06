//SPDX-License-Identifier: MIT
package com.faforever.clan_management.web;

import java.time.OffsetDateTime;

import com.faforever.clan_management.entity.ModerationAction;

import tools.jackson.databind.ObjectMapper;

// One line of a clan's moderation history as the admin page shows it: what was done, by whom,
// why, and for a rename or retag what the clan was called before, read from the snapshot the
// moderation row keeps. tone picks the colour of the timeline marker.
public record ModerationEntry(String action, String label, String tone, String reason, String moderator, OffsetDateTime performedAt, Long reportId, String before) {

    static ModerationEntry from(ModerationAction a, ObjectMapper json) {
        String action = a.getAction();
        return new ModerationEntry(action, label(action), tone(action), a.getReason(), a.getModerator().getDisplayName(), a.getPerformedAt(),
                a.getReport() == null ? null : a.getReport().getId(), before(action, a.getPrevious(), json));
    }

    private static String label(String action) {
        return switch (action) {
            case "SUSPEND" -> "Suspended";
            case "UNSUSPEND" -> "Unsuspended";
            case "RENAME" -> "Renamed";
            case "RETAG" -> "Retagged";
            case "DELETE" -> "Deleted";
            default -> action;
        };
    }

    private static String tone(String action) {
        return switch (action) {
            case "SUSPEND" -> "warn";
            case "UNSUSPEND" -> "ok";
            case "DELETE" -> "danger";
            default -> "accent";
        };
    }

    // was "Old Name" for a rename and was [OLD] for a retag; nothing for the other actions or a snapshot that cannot be read
    private static String before(String action, String previous, ObjectMapper json) {
        boolean rename = "RENAME".equals(action);
        if (previous == null || !(rename || "RETAG".equals(action))) return null;
        try {
            String old = json.readTree(previous).path(rename ? "name" : "tag").asText("");
            if (old.isEmpty()) return null;
            return rename ? "was “" + old + "”" : "was [" + old + "]";
        } catch (RuntimeException e) {
            return null;
        }
    }
}
