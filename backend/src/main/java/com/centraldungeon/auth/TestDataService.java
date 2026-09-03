package com.centraldungeon.auth;

import com.centraldungeon.auth.dto.TestCleanupResponse;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removes the rows an e2e run creates. Without this every run left its tables and users behind, and
 * a development database full of "Mesa E2E ..." is not only noise: it is what made
 * registration-flow.spec.ts fail once the explorer had more tables than a page (decisiones.md #171, #172).
 *
 * <p><b>The patterns are fixed here and nothing widens them.</b> The controller takes no input: game
 * tables whose name contains "E2E" or that an e2e user created, and users whose discordId starts
 * with "e2e-", which is the naming every spec follows. Rows that belong to an e2e user but hang off
 * a real table would go too - it cannot happen, because an e2e user only ever touches its own
 * tables, and keeping the foreign keys satisfied matters more than that hypothetical.
 *
 * <p>Deletes run in foreign key order. Each statement's own entity is never named in its subquery:
 * MySQL rejects deleting from a table that the same statement selects from (error 1093).
 *
 * <p>Only exists under the "test" profile, like TestLoginController.
 */
@Service
@Profile("test")
public class TestDataService {

    private static final String TABLE_NAME_PATTERN = "%E2E%";
    private static final String DISCORD_ID_PREFIX = "e2e-%";

    private static final String E2E_USERS = "select u.id from User u where u.discordId like :discordId";
    private static final String E2E_TABLES =
            "select t.id from GameTable t where t.name like :tableName or t.createdBy.id in (" + E2E_USERS + ")";
    private static final String E2E_REGISTRATIONS = "select reg.id from TableRegistration reg where reg.gameTable.id in ("
            + E2E_TABLES + ") or reg.user.id in (" + E2E_USERS + ")";

    /** Bulk JPQL deletes, which is the one job that does not fit a repository. */
    private final EntityManager entityManager;

    /**
     * @param entityManager used for the bulk deletes this cleanup is made of
     */
    public TestDataService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Removes every row the e2e suite created, children first so no foreign key is ever left dangling.
     *
     * <p>The order below is the dependency order and is not interchangeable. It only ever matches rows
     * the suite itself made - test users by their Discord id prefix, test tables by name - so running
     * it against a database with real data touches nothing.
     *
     * @return how many rows were deleted per table
     */
    @Transactional
    public TestCleanupResponse deleteE2eData() {
        delete("delete from RegistrationRejection r where r.registration.id in (" + E2E_REGISTRATIONS + ")");
        delete("delete from TableRegistration reg2 where reg2.gameTable.id in (" + E2E_TABLES + ") or reg2.user.id in (" + E2E_USERS + ")");
        delete("delete from Notification n where n.user.id in (" + E2E_USERS + ")");
        delete("delete from TableStatusChange c where c.gameTable.id in (" + E2E_TABLES + ") or c.changedBy.id in (" + E2E_USERS + ")");
        delete("delete from Master m where m.gameTable.id in (" + E2E_TABLES + ") or m.user.id in (" + E2E_USERS + ")");
        int gameTables =
                delete("delete from GameTable gt where gt.name like :tableName or gt.createdBy.id in (" + E2E_USERS + ")");
        delete("delete from UserRole ur where ur.user.id in (" + E2E_USERS + ")");
        int users = delete("delete from User u2 where u2.discordId like :discordId");

        return new TestCleanupResponse(gameTables, users);
    }

    /** Only binds what the statement actually names: JPA rejects a parameter the query does not declare. */
    private int delete(String jpql) {
        var query = entityManager.createQuery(jpql);
        if (jpql.contains(":tableName")) {
            query.setParameter("tableName", TABLE_NAME_PATTERN);
        }
        if (jpql.contains(":discordId")) {
            query.setParameter("discordId", DISCORD_ID_PREFIX);
        }
        return query.executeUpdate();
    }
}
