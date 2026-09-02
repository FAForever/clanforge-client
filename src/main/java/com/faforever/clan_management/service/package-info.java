/**
 * Business rules. Everything that decides what a caller may see or do.
 *
 * This is the layer that has to hold, because it is the only one both the web
 * pages and the future REST API will share. ClanService is the pattern to copy:
 * the visibility rule lives there once, so no route can reach clan data and
 * forget to apply it.
 *
 * View-shaped data leaves as a small carrier such as ClanCard, so entities do
 * not grow presentation concerns and no lazy association is touched during
 * rendering (spring.jpa.open-in-view is off, so touching one throws).
 */
package com.faforever.clan_management.service;
