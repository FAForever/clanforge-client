//SPDX-License-Identifier: MIT
package com.faforever.clan_management.service;

// a refusal because the caller lacks the role or the ownership the action takes, kept apart from every other broken rule
public class ClanAccessRefused extends ClanRuleViolation {

    public ClanAccessRefused(String message) {
        super(message);
    }
}
