/**
 * Spring Data repositories. One per aggregate root.
 *
 * Query methods filter in the query, not after paging, so page counts stay
 * correct once private or deleted rows are excluded. Anything a derived method
 * name cannot express belongs in an explicit @Query here rather than in Java in
 * the service.
 *
 * Repositories are not called from controllers. Everything reaching the database
 * goes through a service, so a visibility or permission rule has exactly one
 * place to live.
 */
package com.faforever.clan_management.repository;
