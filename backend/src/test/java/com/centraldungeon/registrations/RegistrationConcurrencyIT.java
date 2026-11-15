package com.centraldungeon.registrations;

import static org.assertj.core.api.Assertions.assertThat;

import com.centraldungeon.approvals.ApprovalService;
import com.centraldungeon.common.LockGate;
import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.security.CurrentUser;
import com.centraldungeon.registrations.dto.CreateRegistrationRequest;
import com.centraldungeon.registrations.dto.RejectRegistrationRequest;
import com.centraldungeon.tables.GameTable;
import com.centraldungeon.tables.GameTableRepository;
import com.centraldungeon.tables.GameTableStatus;
import com.centraldungeon.tables.Master;
import com.centraldungeon.tables.MasterRepository;
import com.centraldungeon.tables.MasterType;
import com.centraldungeon.users.PlatformRole;
import com.centraldungeon.users.Role;
import com.centraldungeon.users.RoleRepository;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserRepository;
import com.centraldungeon.users.UserRole;
import com.centraldungeon.users.UserRoleRepository;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * The application rules MySQL cannot hold on its own - the capacity of a table, the automatic
 * rejection that fills it, and the veto - raced for real against MySQL.
 *
 * <p>Every one of them is a check-then-act serialized by the table's row lock, and every one of them
 * breaks in the same way if anything is read <em>before</em> that lock: under {@code REPEATABLE READ}
 * the first plain read opens the transaction's snapshot, so a transaction that queued behind the lock
 * wakes up and decides from rows as they were before the winner committed. A two-thread race only
 * shows that by luck. Here a {@link LockGate} holds the table's lock while both contenders do their
 * early reads and queue, and only then lets them through, so the stale interleaving happens every
 * time the code allows it.
 *
 * <p>The assertions are about what is left in the database - statuses, the veto trail, rejection rows
 * - and not only about who got a 409, because a race that answers correctly while writing the wrong
 * rows is the more dangerous of the two.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class RegistrationConcurrencyIT {

    private static final Duration QUEUE_TIMEOUT = Duration.ofSeconds(20);
    private static final String OK = "ok";

    @Container
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private GameTableRepository gameTableRepository;

    @Autowired
    private MasterRepository masterRepository;

    @Autowired
    private TableRegistrationRepository registrationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    private JdbcTemplate monitor;
    private ExecutorService pool;
    private User primary;
    private User secondary;

    @BeforeEach
    void setUp() {
        pool = Executors.newFixedThreadPool(2);
        monitor = LockGate.monitor(mysql);
        primary = person("primary", PlatformRole.MASTER);
        secondary = person("secondary", PlatformRole.MASTER);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        pool.shutdownNow();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
    }

    // ---------------------------------------------------------------- capacity

    /**
     * <b>The last seat, accepted twice at once.</b> The first accept fills the table and rejects the
     * rest, the second candidate among them. The second accept must then find its candidate rejected
     * - not the stale {@code Candidate} it read before queueing - and above all must not run its own
     * automatic rejection over a snapshot in which the first candidate was still waiting, which turns
     * somebody who was just accepted into a rejected applicant.
     */
    @Test
    void twoAcceptsForTheLastSeatLeaveExactlyOnePlayerAndOneRejection() throws Exception {
        GameTable table = openTable(1);
        TableRegistration first = candidate(table, "first");
        TableRegistration second = candidate(table, "second");

        List<String> outcomes = raceBehindTheTableLock(table,
                () -> registrationService.accept(first.getId(), primary.getId()),
                () -> registrationService.accept(second.getId(), primary.getId()));

        assertThat(outcomes).as("one accept wins, the other is refused").containsOnlyOnce(OK).hasSize(2);
        assertThat(outcomes).as("the refusal is a 409, never a deadlock").allMatch(RegistrationConcurrencyIT::isOkOrConflict);
        assertThat(countWithStatus(table, "Player")).as("one seat, one player").isEqualTo(1);
        assertThat(countWithStatus(table, "Rejected")).isEqualTo(1);
        assertThat(rejectionRows(table)).as("one rejection row per rejected candidate, no more").isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select count(*) from registration_rejections rr
                join table_registrations r on r.id = rr.registration_id
                where r.game_table_id = ? and r.status = 'Player'""", Integer.class, table.getId()))
                .as("nobody who is a player carries a rejection")
                .isZero();
    }

    /**
     * <b>A full table takes no one else</b>, at either door. Applying is subject to the seats left,
     * and so is accepting - otherwise a candidate who applied after the table filled is one click away
     * from a table with more players than it has room for.
     */
    @Test
    void aFullTableRefusesBothANewApplicationAndAnAcceptance() {
        GameTable table = openTable(1);
        TableRegistration seated = candidate(table, "seated");
        registrationService.accept(seated.getId(), primary.getId());

        User latecomer = person("latecomer", PlatformRole.PLAYER);
        assertThat(outcomeOf(() -> registrationService.apply(
                        table.getId(), latecomer.getId(), new CreateRegistrationRequest(null, List.of()))))
                .isEqualTo(ConflictException.TABLE_FULL);

        // A candidate already waiting when the table filled - the automatic rejection covers the ones
        // it finds, and this row stands for one that got in some other way.
        TableRegistration waiting = candidate(table, "waiting");
        assertThat(outcomeOf(() -> registrationService.accept(waiting.getId(), primary.getId())))
                .isEqualTo(ConflictException.TABLE_FULL);
        assertThat(countWithStatus(table, "Player")).isEqualTo(1);
    }

    // ---------------------------------------------------------------- the veto

    /** <b>The same veto, twice at once.</b> One veto and one 409, and one row in the trail. */
    @Test
    void aDoubleVetoLeavesOneVetoAndOneRowInTheTrail() throws Exception {
        GameTable table = openTable(4);
        TableRegistration target = player(table, "target");

        List<String> outcomes = raceBehindTheTableLock(table,
                () -> registrationService.block(table.getId(), target.getId(), primary.getId(), "first veto"),
                () -> registrationService.block(table.getId(), target.getId(), primary.getId(), "second veto"));

        assertThat(outcomes).containsExactlyInAnyOrder(OK, ConflictException.REGISTRATION_ALREADY_BLOCKED);
        assertThat(vetoRows(target)).isEqualTo(1);
    }

    /**
     * <b>A veto from the Primary while the same Primary approves a co-master's request for it.</b>
     * Both end in the one place that writes {@code Blocked}, and both have to agree on who did it:
     * one veto, one refusal, the request answered once - and no deadlock between the request's lock
     * and the table's, which is what taking them in two different orders would produce.
     */
    @Test
    void aDirectVetoRacingTheApprovalOfAVetoRequestVetoesOnce() throws Exception {
        GameTable table = openTable(4);
        masterRepository.save(new Master(table, secondary, MasterType.Secondary));
        TableRegistration target = player(table, "target");
        String requestId = approvalService.submitPlayerBan(target.getId(), "asked by the co-master", asMaster(secondary)).id();

        List<String> outcomes = raceBehindTheTableLock(table,
                () -> approvalService.blockDirectly(table.getId(), target.getId(), "direct veto", asMaster(primary)),
                () -> approvalService.approveBanRequest(table.getId(), requestId, "request approved", asMaster(primary)));

        assertThat(outcomes).as("one wins; the other is a 409, never a deadlock").containsOnlyOnce(OK).hasSize(2);
        assertThat(outcomes).allMatch(outcome -> outcome.equals(OK)
                || outcome.equals(ConflictException.REGISTRATION_ALREADY_BLOCKED)
                || outcome.equals(ConflictException.REQUEST_ALREADY_RESOLVED));
        assertThat(vetoRows(target)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from approval_requests where id = ?", String.class, requestId))
                .isEqualTo("Approved");
    }

    /**
     * <b>The same race, with the request answered from the admin screen</b> - which a Primary who is
     * also an admin can do. That path used to take the request's lock first and the table's only when
     * applying the veto, the opposite order of a direct veto, which closes the same request: the two
     * waited on each other. The direct veto goes first in the queue so that, in the old order, it gets
     * the table while the approval already holds the request.
     */
    @Test
    void aDirectVetoRacingTheGenericApprovalOfTheSameRequestNeitherDeadlocksNorVetoesTwice() throws Exception {
        GameTable table = openTable(4);
        masterRepository.save(new Master(table, secondary, MasterType.Secondary));
        TableRegistration target = player(table, "target");
        String requestId = approvalService.submitPlayerBan(target.getId(), "asked by the co-master", asMaster(secondary)).id();

        List<String> outcomes = raceInOrder(table,
                () -> approvalService.blockDirectly(table.getId(), target.getId(), "direct veto", asMaster(primary)),
                () -> approvalService.approve(requestId, "approved from the admin screen", asMaster(primary)));

        assertThat(outcomes).as("one wins; the other is a 409, never a deadlock").containsOnlyOnce(OK).hasSize(2);
        assertThat(outcomes).allMatch(RegistrationConcurrencyIT::isOkOrConflict);
        assertThat(vetoRows(target)).isEqualTo(1);
        assertThat(requestStatus(requestId)).isEqualTo("Approved");
    }

    /**
     * <b>A direct veto closing a request somebody else is refusing at that moment.</b> The refusal is
     * written and not yet committed when the veto reads the open requests about that person. Read from
     * the veto's snapshot the request still looks open, and the veto would then wait for the refusal
     * and overwrite it - a request the co-master was told was refused, now approved, and told again.
     * The gate here is the refusal itself, holding the request's row with its new status.
     */
    @Test
    void aDirectVetoDoesNotOverwriteARefusalOfTheSameRequestCommittedWhileItWaited() throws Exception {
        GameTable table = openTable(4);
        masterRepository.save(new Master(table, secondary, MasterType.Secondary));
        TableRegistration target = player(table, "target");
        String requestId = approvalService.submitPlayerBan(target.getId(), "asked by the co-master", asMaster(secondary)).id();

        String outcome;
        try (LockGate refusal = LockGate.hold(transactionManager,
                () -> jdbc.update("update approval_requests set status = 'Rejected' where id = ?", requestId))) {
            Future<String> veto = pool.submit(() -> outcomeOf(() ->
                    approvalService.blockDirectly(table.getId(), target.getId(), "direct veto", asMaster(primary))));
            refusal.awaitWaiters(monitor, 1, QUEUE_TIMEOUT);
            refusal.open();
            outcome = veto.get(30, TimeUnit.SECONDS);
        }

        assertThat(outcome).as("the veto itself goes through").isEqualTo(OK);
        assertThat(requestStatus(requestId)).as("the refusal committed first and stands").isEqualTo("Rejected");
        assertThat(jdbc.queryForObject("""
                select count(*) from notifications
                where user_id = ? and notification_type = 'ApprovalRequestApproved'""",
                Integer.class, secondary.getId()))
                .as("the co-master is not told their refused request was approved")
                .isZero();
    }

    /**
     * <b>Lifting the veto of a player whose seat was filled meanwhile.</b> The veto is lifted, and the
     * person comes back as a candidate: back to {@code Player} would leave the table over its cap.
     */
    @Test
    void liftingTheVetoOfAPlayerWhoseSeatWasFilledBringsThemBackAsACandidate() {
        GameTable table = openTable(1);
        TableRegistration vetoed = player(table, "vetoed");
        registrationService.block(table.getId(), vetoed.getId(), primary.getId(), "vetoed while playing");
        TableRegistration replacement = candidate(table, "replacement");
        registrationService.accept(replacement.getId(), primary.getId());

        registrationService.unblock(table.getId(), vetoed.getId(), primary.getId(), "the veto was a mistake");

        assertThat(statusOf(vetoed)).isEqualTo("Candidate");
        assertThat(countWithStatus(table, "Player")).as("the cap holds").isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select to_status from registration_status_changes
                where registration_id = ? and from_status = 'Blocked'""", String.class, vetoed.getId()))
                .as("the trail records where they actually landed")
                .isEqualTo("Candidate");
    }

    // ---------------------------------------------------------------- two writes over one candidate

    /**
     * <b>Accepting and rejecting the same candidate at once.</b> Whichever comes second must see what
     * the first did. The broken outcome is a player with a rejection row - accepted over a rejection
     * the candidate was already told about.
     */
    @Test
    void acceptAndRejectOfTheSameCandidateDoNotBothApply() throws Exception {
        GameTable table = openTable(4);
        TableRegistration target = candidate(table, "target");

        List<String> outcomes = raceInOrder(table,
                () -> registrationService.accept(target.getId(), primary.getId()),
                () -> registrationService.reject(target.getId(), primary.getId(), new RejectRegistrationRequest("not a fit")));

        assertThat(outcomes).containsOnlyOnce(OK).hasSize(2);
        assertThat(outcomes).as("the refusal is a 409, never a deadlock").allMatch(RegistrationConcurrencyIT::isOkOrConflict);
        String status = statusOf(target);
        int rejections = rejectionRows(table);
        assertThat(status.equals("Player") ? rejections == 0 : status.equals("Rejected") && rejections == 1)
                .as("status %s with %d rejection rows", status, rejections)
                .isTrue();
    }

    /**
     * <b>A candidate withdrawing while the acceptance that fills the table rejects the rest.</b>
     * Either they withdrew first and the rejection skips them, or they were rejected first and the
     * withdrawal is refused. Never a withdrawn application carrying a rejection, which is a person
     * told «the table is full» about an application they had already taken back.
     */
    @Test
    void aWithdrawalRacingTheAutomaticRejectionEndsInOneOfTheTwoNotBoth() throws Exception {
        GameTable table = openTable(1);
        TableRegistration accepted = candidate(table, "accepted");
        TableRegistration leaving = candidate(table, "leaving");

        List<String> outcomes = raceInOrder(table,
                () -> registrationService.accept(accepted.getId(), primary.getId()),
                () -> {
                    registrationService.withdraw(leaving.getId(), leaving.getUser().getId());
                    return null;
                });

        assertThat(outcomes.getFirst()).as("the acceptance itself always goes through").isEqualTo(OK);
        assertThat(outcomes.get(1)).as("the withdrawal goes through or is a 409, never a deadlock")
                .matches(RegistrationConcurrencyIT::isOkOrConflict);
        String status = statusOf(leaving);
        int rejections = rejectionRows(table);
        if (outcomes.get(1).equals(OK)) {
            assertThat(status).isEqualTo("Deleted");
            assertThat(rejections).as("a withdrawn application is not rejected afterwards").isZero();
        } else {
            assertThat(status).isEqualTo("Rejected");
            assertThat(rejections).isEqualTo(1);
        }
    }

    // ---------------------------------------------------------------- the race harness

    /**
     * Holds the table's lock, starts both calls, waits until both are queued behind it - each having
     * done whatever it reads before its own lock - and lets them through.
     */
    private List<String> raceBehindTheTableLock(GameTable table, Callable<?> first, Callable<?> second)
            throws Exception {
        try (LockGate gate = holdTableLock(table)) {
            Future<String> a = pool.submit(() -> outcomeOf(first));
            Future<String> b = pool.submit(() -> outcomeOf(second));
            gate.awaitWaiters(monitor, 2, QUEUE_TIMEOUT);
            gate.open();
            return List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
        }
    }

    /**
     * Like {@link #raceBehindTheTableLock}, but in a fixed order and for a second call that may not
     * wait on the table's lock at all: the first is queued before the second starts, and the gate opens
     * once the second is queued too or has already finished - which, without a lock, it does at once.
     */
    private List<String> raceInOrder(GameTable table, Callable<?> first, Callable<?> second) throws Exception {
        try (LockGate gate = holdTableLock(table)) {
            Future<String> a = pool.submit(() -> outcomeOf(first));
            gate.awaitWaiters(monitor, 1, QUEUE_TIMEOUT);
            Future<String> b = pool.submit(() -> outcomeOf(second));
            gate.awaitUntil(monitor, waiting -> waiting >= 2 || b.isDone(), QUEUE_TIMEOUT);
            gate.open();
            return List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
        }
    }

    private LockGate holdTableLock(GameTable table) {
        return LockGate.hold(transactionManager, () -> gameTableRepository.findByIdForUpdate(table.getId()).orElseThrow());
    }

    /** A call that returned, or one refused with a business conflict - not a lock failure or a 500. */
    private static boolean isOkOrConflict(String outcome) {
        // A conflict's code, or CONFLICT for one without a code; an exception renders as Class: message.
        return outcome.equals(OK) || outcome.matches("[A-Z_]+");
    }

    /** {@value #OK} when the call returned, the conflict's code when it was refused, the class otherwise. */
    private static String outcomeOf(Callable<?> call) {
        try {
            call.call();
            return OK;
        } catch (ConflictException conflict) {
            return conflict.getErrorCode() != null ? conflict.getErrorCode() : "CONFLICT";
        } catch (Exception other) {
            return other.getClass().getSimpleName() + ": " + other.getMessage();
        }
    }

    // ---------------------------------------------------------------- fixtures and reads

    private GameTable openTable(int maxPlayers) {
        GameTable table = new GameTable("Concurrency " + UUID.randomUUID(), primary);
        table.setStatus(GameTableStatus.Opened);
        table.setMaxPlayers(maxPlayers);
        GameTable saved = gameTableRepository.save(table);
        masterRepository.save(new Master(saved, primary, MasterType.Primary));
        return saved;
    }

    private TableRegistration candidate(GameTable table, String name) {
        return registrationRepository.save(new TableRegistration(table, person(name, PlatformRole.PLAYER), null));
    }

    private TableRegistration player(GameTable table, String name) {
        TableRegistration registration = new TableRegistration(table, person(name, PlatformRole.PLAYER), null);
        registration.setStatus(TableRegistrationStatus.Player);
        return registrationRepository.save(registration);
    }

    private User person(String name, PlatformRole role) {
        User user = new User(UUID.randomUUID().toString().replace("-", ""), name);
        user.setName(name);
        User saved = userRepository.save(user);
        Role entity = roleRepository.findByName(role.roleName()).orElseThrow();
        userRoleRepository.save(new UserRole(saved, entity));
        return saved;
    }

    private static CurrentUser asMaster(User user) {
        return new CurrentUser(user.getId(), Set.of(PlatformRole.MASTER.roleName()));
    }

    private int countWithStatus(GameTable table, String status) {
        return jdbc.queryForObject(
                "select count(*) from table_registrations where game_table_id = ? and status = ?",
                Integer.class, table.getId(), status);
    }

    private int rejectionRows(GameTable table) {
        return jdbc.queryForObject("""
                select count(*) from registration_rejections rr
                join table_registrations r on r.id = rr.registration_id
                where r.game_table_id = ?""", Integer.class, table.getId());
    }

    private int vetoRows(TableRegistration registration) {
        return jdbc.queryForObject(
                "select count(*) from registration_status_changes where registration_id = ? and to_status = 'Blocked'",
                Integer.class, registration.getId());
    }

    private String requestStatus(String requestId) {
        return jdbc.queryForObject("select status from approval_requests where id = ?", String.class, requestId);
    }

    private String statusOf(TableRegistration registration) {
        return jdbc.queryForObject(
                "select status from table_registrations where id = ?", String.class, registration.getId());
    }
}
