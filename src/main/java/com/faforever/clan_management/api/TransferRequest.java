//SPDX-License-Identifier: MIT
package com.faforever.clan_management.api;

// the user id of the current clan member who should become the new owner
public record TransferRequest(Long userId) {}
