//SPDX-License-Identifier: MIT
package com.faforever.clan_management;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.entity.ClanInvitation;
import com.faforever.clan_management.entity.ClanReport;
import com.faforever.clan_management.repository.AppUserRepository;
import com.faforever.clan_management.repository.ClanRepository;
import com.faforever.clan_management.service.AdminService;
import com.faforever.clan_management.service.ClanForm;
import com.faforever.clan_management.service.ClanManagementService;
import com.faforever.clan_management.service.InvitationService;
import com.faforever.clan_management.service.ReportService;

import static org.assertj.core.api.Assertions.assertThat;

//two requests on one clan at the same moment, each in its own transaction on its own thread, so unlike every other database test this one commits
//clan_event refuses deletes, so the rows cannot be taken back: it runs only when CLAN_RACE_TESTS names the database, which should be one made for the run and dropped after
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "CLAN_RACE_TESTS", matches = ".+")
class ClanLockIntegrationTest {

    private static final int rounds = 6;

    @Autowired
    private AppUserRepository users;
    @Autowired
    private ClanRepository clans;
    @Autowired
    private ClanManagementService management;
    @Autowired
    private AdminService admins;
    @Autowired
    private ReportService reports;
    @Autowired
    private InvitationService invitations;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource data;

    private AppUser someone(String role) {
        String stamp = Long.toString(System.nanoTime());
        return users.save(new AppUser("lock-" + role+"-" + stamp,"lock_"+role + "_"+stamp, "local", null));
    }

    private String freeTag() {
        for (String t : List.of("ZL9", "ZL8", "ZL7", "XL9", "XL8")) {
            if (clans.findByTagIgnoreCaseAndDeletedAtIsNull(t).isEmpty()) return t;
        }
        throw new IllegalStateException("every candidate tag is taken");
    }

    private Clan clanOf(AppUser owner) {
        ClanForm form = new ClanForm();
        form.setName("Lock Race " + System.nanoTime());
        form.setTag(freeTag());
        return management.create(owner,form);
    }

    //both calls released together, the outcome of each as ok or the name of what it threw; given a round, the second starts 5 ms late on even rounds and the first on odd ones so each order gets its turn at the lock, and given none they start at the same moment
    private List<String> together(Integer round, Callable<?> first, Callable<?> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier gate = new CyclicBarrier(2);
        List<Future<?>> running = new ArrayList<>();
        for (Callable<?> c : List.of(round != null && round % 2 == 1 ? late(first) : first, round != null && round % 2 == 0 ? late(second) : second)) {
            running.add(pool.submit(() -> {
                gate.await();
                return c.call();
            }));
        }
        List<String> out = new ArrayList<>();
        for (Future<?> f : running) {
            try {
                f.get();
                out.add("ok");
            } catch (java.util.concurrent.ExecutionException e) {
                out.add(e.getCause().getClass().getSimpleName());
            }
        }
        pool.shutdown();
        return out;
    }

    //refuses to write anywhere but the database CLAN_RACE_TESTS names, so pointing it at the shared one takes typing that name
    @BeforeEach
    void onlyTheNamedDatabase() {
        assertThat(jdbc.queryForObject("select current_database()",String.class)).as("CLAN_RACE_TESTS names the database this test may write to").isEqualTo(System.getenv("CLAN_RACE_TESTS"));
        assertThat(System.getenv("CLAN_RACE_TESTS")).as("the shared database is never a place for rows nobody can take back").isNotEqualTo("clan_management");
    }

    private Callable<?> late(Callable<?> call) {
        return () -> {
            Thread.sleep(5);
            return call.call();
        };
    }

    private int activeIn(Long clanId) {
        return jdbc.queryForObject("select count(*) from clan_membership where clan_id = ? and left_at is null", Integer.class, clanId);
    }

    @Test
    @DisplayName("an owner and an administrator deleting one clan at once write one ClanDeleted and leave nobody in it")
    void twoDeletesAtOnce() throws Exception {
        for (int r = 0; r < rounds; r++) {
            AppUser owner = someone("owner");
            AppUser admin = someone("admin");
            Clan clan = clanOf(owner);
            List<String> calls = together(r, () -> admins.delete(admin,clan.getId(), null, null), () -> {
                management.delete(owner);
                return null;
            });
            Integer deletedEvents = jdbc.queryForObject("select count(*) from clan_event where entity_type = 'clan' and entity_id = ? and event_type = 'ClanDeleted'",Integer.class, clan.getId());
            assertThat(deletedEvents).as("round %d, calls %s", r, calls).isEqualTo(1);
            assertThat(calls).as("round %d",r).containsOnlyOnce("ok");
            assertThat(activeIn(clan.getId())).isZero();
        }
    }

    @Test
    @DisplayName("an invite accepted while an administrator deletes the clan leaves no active member in the deleted clan")
    void acceptDuringDelete() throws Exception {
        for (int r = 0; r < rounds; r++) {
            AppUser owner = someone("owner");
            AppUser admin = someone("admin");
            AppUser invitee = someone("invitee");
            Clan clan = clanOf(owner);
            ClanInvitation inv = invitations.invite(owner, invitee.getDisplayName(), "MEMBER");
            List<String> calls = together(r, () -> admins.delete(admin, clan.getId(),null,null),() -> invitations.accept(invitee, inv.getId()));
            assertThat(activeIn(clan.getId())).as("round %d, calls %s", r, calls).isZero();
            String state = jdbc.queryForObject("select state from clan_invitation where id = ?", String.class, inv.getId());
            assertThat(state).as("round %d, calls %s", r, calls).isEqualTo(calls.get(1).equals("ok") ? "ACCEPTED" : "REVOKED"); //accepted then closed by the delete, or revoked by the delete and refused
        }
    }

    @Test
    @DisplayName("a report dismissed while an administrator deletes the clan stays dismissed when both calls succeed")
    void dismissalDuringDelete() throws Exception {
        for (int r = 0; r < rounds * 2; r++) { // the sweep only goes wrong when the two overlap, so these rounds start together and there are more of them
            AppUser admin = someone("admin");
            AppUser second = someone("second");
            Clan clan = clanOf(someone("owner"));
            ClanReport report = reports.file(someone("reporter"), clan.getId(), "OFFENSIVE_TAG", "dismissed during a delete");
            List<String> calls = together(null, () -> admins.delete(admin, clan.getId(),null,null),() -> reports.close(second, report.getId(), true));
            String state = jdbc.queryForObject("select state from clan_report where id = ?", String.class, report.getId());
            if (calls.equals(List.of("ok", "ok"))) assertThat(state).as("round %d", r).isEqualTo("DISMISSED"); // the dismissal went first, so the delete's sweep must have left it alone
            assertThat(state).isNotEqualTo("OPEN");
        }
    }

    @Test
    @DisplayName("a dismissal not yet committed when an administrator's delete reaches the open reports is waited for, then left as it is")
    void dismissalHeldWhileTheDeleteSweeps() throws Exception {
        AppUser admin = someone("admin");
        Clan clan = clanOf(someone("owner"));
        ClanReport report = reports.file(someone("reporter"), clan.getId(), "OFFENSIVE_TAG", "dismissed while the delete sweeps");
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (Connection held = data.getConnection()) {
            held.setAutoCommit(false);
            try (PreparedStatement ps = held.prepareStatement("update clan_report set state = 'DISMISSED', resolved_at = now() where id = ?")) {
                ps.setLong(1, report.getId());
                ps.executeUpdate(); //the row stays locked until the commit below
            }
            Future<?> delete = pool.submit(() -> admins.delete(admin, clan.getId(),null, null));
            Integer heldPid = new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(held, true)).queryForObject("select pg_backend_pid()", Integer.class);
            boolean waited = false;
            for (int tries = 0; tries < 100 && !waited; tries++) { //until the delete is seen waiting on this row, five seconds at most
                Thread.sleep(50);
                waited = jdbc.queryForObject("select count(*) from pg_stat_activity where ? = any(pg_blocking_pids(pid))", Integer.class, heldPid) > 0;
            }
            assertThat(waited).as("the delete waited on the held report").isTrue();
            held.commit();
            delete.get();
        } finally {
            pool.shutdown();
        }
        assertThat(jdbc.queryForObject("select state from clan_report where id = ?", String.class,report.getId())).isEqualTo("DISMISSED");
    }
}
