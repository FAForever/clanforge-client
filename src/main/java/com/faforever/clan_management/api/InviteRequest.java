//SPDX-License-Identifier: MIT
package com.faforever.clan_management.api;

//what POST /api/clans/{id}/invite takes, the same two fields the invite form posts
public record InviteRequest(String username, String role) {
}
