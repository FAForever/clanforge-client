package com.faforever.clan_management.service;

import java.time.Duration;
import java.time.OffsetDateTime;

//small text helpers the page carriers share
final class Names {

    private Names() {
    }

    static String initials(String name) {
        StringBuilder sb = new StringBuilder();//two characters for the avatar circle, the first two letters or digits of the name
        if (name != null) {
            for (int i = 0; i < name.length() && sb.length() < 2; i++) {
                char c = name.charAt(i);
                if (Character.isLetterOrDigit(c)) sb.append(Character.toUpperCase(c));
            }
        }
        //System.out.println("initials " + name + " " + sb);
        return sb.length() == 0 ? "??" : sb.toString();
    }

    //just now, 5m ago, 3h ago, 2d ago, the shape the mock-ups used
    static String ago(OffsetDateTime t) {
        if (t == null) return "";
        long minutes = Duration.between(t, OffsetDateTime.now()).toMinutes();
        //System.out.println("ago " + minutes);
        if (minutes < 1) return "just now";
        if (minutes < 60) return minutes + "m ago";
        if (minutes < 1440) return (minutes / 60) + "h ago";
        return (minutes / 1440) + "d ago";
    }

    static String roleLabel(String role) {
        if (role == null || role.isEmpty()) return ""; // the role OWNER reads as Owner on the page
        return role.charAt(0)+role.substring(1).toLowerCase();
    }
}
