//SPDX-License-Identifier: MIT
package com.faforever.clan_management.service;

// the clan named by a path id does not exist, or has been deleted; kept apart from a refusal
// so the REST API answers 404 "deleted or missing" rather than 403, as its OpenAPI contract documents
public class ClanNotFound extends RuntimeException {

    public ClanNotFound(String message) {
        super(message);
    }
}
