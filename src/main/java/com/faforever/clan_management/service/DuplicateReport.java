//SPDX-License-Identifier: MIT
package com.faforever.clan_management.service;

// a second open report by the same person on the same clan. A ClanRuleViolation so the web pages
// still flash it unchanged, while the REST API answers 409 (the conflict its OpenAPI contract documents).
public class DuplicateReport extends ClanRuleViolation {

    public DuplicateReport(String message) {
        super(message);
    }
}
