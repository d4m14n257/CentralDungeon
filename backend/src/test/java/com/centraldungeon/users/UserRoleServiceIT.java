package com.centraldungeon.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.common.security.CurrentUser;
import com.centraldungeon.users.dto.AdminUserSummaryResponse;
import com.centraldungeon.users.dto.UserAdminChangeResponse;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * The one global invariant of F3.1: <b>the platform is never left without an Owner</b>
 * (fase-3-admin-owner.md 3). MySQL cannot express it - there is no constraint for "at least one row
 * of this shape has to survive" - so it is checked in {@link UserRoleService} and therefore has to
 * be proven against the real engine, exactly like the single live Primary of #73 in
 * {@code MasterServiceIT}.
 *
 * <p>And the rest of what only a real database can answer for this slice: the two audit tables
 * really receive their rows through their foreign keys, the correlated EXISTS behind {@code /role}
 * counts the same people it lists, the history's {@code @EntityGraph} collapses the N+1, and the
 * cache eviction registered on {@code afterCommit} actually fires - the unit tests all run the
 * branch with no transaction, so that half of the code had never executed.
 *
 * <p>Wired with {@code @DynamicPropertySource}, not {@code @ServiceConnection}: see
 * {@code RegistrationServiceIT} for why.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class UserRoleServiceIT {

    @Container
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private UserRoleService userRoleService;

    @Autowired
    private AdminUserService adminUserService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private UserRoleChangeRepository userRoleChangeRepository;

    @Autowired
    private UserStatusChangeRepository userStatusChangeRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void setUp() {
        userRoleChangeRepository.deleteAll();
        userStatusChangeRepository.deleteAll();
        userRoleRepository.deleteAll();
        userRepository.deleteAll();
    }

    /**
     * <b>The invariant, under a real race.</b> Two owners revoke each other at the same moment. Each
     * one passes {@code isLastActiveOwner} while the other is still live, so each one believes there
     * is somebody left afterwards - and if nothing serialises the two, both revocations commit and
     * the platform ends with nobody who can grant Owner back. There is no way in from outside: the
     * rule exists precisely because a platform without an owner cannot repair itself.
     *
     * <p>It is the same shape as {@code MasterServiceIT}'s hand-off race and needs the same answer -
     * a lock held across the count and the write.
     */
    @Test
    void twoOwnersRevokingEachOtherAtOnceCannotLeaveThePlatformWithoutOne() throws InterruptedException {
        User first = owner("owner-one");
        User second = owner("owner-two");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();

        pool.submit(() -> revokeOwnerIgnoringConflict(second, first, start, succeeded));
        pool.submit(() -> revokeOwnerIgnoringConflict(first, second, start, succeeded));

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(userRoleRepository.countActiveHolders(PlatformRole.OWNER.roleName()))
                .as("the platform must keep at least one active owner, whatever the interleaving")
                .isGreaterThanOrEqualTo(1);
        assertThat(succeeded.get())
                .as("only one of the two revocations may be allowed through")
                .isEqualTo(1);
    }

    /**
     * The same invariant from the other side: several owners are demoted at once by granting them
     * Admin, which revokes Owner as the exclusion of #169 requires. Whatever order the threads land
     * in, one owner has to survive.
     */
    @Test
    void concurrentPromotionsToAdminCannotEmptyTheOwners() throws InterruptedException {
        List<User> owners = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            owners.add(owner("owner-" + i));
        }

        ExecutorService pool = Executors.newFixedThreadPool(owners.size());
        CountDownLatch start = new CountDownLatch(1);

        for (User target : owners) {
            User actor = owners.get((owners.indexOf(target) + 1) % owners.size());
            pool.submit(() -> {
                start.await();
                try {
                    userRoleService.grantRole(target.getId(), PlatformRole.ADMIN, "promoting", asOwner(actor));
                } catch (ConflictException expectedForTheLastOne) {
                    return false;
                }
                return true;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(userRoleRepository.countActiveHolders(PlatformRole.OWNER.roleName()))
                .as("granting Admin revokes Owner (#169); the last owner's grant must be refused whole")
                .isGreaterThanOrEqualTo(1);
    }

    /** The sequential case, against the real rows: the last owner is refused with its own code. */
    @Test
    void revokingTheLastOwnerIsRefusedWithLastOwner() {
        User theOnlyOwner = owner("solo-owner");
        User anotherOwner = owner("second-owner");

        // Two owners: taking one is fine.
        userRoleService.revokeRole(anotherOwner.getId(), PlatformRole.OWNER, "stepping down", asOwner(theOnlyOwner));
        assertThat(userRoleRepository.countActiveHolders(PlatformRole.OWNER.roleName())).isEqualTo(1);

        // One owner left: taking that one is not.
        assertThatThrownBy(() -> userRoleService.revokeRole(
                        theOnlyOwner.getId(), PlatformRole.OWNER, "taking the last one", asOwner(anotherOwner)))
                .isInstanceOf(ConflictException.class)
                .extracting(exception -> ((ConflictException) exception).getErrorCode())
                .isEqualTo(ConflictException.LAST_OWNER);

        assertThat(userRoleRepository.countActiveHolders(PlatformRole.OWNER.roleName())).isEqualTo(1);
    }

    /** An owner cannot step down alone, even with other owners around - the more specific 409. */
    @Test
    void anOwnerCannotRevokeTheirOwnOwnerRole() {
        User first = owner("self-owner");
        owner("spare-owner");

        assertThatThrownBy(() -> userRoleService.revokeRole(
                        first.getId(), PlatformRole.OWNER, "resigning", asOwner(first)))
                .isInstanceOf(ConflictException.class)
                .extracting(exception -> ((ConflictException) exception).getErrorCode())
                .isEqualTo(ConflictException.CANNOT_REVOKE_OWN_OWNER);
    }

    /**
     * The exclusion of #169 through the real tables: granting Admin to an owner flips <b>one</b> row
     * of {@code users_roles} per role - the primary key is the pair, so a second row cannot exist -
     * and leaves <b>two</b> rows of {@code user_role_changes}, one for each half of the change.
     */
    @Test
    void grantingAdminToAnOwnerRevokesOwnerAndLeavesBothAuditRows() {
        User granter = owner("granting-owner");
        User target = owner("demoted-owner");

        userRoleService.grantRole(target.getId(), PlatformRole.ADMIN, "moving them to admin", asOwner(granter));

        assertThat(userRoleRepository.findActiveRoleNames(target.getId())).containsExactly(PlatformRole.ADMIN.roleName());
        assertThat(userRoleRepository.findAllGrants(target.getId()))
                .as("one row per (user, role) pair, whatever the history")
                .hasSize(2);

        List<UserRoleChange> trail = userRoleChangeRepository.findByUser_IdOrderByCreatedAtAsc(target.getId());
        assertThat(trail)
                .as("the revocation gets a row of its own - burying it inside the grant loses the event")
                .hasSize(2);
        assertThat(trail).extracting(change -> change.getRole().getName() + ":" + change.getAction())
                .containsExactlyInAnyOrder(
                        PlatformRole.OWNER.roleName() + ":" + UserRoleChangeAction.Revoked,
                        PlatformRole.ADMIN.roleName() + ":" + UserRoleChangeAction.Granted);
        assertThat(trail).allSatisfy(change -> {
            assertThat(change.getJustification()).isEqualTo("moving them to admin");
            assertThat(change.getChangedBy().getId()).isEqualTo(granter.getId());
        });
    }

    /**
     * <b>The page's total and the page's content have to come from the same predicate.</b> Spring
     * Data builds a second, separate {@code CriteriaQuery} for the count, and
     * {@code UserSearchSpecification.holdsRole} answers {@code disjunction()} - matches nobody - when
     * it is handed a null query. If the count query ever went down that branch the screen would show
     * rows with a total of zero, silently and only for {@code /role}.
     */
    @Test
    void theRoleFilterCountsTheSamePeopleItLists() {
        for (int i = 0; i < 7; i++) {
            grant(person("master-" + i), PlatformRole.MASTER);
        }
        for (int i = 0; i < 3; i++) {
            person("plain-" + i);
        }

        PageResponse<AdminUserSummaryResponse> firstPage =
                adminUserService.search("/role Master", PageRequest.of(0, 4, Sort.by("discordUsername", "id")));

        assertThat(firstPage.content()).hasSize(4);
        assertThat(firstPage.totalElements()).isEqualTo(7);
        assertThat(firstPage.totalPages()).isEqualTo(2);

        PageResponse<AdminUserSummaryResponse> secondPage =
                adminUserService.search("/role Master", PageRequest.of(1, 4, Sort.by("discordUsername", "id")));
        assertThat(secondPage.content()).hasSize(3);
        assertThat(secondPage.totalElements()).isEqualTo(7);
    }

    /**
     * The history panel renders the actor's name and the role's name on every line. Without the
     * {@code @EntityGraph} on the two finders, one history is one query per entry - the N+1 that only
     * a real database can show.
     */
    @Test
    void theHistoryResolvesActorAndRoleWithoutAQueryPerRow() {
        User actor = owner("history-owner");
        User target = person("history-target");

        for (int i = 0; i < 5; i++) {
            userRoleService.grantRole(target.getId(), PlatformRole.MASTER, "up " + i, asOwner(actor));
            userRoleService.revokeRole(target.getId(), PlatformRole.MASTER, "down " + i, asOwner(actor));
        }
        adminUserService.block(target.getId(), "blocking", asOwner(actor));
        adminUserService.unblock(target.getId(), "unblocking", asOwner(actor));

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        List<UserAdminChangeResponse> history = adminUserService.history(target.getId());

        assertThat(history).hasSize(12);
        assertThat(history).extracting(UserAdminChangeResponse::changedByName).doesNotContainNull();
        assertThat(statistics.getPrepareStatementCount())
                .as("one lookup for the person and one query per audit table - not one per row")
                .isLessThanOrEqualTo(5);
        statistics.setStatisticsEnabled(false);
    }

    /**
     * <b>The least exercised line of the slice.</b> The eviction is registered on {@code afterCommit}
     * and every unit test runs the other branch - the one with no transaction open - so this is the
     * first time the synchronisation actually fires. A block whose eviction never ran leaves the
     * blocked person inside for the full 60 s of the cache's TTL (#128), which is the whole failure
     * the callback exists to prevent.
     */
    @Test
    void aBlockIsVisibleToTheVeryNextAuthenticationRatherThanAtTheEndOfTheTtl() {
        User actor = owner("blocking-owner");
        User target = person("to-be-blocked");
        grant(target, PlatformRole.PLAYER);

        // Prime the cache the way JwtAuthenticationFilter does on every request.
        assertThat(userService.loadAuthSnapshot(target.getId()).status()).isEqualTo(UserStatus.Allowed);

        adminUserService.block(target.getId(), "spam", asOwner(actor));

        assertThat(userService.loadAuthSnapshot(target.getId()).status())
                .as("the cached snapshot must have been dropped after the commit")
                .isEqualTo(UserStatus.Blocked);
    }

    /** The same callback on the role side: a revoked role stops authorising on the next request. */
    @Test
    void aRevokedRoleIsGoneFromTheAuthSnapshotImmediately() {
        User actor = owner("revoking-owner");
        User target = person("to-be-demoted");
        grant(target, PlatformRole.MASTER);

        assertThat(userService.loadAuthSnapshot(target.getId()).roles()).contains(PlatformRole.MASTER.roleName());

        userRoleService.revokeRole(target.getId(), PlatformRole.MASTER, "no longer runs tables", asOwner(actor));

        assertThat(userService.loadAuthSnapshot(target.getId()).roles())
                .doesNotContain(PlatformRole.MASTER.roleName());
    }

    /**
     * Both trails read as one timeline, and {@code justification} really is NOT NULL with its three
     * foreign keys in place - none of that SQL had ever run before this slice.
     */
    @Test
    void theHistoryMergesRoleChangesAndBlocksIntoOneTimeline() {
        User actor = owner("timeline-owner");
        User target = person("timeline-target");

        userRoleService.grantRole(target.getId(), PlatformRole.MASTER, "runs a table now", asOwner(actor));
        adminUserService.block(target.getId(), "behaviour", asOwner(actor));
        adminUserService.unblock(target.getId(), "apologised", asOwner(actor));

        List<UserAdminChangeResponse> history = adminUserService.history(target.getId());

        assertThat(history).extracting(UserAdminChangeResponse::type)
                .containsExactly("RoleGranted", "StatusChanged", "StatusChanged");
        assertThat(history).extracting(UserAdminChangeResponse::justification)
                .containsExactly("runs a table now", "behaviour", "apologised");
        assertThat(history).extracting(UserAdminChangeResponse::changedByName).containsOnly(actor.getDiscordUsername());
    }

    private boolean revokeOwnerIgnoringConflict(User target, User actor, CountDownLatch start, AtomicInteger succeeded)
            throws InterruptedException {
        start.await();
        try {
            userRoleService.revokeRole(target.getId(), PlatformRole.OWNER, "concurrent revoke", asOwner(actor));
            succeeded.incrementAndGet();
            return true;
        } catch (RuntimeException refused) {
            return false;
        }
    }

    private CurrentUser asOwner(User user) {
        return new CurrentUser(user.getId(), Set.of(PlatformRole.OWNER.roleName()));
    }

    private User owner(String discordUsername) {
        User user = person(discordUsername);
        grant(user, PlatformRole.OWNER);
        return user;
    }

    private User person(String discordUsername) {
        User user = new User(UUID.randomUUID().toString().replace("-", ""), discordUsername);
        user.setName(discordUsername);
        return userRepository.save(user);
    }

    private void grant(User user, PlatformRole role) {
        Role entity = roleRepository.findByName(role.roleName()).orElseThrow();
        userRoleRepository.save(new UserRole(user, entity));
    }
}
