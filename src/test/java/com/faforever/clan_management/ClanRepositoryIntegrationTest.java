package com.faforever.clan_management;

import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.repository.AppUserRepository;
import com.faforever.clan_management.repository.ClanRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the application actually reads clan data out of PostgreSQL.
 *
 * This is an integration test: it needs a real database with the schema applied
 * and some data loaded (docker compose up -d, then db/load_dump.py). It skips
 * itself unless SPRING_DATASOURCE_URL is set, so it never fails the build for
 * someone who has not started a database.
 *
 * Run it with:
 *   $env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/clan_management"
 *   $env:SPRING_DATASOURCE_USERNAME="clanadmin"
 *   $env:SPRING_DATASOURCE_PASSWORD="devpassword"
 *   ./mvnw test -Dtest=ClanRepositoryIntegrationTest
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
class ClanRepositoryIntegrationTest {

    @Autowired
    private ClanRepository clanRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Test
    @DisplayName("reads clans out of PostgreSQL")
    void readsClansFromDatabase() {
        List<Clan> clans = clanRepository.findAll();

        assertThat(clans)
                .as("no clans found - is the database loaded? run db/load_dump.py")
                .isNotEmpty();
    }

    @Test
    @DisplayName("clan rows come back fully populated")
    void clanFieldsArePopulated() {
        Clan clan = clanRepository.findAll().get(0);

        assertThat(clan.getId()).isNotNull();
        assertThat(clan.getName()).isNotBlank();
        assertThat(clan.getTag()).isNotBlank();
        assertThat(clan.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("the clan to owner relationship resolves across tables")
    void clanOwnerRelationshipResolves() {
        // A plain findAll() only proves one table can be read. Following the
        // owner association proves the foreign key mapping works too, which is
        // where entity/schema mismatches actually show up.
        Clan clan = clanRepository.findAll().get(0);

        assertThat(clan.getOwner())
                .as("clan owner should resolve to an app_user row")
                .isNotNull();
        assertThat(clan.getOwner().getId()).isNotNull();
        assertThat(appUserRepository.findById(clan.getOwner().getId())).isPresent();
    }
}
