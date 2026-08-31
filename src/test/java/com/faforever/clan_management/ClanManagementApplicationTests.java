package com.faforever.clan_management;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Starts the whole application context, including JPA repositories.
 *
 * This previously excluded DataSourceAutoConfiguration so it could run without a
 * database. That only works while no bean needs a repository, which stopped being
 * true once the clan service was added - excluding the data source removed the
 * repositories the service depends on. Rather than layering on more exclusions,
 * the test now needs a real database and skips itself when one is not configured,
 * the same rule ClanRepositoryIntegrationTest uses.
 *
 * Testcontainers would remove the need for the guard by starting a throwaway
 * PostgreSQL per run; it is already in the planned stack.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
class ClanManagementApplicationTests {

    @Test
    void contextLoads() {
    }
}
