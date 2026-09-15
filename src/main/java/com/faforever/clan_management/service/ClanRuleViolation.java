package com.faforever.clan_management.service;

//thrown when a caller asks for something the clan rules refuse, the message is shown to them as it is
public class ClanRuleViolation extends RuntimeException {

    public ClanRuleViolation(String message) {
        super(message);
    }
}
