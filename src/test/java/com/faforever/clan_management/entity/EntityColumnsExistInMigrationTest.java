package com.faforever.clan_management.entity;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.model.relational.Namespace;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Table;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

// the real guard on the entity mapping is ClanManagementApplicationTests, which
// boots hibernate in validate mode against the schema flyway built. that one
// skips itself unless SPRING_DATASOURCE_URL points at a live database, so on a
// machine without one it proves nothing.
//
// this one needs nothing at all: no database, no container, no connection. it
// builds hibernate's mapping model in memory, asks it which table and column
// names the entities expect, and checks each one exists in the migrations.
//
// it catches the single most likely mistake in a hand-written mapping, a
// @Column name that does not match the migration. it does not check types, so
// it does not replace the validate test. it just fails faster, and it always
// runs.
class EntityColumnsExistInMigrationTest {

    private static final String[] ENTITIES = {
        "com.faforever.clan_management.entity.AppUser",
        "com.faforever.clan_management.entity.Clan",
        "com.faforever.clan_management.entity.ClanMembership",
        "com.faforever.clan_management.entity.ClanInvitation",
        "com.faforever.clan_management.entity.ClanEvent",
        "com.faforever.clan_management.entity.ClanReport",
        "com.faforever.clan_management.entity.ModerationAction",
        "com.faforever.clan_management.entity.ClanLogo",
        "com.faforever.clan_management.entity.CalendarEvent",
    };

    @Test
    void everyColumnHibernateExpectsExistsInTheMigration() throws Exception {
        Map<String, Set<String>> fromMigration = tablesInMigration(readMigrations());
        Map<String, Set<String>> fromEntities = tablesInMapping();

        assertFalse(fromMigration.isEmpty(), "no create table statements found in the migrations");
        assertEquals(ENTITIES.length, fromEntities.size(),
            "expected " + ENTITIES.length + " mapped tables, hibernate produced " + fromEntities.keySet());

        StringBuilder problems = new StringBuilder();

        for (Map.Entry<String, Set<String>> table : fromEntities.entrySet()) {
            String tableName = table.getKey();
            Set<String> actual = fromMigration.get(tableName);

            if (actual == null) {
                problems.append("table '").append(tableName)
                        .append("' is mapped by an entity but is never created by a migration. tables created: ")
                        .append(fromMigration.keySet()).append('\n');
                continue;
            }

            for (String column : table.getValue()) {
                if (!actual.contains(column)) {
                    problems.append("column '").append(tableName).append('.').append(column)
                            .append("' is mapped by an entity but no migration creates it. columns the migrations have: ")
                            .append(actual).append('\n');
                }
            }
        }

        if (problems.length() > 0) {
            fail("the entity mapping has drifted from the migrations:\n" + problems);
        }
    }

    // builds hibernate's mapping model with no connection and no database. the
    // dialect is stated explicitly and jdbc metadata access is switched off,
    // which is what lets this run anywhere.
    private Map<String, Set<String>> tablesInMapping() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
            .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
            .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
            .build();

        try {
            MetadataSources sources = new MetadataSources(registry);
            for (String entity : ENTITIES) {
                sources.addAnnotatedClassName(entity);
            }
            Metadata metadata = sources.buildMetadata();

            Map<String, Set<String>> tables = new LinkedHashMap<>();
            for (Namespace namespace : metadata.getDatabase().getNamespaces()) {
                for (Table table : namespace.getTables()) {
                    Set<String> columns = new LinkedHashSet<>();
                    for (Column column : table.getColumns()) {
                        columns.add(column.getName().toLowerCase());
                    }
                    tables.put(table.getName().toLowerCase(), columns);
                }
            }
            return tables;
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    //every V<n>__*.sql under db/migration in version order, joined into one script, so a column V2 adds counts
    private String readMigrations() throws IOException {
        Resource[] found = new PathMatchingResourcePatternResolver().getResources("classpath*:db/migration/V*__*.sql");
        if (found.length == 0) {
            throw new IllegalStateException(
                "no migrations on the classpath. they must live in "
                    + "src/main/resources/db/migration/ or flyway will not find them either.");
        }
        List<Resource> ordered = new ArrayList<>(Arrays.asList(found));
        ordered.sort(Comparator.comparingInt(r -> Integer.parseInt(r.getFilename().substring(1, r.getFilename().indexOf("__")))));
        StringBuilder all = new StringBuilder();
        for (Resource r : ordered) {
            try (InputStream in = r.getInputStream()) {
                all.append(new String(in.readAllBytes(), StandardCharsets.UTF_8)).append('\n');
            }
        }
        //System.out.println("migrations " + ordered.size());
        return all.toString();
    }

    private static final Pattern CREATE_TABLE = Pattern.compile(
        "create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?([a-z_][a-z0-9_]*)\\s*\\((.*?)\\)\\s*;",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern ADD_COLUMN = Pattern.compile(
        "alter\\s+table\\s+([a-z_][a-z0-9_]*)\\s+add\\s+column\\s+([a-z_][a-z0-9_]*)",
        Pattern.CASE_INSENSITIVE);

    private Map<String, Set<String>> tablesInMigration(String sql) {
        Map<String, Set<String>> tables = new LinkedHashMap<>();
        String clean = stripLineComments(sql);
        Matcher matcher = CREATE_TABLE.matcher(clean);

        while (matcher.find()) {
            String table = matcher.group(1).toLowerCase();
            Set<String> columns = new LinkedHashSet<>();

            for (String part : splitTopLevel(matcher.group(2))) {
                String trimmed = part.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String firstWord = trimmed.split("\\s+")[0].toLowerCase();
                // table-level clauses, not columns
                if (firstWord.equals("constraint") || firstWord.equals("primary")
                    || firstWord.equals("unique") || firstWord.equals("foreign")
                    || firstWord.equals("check")) {
                    continue;
                }
                columns.add(firstWord);
            }
            tables.put(table, columns);
        }
        Matcher added = ADD_COLUMN.matcher(clean); //columns a later migration bolts onto an earlier table
        while (added.find()) {
            Set<String> columns = tables.get(added.group(1).toLowerCase());
            if (columns != null) columns.add(added.group(2).toLowerCase());
        }
        return tables;
    }

    private String stripLineComments(String sql) {
        return sql.replaceAll("(?m)--.*$", "");
    }

    // splits a column list on commas that are not inside brackets, so that
    // CHECK (visibility IN ('PUBLIC', 'PRIVATE')) stays in one piece.
    private static List<String> splitTopLevel(String body) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            }
            if (c == ',' && depth == 0) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());
        return parts;
    }
}
