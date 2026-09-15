package com.faforever.clan_management.service;

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

    static String roleLabel(String role) {
        if (role == null || role.isEmpty()) return ""; // the role OWNER reads as Owner on the page
        return role.charAt(0)+role.substring(1).toLowerCase();
    }
}
