//SPDX-License-Identifier: MIT
package com.faforever.clan_management.api;

// the role to give a member, MODERATOR or MEMBER; MembershipService checks it
public record RoleRequest(String role) {}
