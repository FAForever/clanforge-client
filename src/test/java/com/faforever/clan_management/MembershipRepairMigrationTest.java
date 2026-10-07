//SPDX-License-Identifier: MIT
package com.faforever.clan_management;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.repository.AppUserRepository;
import com.faforever.clan_management.repository.ClanRepository;
import com.faforever.clan_management.service.ClanForm;
import com.faforever.clan_management.service.ClanManagementService;

import static org.assertj.core.api.Assertions.assertThat;

//V4 run again by hand over rows set up to need it, inside a transaction that is rolled back
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
@Transactional
class MembershipRepairMigrationTest {

    @Autowired
    private AppUserRepository users;
    @Autowired
    private ClanRepository clans;
    @Autowired
    private ClanManagementService management;
    @Autowired
    private JdbcTemplate jdbc;

    private String freeTag() {
        for (String t : List.of("ZR9","ZR8", "ZR7", "XR9", "XR8")) {
            if (clans.findByTagIgnoreCaseAndDeletedAtIsNull(t).isEmpty()) return t;
        }
        throw new IllegalStateException("every candidate tag is taken");
    }

    private Clan clanOf(AppUser owner) {
        ClanForm form = new ClanForm();
        form.setName("Repair Case "+System.nanoTime());
        form.setTag(freeTag());
        return management.create(owner, form);
    }

    private AppUser someone(String role) {
        String stamp = Long.toString(System.nanoTime());
        return users.save(new AppUser("repair-"+role + "-" + stamp, "repair_" + role + "_" + stamp, "local", null));
    }

    @Test
    @DisplayName("a membership left active in a deleted clan is closed when the clan was deleted, and one in a live clan is left alone")
    void strandedMembershipIsClosed() throws Exception {
        AppUser owner = someone("owner");
        AppUser stranded = someone("stranded");
        AppUser closedBefore = someone("left");
        Clan gone = clanOf(owner);
        jdbc.update("insert into clan_membership (clan_id, user_id, role, joined_at) values (?, ?, 'MEMBER', now() - interval '3 days')", gone.getId(), stranded.getId());
        jdbc.update("insert into clan_membership (clan_id, user_id, role, joined_at, left_at) values (?, ?, 'MEMBER', now() - interval '3 days', now() - interval '2 days')", gone.getId(), closedBefore.getId()); //closed properly before the delete
        jdbc.update("update clan_membership set joined_at = now() - interval '3 days' where clan_id = ? and user_id = ?",gone.getId(), owner.getId());
        jdbc.update("update clan set deleted_at = now() - interval '1 day' where id = ?",gone.getId()); //deleted without closing its rows, the state a race left behind
        Clan live = clanOf(someone("other"));
        String sql = new String(getClass().getResourceAsStream("/db/migration/V4__close_memberships_of_deleted_clans.sql").readAllBytes(), StandardCharsets.UTF_8);
        jdbc.execute(sql);
        assertThat(jdbc.queryForObject("select count(*) from clan_membership where clan_id = ? and left_at is null", Integer.class, gone.getId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from clan_membership m join clan c on c.id = m.clan_id where m.clan_id = ? and m.user_id in (?, ?) and m.left_at = c.deleted_at", Integer.class,gone.getId(), owner.getId(), stranded.getId())).isEqualTo(2); //closed at the delete, which came after they joined
        assertThat(jdbc.queryForObject("select count(*) from clan_membership where clan_id = ? and user_id = ? and left_at = now() - interval '2 days'", Integer.class, gone.getId(), closedBefore.getId())).isEqualTo(1); //the row that was already closed keeps its time
        assertThat(jdbc.queryForObject("select count(*) from clan_membership where clan_id = ? and left_at is null", Integer.class, live.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from clan_event e join clan_membership m on m.id = e.entity_id where e.entity_type = 'clan_membership' and e.event_type = 'MemberRemoved' and e.actor_id is null and m.clan_id = ? and (e.payload ->> 'clanId')::bigint = m.clan_id and (e.payload ->> 'userId')::bigint = m.user_id",Integer.class, gone.getId())).isEqualTo(2);
    }
}
