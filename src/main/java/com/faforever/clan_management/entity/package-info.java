/**
 * JPA entities, one per table in the migrations.
 *
 * Shared by every lane, so treat it as frozen: changing a field here changes
 * what every other package sees. A change in this package is a change to the
 * schema, which means a new migration under src/main/resources/db/migration,
 * never an edit to one that has already been applied.
 *
 * EntityColumnsExistInMigrationTest fails the build if an entity names a column
 * no migration creates. It needs no database, so it runs on every machine.
 */
package com.faforever.clan_management.entity;
