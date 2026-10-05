//SPDX-License-Identifier: MIT
package com.faforever.clan_management.api;

//what the admin clan actions take: a new name or tag where the action needs one, an optional reason for the moderation log, and the report it answers if any
public record AdminActionRequest(String name, String tag, String reason, Long reportId) {
}
