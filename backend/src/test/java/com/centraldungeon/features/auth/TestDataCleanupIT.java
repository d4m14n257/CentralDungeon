package com.centraldungeon.features.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * The e2e cleanup against the real foreign-key graph, so that a table nobody remembered stops being
 * a failure that only shows up one run later.
 *
 * <p>The cleanup runs in one transaction after every Playwright run, and when a single foreign key
 * is missing from its order the whole thing rolls back with a 500 that nobody reads, because the
 * tests are already over. The database then fills up, and the next run fails on pagination in some
 * unrelated screen. That happened more than ten times, each one fixed by adding the line that had
 * been forgotten. These two tests are the barrier instead:
 *
 * <ul>
 *   <li>The first reads the schema's foreign keys and fails as soon as a migration adds a table that
 *       hangs off {@code users} or {@code game_tables} without saying whether the cleanup covers it.</li>
 *   <li>The second fills every covered table with rows an e2e run would leave, plus rows that belong
 *       to nobody under test, runs the cleanup, and checks that the first kind are gone and the second
 *       kind are not.</li>
 * </ul>
 *
 * <p>Wired with {@code @DynamicPropertySource}, not {@code @ServiceConnection}: see
 * {@code RegistrationServiceIT} for why.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers
class TestDataCleanupIT {

    /** Tables that hang off users or game_tables and that {@link TestDataService#deleteE2eData} empties of e2e rows. */
    static final Set<String> CLEANED = Set.of(
            "users", "users_roles", "game_tables", "masters", "table_schedules", "table_sessions",
            "session_attendance", "table_status_changes", "table_registrations", "registration_rejections",
            "registration_status_changes", "registration_files", "table_tasks", "task_submissions", "task_files",
            "submission_files", "files", "file_categories", "table_files", "table_systems", "table_tags",
            "table_platforms", "approval_requests", "notifications", "system_settings", "system_setting_changes",
            "user_role_changes", "user_status_changes", "refresh_tokens");

    /** Tables that hang off them but that nothing writes yet, each with the reason it is safe to skip. */
    static final Map<String, String> EXEMPT = Map.of(
            "comment_drafts", "no entity and no writer until comments are built",
            "comments", "no entity and no writer until comments are built",
            "system_feedback", "no entity and no writer until feedback is built",
            "impersonation_sessions", "no entity and no writer until \"view as\" is built",
            "audit_logs", "no entity and no writer until auditing is built");

    @Container
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private TestDataService testDataService;

    @Autowired
    private JdbcTemplate jdbc;

    /** Every table reachable from users or game_tables is either cleaned or exempt, and the lists name only real tables. */
    @Test
    void everyTableHangingOffUsersOrTablesIsCleanedOrExempt() {
        Set<String> dependents = tablesHangingOff(Set.of("users", "game_tables"));

        Set<String> unaccounted = new TreeSet<>(dependents);
        unaccounted.removeAll(CLEANED);
        unaccounted.removeAll(EXEMPT.keySet());
        Set<String> stale = new TreeSet<>(CLEANED);
        stale.addAll(EXEMPT.keySet());
        stale.removeAll(dependents);
        Set<String> both = new TreeSet<>(CLEANED);
        both.retainAll(EXEMPT.keySet());

        assertThat(unaccounted)
                .as("tables that point at users or game_tables and that the e2e cleanup does not account for -"
                        + " add their delete to TestDataService and the table to CLEANED, or to EXEMPT with a reason")
                .isEmpty();
        assertThat(stale).as("listed tables that no longer hang off users or game_tables").isEmpty();
        assertThat(both).as("tables listed as both cleaned and exempt").isEmpty();
    }

    /**
     * The whole graph an e2e run can leave - including an e2e user acting on a real table - goes, and
     * what belongs to nobody under test stays.
     */
    @Test
    void cleanupEmptiesEveryCoveredTableOfE2eRowsAndKeepsTheRest() {
        populate();
        Map<String, Integer> before = countRows(CLEANED);

        testDataService.deleteE2eData();

        Map<String, Integer> after = countRows(CLEANED);
        Set<String> untouched = new TreeSet<>();
        for (String table : CLEANED) {
            if (after.get(table) >= before.get(table)) {
                untouched.add(table);
            }
        }
        assertThat(untouched).as("covered tables the cleanup removed nothing from").isEmpty();
        assertThat(count("SELECT COUNT(*) FROM users WHERE discord_id LIKE 'e2e-%'")).isZero();
        assertThat(count("SELECT COUNT(*) FROM game_tables WHERE name LIKE '%E2E%'")).isZero();
        assertThat(count("SELECT COUNT(*) FROM tags WHERE name LIKE 'E2E %'")).isZero();

        assertThat(count("SELECT COUNT(*) FROM users WHERE id IN ('real-master', 'real-player')")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM game_tables WHERE id = 'real-table' AND claimed_by IS NULL")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM table_registrations WHERE id = 'real-reg'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM table_sessions WHERE id = 'real-session'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM table_tasks WHERE id = 'real-task'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM files WHERE id = 'real-file'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM tags WHERE id = 'real-tag' AND canonical_id IS NULL")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM masters WHERE user_id = 'real-master'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM refresh_tokens WHERE id = 'real-rt'")).isEqualTo(1);
    }

    /** Closure over the foreign keys: every table that points, directly or through others, at one of the roots. */
    private Set<String> tablesHangingOff(Set<String> roots) {
        Map<String, Set<String>> referrers = new HashMap<>();
        jdbc.query(
                "SELECT TABLE_NAME, REFERENCED_TABLE_NAME FROM information_schema.KEY_COLUMN_USAGE"
                        + " WHERE TABLE_SCHEMA = DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL",
                row -> {
                    String table = row.getString(1).toLowerCase();
                    String referenced = row.getString(2).toLowerCase();
                    if (!table.equals(referenced)) {
                        referrers.computeIfAbsent(referenced, key -> new HashSet<>()).add(table);
                    }
                });
        Set<String> reached = new TreeSet<>(roots);
        List<String> pending = new ArrayList<>(roots);
        while (!pending.isEmpty()) {
            for (String referrer : referrers.getOrDefault(pending.removeLast(), Set.of())) {
                if (reached.add(referrer)) {
                    pending.add(referrer);
                }
            }
        }
        return reached;
    }

    private Map<String, Integer> countRows(Set<String> tables) {
        Map<String, Integer> counts = new TreeMap<>();
        for (String table : tables) {
            counts.put(table, count("SELECT COUNT(*) FROM " + table));
        }
        return counts;
    }

    private int count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    /**
     * An e2e table with everything that can hang off it, an e2e user acting on a real table, and the
     * real rows that must survive.
     */
    private void populate() {
        // People: two real, three e2e - a master, a player and an admin.
        user("real-master", "real-1");
        user("real-player", "real-2");
        user("e2e-master", "e2e-1");
        user("e2e-player", "e2e-2");
        user("e2e-admin", "e2e-3");
        sql("INSERT INTO users_roles (user_id, role_id, created_at) SELECT 'e2e-master', id, NOW() FROM roles WHERE name = 'Master'");
        sql("INSERT INTO users_roles (user_id, role_id, created_at) SELECT 'e2e-admin', id, NOW() FROM roles WHERE name = 'Admin'");
        sql("INSERT INTO users_roles (user_id, role_id, created_at) SELECT 'real-master', id, NOW() FROM roles WHERE name = 'Master'");

        // The real table, which an e2e admin has reserved in the queue.
        sql("INSERT INTO game_tables (id, name, status, created_by, claimed_by, claimed_at, created_at)"
                + " VALUES ('real-table', 'Real table', 'Preparation', 'real-master', 'e2e-admin', NOW(), NOW())");
        sql("INSERT INTO masters (game_table_id, user_id, master_type, created_at) VALUES ('real-table', 'real-master', 'Primary', NOW())");
        sql("INSERT INTO table_sessions (id, game_table_id, sequence_number, scheduled_at, created_at)"
                + " VALUES ('real-session', 'real-table', 1, NOW(), NOW())");
        sql("INSERT INTO table_tasks (id, game_table_id, audience, title, created_at)"
                + " VALUES ('real-task', 'real-table', 'Players', 'Real task', NOW())");
        sql("INSERT INTO table_registrations (id, game_table_id, user_id, status, created_at)"
                + " VALUES ('real-reg', 'real-table', 'real-player', 'Rejected', NOW())");
        file("real-file", "real-master");

        // The e2e table and everything that hangs off it.
        sql("INSERT INTO game_tables (id, name, status, created_by, created_at) VALUES ('e2e-table', 'Mesa E2E', 'Opened', 'e2e-master', NOW())");
        sql("INSERT INTO masters (game_table_id, user_id, master_type, created_at) VALUES ('e2e-table', 'e2e-master', 'Primary', NOW())");
        sql("INSERT INTO table_schedules (game_table_id, weekday, hourtime) VALUES ('e2e-table', 'Monday', '20:00:00')");
        sql("INSERT INTO table_status_changes (id, game_table_id, from_status, to_status, changed_by, created_at)"
                + " VALUES ('e2e-tsc', 'e2e-table', 'Draft', 'Opened', 'e2e-master', NOW())");
        sql("INSERT INTO table_sessions (id, game_table_id, sequence_number, scheduled_at, created_at)"
                + " VALUES ('e2e-session', 'e2e-table', 1, NOW(), NOW())");
        sql("INSERT INTO session_attendance (table_session_id, user_id, created_at) VALUES ('e2e-session', 'e2e-player', NOW())");
        sql("INSERT INTO table_registrations (id, game_table_id, user_id, status, created_at)"
                + " VALUES ('e2e-reg', 'e2e-table', 'e2e-player', 'Blocked', NOW())");
        sql("INSERT INTO registration_rejections (id, registration_id, description, rejected_at, rejected_by)"
                + " VALUES ('e2e-rej', 'e2e-reg', 'no', NOW(), 'e2e-master')");
        sql("INSERT INTO registration_status_changes (id, registration_id, from_status, to_status, changed_by, justification, created_at)"
                + " VALUES ('e2e-rsc', 'e2e-reg', 'Player', 'Blocked', 'e2e-master', 'veto', NOW())");
        // A task tied to a session: table_tasks points at table_sessions, so the session cannot go first.
        sql("INSERT INTO table_tasks (id, game_table_id, table_session_id, audience, title, created_at)"
                + " VALUES ('e2e-task', 'e2e-table', 'e2e-session', 'Players', 'E2E task', NOW())");
        sql("INSERT INTO task_submissions (id, task_id, user_id, created_at) VALUES ('e2e-sub', 'e2e-task', 'e2e-player', NOW())");
        file("e2e-file", "e2e-master");
        sql("INSERT INTO file_categories (file_id, category, created_at) VALUES ('e2e-file', 'TableMaterial', NOW())");
        sql("INSERT INTO table_files (game_table_id, file_id, created_at) VALUES ('e2e-table', 'e2e-file', NOW())");
        // A real person's file attached to the e2e table: the link goes, the file stays.
        sql("INSERT INTO table_files (game_table_id, file_id, created_at) VALUES ('e2e-table', 'real-file', NOW())");
        sql("INSERT INTO registration_files (registration_id, file_id, created_at) VALUES ('e2e-reg', 'e2e-file', NOW())");
        sql("INSERT INTO submission_files (submission_id, file_id, created_at) VALUES ('e2e-sub', 'e2e-file', NOW())");
        sql("INSERT INTO task_files (task_id, file_id, created_at) VALUES ('e2e-task', 'e2e-file', NOW())");
        sql("INSERT INTO table_systems (game_table_id, system_id, created_at) SELECT 'e2e-table', id, NOW() FROM systems LIMIT 1");
        sql("INSERT INTO table_platforms (game_table_id, platform_id, created_at) SELECT 'e2e-table', id, NOW() FROM platforms LIMIT 1");

        // An E2E tag with an alias of its own, a real tag that points at it, and a real table using it.
        sql("INSERT INTO tags (id, name, status, created_at) VALUES ('e2e-tag', 'E2E proposal', 'Accepted', NOW())");
        sql("INSERT INTO tags (id, name, canonical_id, status, created_at) VALUES ('e2e-tag-alias', 'E2E alias', 'e2e-tag', 'Accepted', NOW())");
        sql("INSERT INTO tags (id, name, canonical_id, status, created_at) VALUES ('real-tag', 'Real alias', 'e2e-tag', 'Accepted', NOW())");
        sql("INSERT INTO table_tags (game_table_id, tag_id, created_at) VALUES ('e2e-table', 'e2e-tag', NOW())");
        sql("INSERT INTO table_tags (game_table_id, tag_id, created_at) VALUES ('real-table', 'e2e-tag-alias', NOW())");

        // An e2e user acting on the real table: playing there, rejecting there, the target of a task there.
        sql("INSERT INTO table_registrations (id, game_table_id, user_id, status, created_at)"
                + " VALUES ('e2e-reg-on-real', 'real-table', 'e2e-player', 'Player', NOW())");
        sql("INSERT INTO registration_rejections (id, registration_id, description, rejected_at, rejected_by)"
                + " VALUES ('rej-by-e2e', 'real-reg', 'no', NOW(), 'e2e-master')");
        sql("INSERT INTO table_tasks (id, game_table_id, audience, target_user_id, title, created_at)"
                + " VALUES ('task-for-e2e', 'real-table', 'Single', 'e2e-player', 'Just you', NOW())");
        sql("INSERT INTO session_attendance (table_session_id, user_id, created_at) VALUES ('real-session', 'e2e-player', NOW())");
        sql("INSERT INTO table_status_changes (id, game_table_id, from_status, to_status, changed_by, created_at)"
                + " VALUES ('tsc-by-e2e', 'real-table', 'Draft', 'Preparation', 'e2e-admin', NOW())");

        // The platform side: requests, notifications, settings and the two audit trails.
        sql("INSERT INTO approval_requests (id, request_type, entity_type, entity_id, requested_by, justification, claimed_by, created_at)"
                + " VALUES ('e2e-ar', 'MasterGrant', 'user', 'e2e-player', 'e2e-player', 'please', 'e2e-admin', NOW())");
        sql("INSERT INTO notifications (id, user_id, notification_type, created_at) VALUES ('e2e-n', 'e2e-player', 'TableOpened', NOW())");
        sql("INSERT INTO system_settings (setting_key, value, value_type, category, updated_by, updated_at)"
                + " VALUES ('tables.max_players_cap', '10', 'Integer', 'Limits', 'e2e-admin', NOW())");
        sql("INSERT INTO system_setting_changes (id, setting_key, from_value, to_value, changed_by, justification, created_at)"
                + " VALUES ('e2e-ssc', 'tables.max_players_cap', NULL, '10', 'e2e-admin', 'why', NOW())");
        sql("INSERT INTO user_role_changes (id, user_id, role_id, action, changed_by, justification, created_at)"
                + " SELECT 'e2e-urc', 'real-player', id, 'Granted', 'e2e-admin', 'why', NOW() FROM roles WHERE name = 'Master'");
        sql("INSERT INTO user_status_changes (id, user_id, from_status, to_status, changed_by, justification, created_at)"
                + " VALUES ('e2e-usc', 'e2e-player', 'Allowed', 'Blocked', 'e2e-admin', 'why', NOW())");

        // Sessions: an e2e login goes, a real person's stays.
        sql("INSERT INTO refresh_tokens (id, jti_hash, family_id, user_id, expires_at, created_at)"
                + " VALUES ('e2e-rt', REPEAT('a', 64), 'e2e-family', 'e2e-player', NOW(), NOW())");
        sql("INSERT INTO refresh_tokens (id, jti_hash, family_id, user_id, expires_at, created_at)"
                + " VALUES ('real-rt', REPEAT('b', 64), 'real-family', 'real-player', NOW(), NOW())");
    }

    private void user(String id, String discordId) {
        jdbc.update("INSERT INTO users (id, discord_id, discord_username, created_at) VALUES (?, ?, ?, NOW())", id, discordId, id);
    }

    private void file(String id, String owner) {
        jdbc.update("INSERT INTO files (id, name, storage_key, mime_type, size_bytes, user_created_id, created_at)"
                + " VALUES (?, ?, ?, 'application/pdf', 1, ?, NOW())", id, id + ".pdf", id, owner);
    }

    private void sql(String statement) {
        jdbc.update(statement);
    }
}
