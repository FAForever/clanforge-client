//SPDX-License-Identifier: MIT
package com.faforever.clan_management.api;

//what POST /api/reports takes: the clan, which of the three categories, and what is wrong in the reporters words
public record ReportRequest(Long clanId, String category, String reason) {
}
