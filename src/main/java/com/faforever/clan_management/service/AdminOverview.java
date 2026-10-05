//SPDX-License-Identifier: MIT
package com.faforever.clan_management.service;

// the figures across the top of the admin pages, each one a count query
public record AdminOverview(long clans, long active, long suspended, long deleted, long openReports, long resolvedReports, long dismissedReports) {
}
