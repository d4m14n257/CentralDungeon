package com.centraldungeon.approvals;

import static org.assertj.core.api.Assertions.assertThat;

import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.security.CurrentUser;
import com.centraldungeon.notifications.NotificationRepository;
import com.centraldungeon.notifications.NotificationType;
import com.centraldungeon.users.PlatformRole;
import com.centraldungeon.users.Role;
import com.centraldungeon.users.RoleRepository;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserRepository;
import com.centraldungeon.users.UserRole;
import com.centraldungeon.users.UserRoleChangeRepository;
import com.centraldungeon.users.UserRoleRepository;
import com.centraldungeon.users.UserStatusChangeRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * What only a real database can answer about F3.2: the two check-then-act rules under a real race,
 * and the orphan sweep of #78 over real rows.
 *
 * <p>{@code REQUEST_ALREADY_RESOLVED} and {@code REQUEST_ALREADY_PENDING} both read a row, decide,
 * and then write - with nothing serialising the two halves. Every unit test of them runs one thread,
 * so the rule reads correct and the interleaving is invisible; F3.1 learned what that costs when the
 * last-owner invariant turned out to break for real against MySQL (#252). These are the same shape,
 * so they get the same treatment: two threads, one latch, and an assertion about the row afterwards.
 *
 * <p>And {@link ApprovalOrphanCheckService}, which is a third of the price of the polymorphic
 * reference (#78) and had only ever run against mocks. A job nobody ever ran against a database is a
 * job nobody knows runs.
 *
 * <p>Wired with {@code @DynamicPropertySource}, not {@code @ServiceConnection}: see
 * {@code RegistrationServiceIT} for why.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class ApprovalServiceIT {

    @Container
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ApprovalOrphanCheckService orphanCheckService;

    @Autowired
    private ApprovalRequestRepository approvalRequestRepository;

    @Autowired
    private NotificationRepository notificationRepository;

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
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        approvalRequestRepository.deleteAll();
        notificationRepository.deleteAll();
        userRoleChangeRepository.deleteAll();
        userStatusChangeRepository.deleteAll();
        userRoleRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ---------------------------------------------------------------- the race of two admins

    /**
     * <b>Two admins answering the same request at the same moment, and the answers differ.</b>
     *
     * <p>{@code REQUEST_ALREADY_RESOLVED} is a check-then-act with no lock: each transaction reads
     * the row as {@code Pending} from its own snapshot, each one passes the guard, and the write that
     * follows is a plain UPDATE with no version column and no {@code where status = 'Pending'} to
     * refuse it. The second UPDATE waits for the first one's row lock and then overwrites it. The
     * contract says one answer and one 409; without a lock the mechanism delivers two answers.
     *
     * <p><b>This is where it stops being benign.</b> Approving grants the role and rejecting grants
     * nothing, so both getting through leaves a row that can say {@code Rejected} over a person who
     * is now a Master. That is not a lost resolution note - it is the record of the decision
     * contradicting the decision, and the requester gets told both things.
     *
     * <p>The assertion is the contract's promise and not the implementation's behaviour on purpose:
     * a test asserting what the code currently does would pass for ever and say nothing.
     */
    @Test
    void oneAdminApprovingWhileAnotherRejectsMustNotBothGetThrough() throws InterruptedException {
        User asker = person("the-asker");
        User firstAdmin = admin("admin-one");
        User secondAdmin = admin("admin-two");
        String requestId = submitAs(asker, ApprovalRequestType.MasterGrant, "quiero dirigir");

        Outcome outcome = raceOn(
                () -> approvalService.approve(requestId, "sí", asAdmin(firstAdmin)),
                () -> approvalService.reject(requestId, "no", asAdmin(secondAdmin)));

        ApprovalRequest resolved = approvalRequestRepository.findById(requestId).orElseThrow();
        boolean holdsMaster =
                userRoleRepository.findActiveRoleNames(asker.getId()).contains(PlatformRole.MASTER.roleName());

        assertThat(outcome.succeeded())
                .as("one answer, not two - the loser is REQUEST_ALREADY_RESOLVED. Refusals seen: "
                        + outcome.refusals())
                .isEqualTo(1);
        assertThat(notificationRepository.findAll())
                .as("one resolution is one piece of news: the requester must not be told both things")
                .hasSize(1);
        assertThat(resolved.getStatus() == ApprovalStatus.Approved)
                .as("the stored outcome must be the one that actually happened - it says "
                        + resolved.getStatus() + " while the Master role is "
                        + (holdsMaster ? "held" : "not held"))
                .isEqualTo(holdsMaster);
    }

    /**
     * The same race on a request whose approval has no side effect at all, which is the pure form of
     * it: nothing else can interfere, so what comes out is the guard on its own.
     *
     * <p>A {@code General} request is the right shape for that, and it is also the common one - two
     * of the three types of this slice do nothing when approved.
     */
    @Test
    void twoAdminsApprovingTheSameRequestAtOnceProduceOneAnswerAndOneConflict() throws InterruptedException {
        User asker = person("the-asker");
        User firstAdmin = admin("admin-one");
        User secondAdmin = admin("admin-two");
        String requestId = submitAs(asker, ApprovalRequestType.General, "una consulta");

        Outcome outcome = raceOn(
                () -> approvalService.approve(requestId, "sí, de la primera", asAdmin(firstAdmin)),
                () -> approvalService.approve(requestId, "sí, de la segunda", asAdmin(secondAdmin)));

        assertThat(outcome.succeeded())
                .as("only one of the two resolutions may be allowed through - the other is "
                        + "REQUEST_ALREADY_RESOLVED. Refusals seen: " + outcome.refusals())
                .isEqualTo(1);
        assertThat(notificationRepository.findAll())
                .as("one resolution is one piece of news: the requester must not be told twice")
                .hasSize(1);
    }

    /**
     * And the same race on a {@code MasterGrant}, where something <em>does</em> stop it - but not the
     * guard, and not with the code the contract declares.
     *
     * <p>Both transactions reach {@code UserRoleService.grantRole}, both find no live grant, and both
     * insert the same {@code (user_id, role_id)} pair. The primary key refuses the second one, so the
     * second resolution rolls back whole - which is why this race looks harmless from the outside. It
     * is not the rule doing the refusing, though: the refusal that comes out is a data-integrity
     * failure, which every other layer turns into a 500, where the contract says 409
     * {@code REQUEST_ALREADY_RESOLVED}. The admin who lost is told the server broke.
     */
    @Test
    void theLoserOfAMasterGrantRaceIsToldItWasAlreadyResolvedAndNotThatTheServerBroke()
            throws InterruptedException {
        User asker = person("the-asker");
        User firstAdmin = admin("admin-one");
        User secondAdmin = admin("admin-two");
        String requestId = submitAs(asker, ApprovalRequestType.MasterGrant, "quiero dirigir");

        Outcome outcome = raceOn(
                () -> approvalService.approve(requestId, "sí, de la primera", asAdmin(firstAdmin)),
                () -> approvalService.approve(requestId, "sí, de la segunda", asAdmin(secondAdmin)));

        assertThat(outcome.succeeded()).isEqualTo(1);
        assertThat(outcome.refusals())
                .singleElement()
                .as("the loser has to get the conflict the contract declares, not whatever the "
                        + "database happened to throw")
                .asString()
                .startsWith("ConflictException");
    }

    /**
     * The other check-then-act: {@code REQUEST_ALREADY_PENDING} reads
     * {@code existsByRequestTypeAndRequestedBy_IdAndStatus} and then inserts, with no unique index
     * behind it - the table has none, because "one Pending per type and per person" is not a shape
     * MySQL can express with a plain unique key.
     *
     * <p>A double-clicked button is the everyday way to hit it, and the failure it produces is the
     * exact one the rule exists to prevent: two rows in the queue that an admin has to answer one at
     * a time.
     */
    @Test
    void oneButtonPressedTwiceAtOnceOpensOneRequest() throws InterruptedException {
        User asker = person("the-eager-asker");

        Outcome outcome = raceOn(
                () -> approvalService.submit(ApprovalRequestType.MasterGrant, "primer clic", asPlayer(asker)),
                () -> approvalService.submit(ApprovalRequestType.MasterGrant, "segundo clic", asPlayer(asker)));

        assertThat(outcome.succeeded())
                .as("one press, one request. Refusals seen: " + outcome.refusals())
                .isEqualTo(1);
        assertThat(approvalRequestRepository.findAll())
                .as("a second Pending row of the same type is the duplicate the rule exists to stop")
                .hasSize(1);
    }

    // ---------------------------------------------------------------- the orphan sweep, for real

    /**
     * <b>The sweep of #78, over rows instead of mocks.</b> One Pending request whose reference no
     * longer resolves is reported; a healthy one is not; and a resolved one is not looked at, because
     * whether a two-year-old approval still points at a live entity is a question nobody can act on.
     */
    @Test
    void theSweepReportsTheBrokenReferenceAndLeavesTheHealthyOneAlone() {
        User asker = person("the-asker");
        User admin = admin("the-admin");

        String healthy = submitAs(asker, ApprovalRequestType.MasterGrant, "sana");
        String broken = submitAs(asker, ApprovalRequestType.General, "rota");
        pointAtNothing(broken);

        assertThat(orphanCheckService.reportOrphans())
                .as("exactly the one whose reference is gone")
                .isEqualTo(1);

        // Resolved rows are outside the sweep: only an open request pointing at nothing is a request
        // an admin is about to be unable to answer.
        approvalService.approve(healthy, "concedido", asAdmin(admin));
        pointAtNothing(healthy);
        assertThat(orphanCheckService.reportOrphans())
                .as("a resolved request is a record of something that happened, not a thing to fix")
                .isEqualTo(1);

        // And nothing is deleted or resolved: a row that lost its anchor is the evidence.
        assertThat(approvalRequestRepository.findById(broken).orElseThrow().getStatus())
                .isEqualTo(ApprovalStatus.Pending);
        assertThat(approvalRequestRepository.count()).isEqualTo(2);
    }

    /**
     * <b>The ERROR branch, from a real row.</b> An {@code entity_type} the resolver has no case for
     * throws - which is the right diagnosis for a flow added without teaching it - but a sweep is a
     * report over many rows, and one misconfigured row must not cost the report on every row behind
     * it. The row written here is exactly what the next phase produces the day it points a request at
     * something new and forgets the resolver.
     *
     * <p>The sentinel <b>used to be {@code game_table}</b>, and F3.4 turning that into a case the
     * resolver answers is what made this test start reporting two orphans instead of one: the fixture
     * stopped being unknown and became a request pointing at a table that is not there. So the
     * sentinel moved to a type nothing resolves - and the fact that it had to move is the point of the
     * {@code default} branch being there at all.
     */
    @Test
    void anUnknownEntityTypeDoesNotStopThePassAndTheRowBehindItIsStillReported() {
        User asker = person("the-asker");

        // Oldest first is the sweep's order, so the unknown-type row has to be created first for the
        // orphan behind it to be the one that would have been lost.
        String unknown = submitAs(asker, ApprovalRequestType.MasterGrant, "tipo desconocido");
        jdbcTemplate.update("update approval_requests set entity_type = 'table_session' where id = ?", unknown);

        String orphan = submitAs(asker, ApprovalRequestType.General, "detrás del desconocido");
        pointAtNothing(orphan);

        assertThat(orphanCheckService.reportOrphans())
                .as("the unknown type is logged at ERROR and is not an orphan; the real orphan behind it still is")
                .isEqualTo(1);
    }

    /** The scheduled entry point runs the same pass - it is the one a cron will actually call. */
    @Test
    void theScheduledEntryPointRunsWithoutAnOpenTransactionAroundIt() {
        User asker = person("the-asker");
        String broken = submitAs(asker, ApprovalRequestType.General, "rota");
        pointAtNothing(broken);

        // No assertion beyond "it completes": the sweep only logs, and what is being checked is that
        // the lazy `requestedBy` the WARN line reads resolves inside the job's own transaction. Self
        // invocation would have left it without one and this is where that shows.
        orphanCheckService.checkForOrphanedReferences();

        assertThat(approvalRequestRepository.findById(broken).orElseThrow().getStatus())
                .isEqualTo(ApprovalStatus.Pending);
    }

    // ---------------------------------------------------------------- the reference, at resolve time

    /** Both resolutions check the reference, not only the approval - a rejection is not a free pass. */
    @Test
    void neitherResolutionActsOnAGhost() {
        User asker = person("the-asker");
        User admin = admin("the-admin");
        String broken = submitAs(asker, ApprovalRequestType.General, "rota");
        pointAtNothing(broken);

        assertThat(conflictCodeOf(() -> approvalService.approve(broken, "sí", asAdmin(admin))))
                .isEqualTo("REQUEST_ENTITY_GONE");
        assertThat(conflictCodeOf(() -> approvalService.reject(broken, "no", asAdmin(admin))))
                .isEqualTo("REQUEST_ENTITY_GONE");
        assertThat(approvalRequestRepository.findById(broken).orElseThrow().getStatus())
                .as("refused whole: nothing half-applied")
                .isEqualTo(ApprovalStatus.Pending);
        assertThat(notificationRepository.findAll()).isEmpty();
    }

    /** Approving a {@code General} resolves it and does nothing else - which is its whole nature. */
    @Test
    void approvingAGeneralRequestHasNoEffectBeyondBeingResolved() {
        User asker = person("the-asker");
        User admin = admin("the-admin");
        String request = submitAs(asker, ApprovalRequestType.General, "una consulta");

        approvalService.approve(request, "respondido", asAdmin(admin));

        assertThat(userRoleRepository.findActiveRoleNames(asker.getId()))
                .doesNotContain(PlatformRole.MASTER.roleName());
        assertThat(notificationRepository.findAll())
                .singleElement()
                .satisfies(n -> assertThat(n.getNotificationType()).isEqualTo(NotificationType.ApprovalRequestApproved));
    }

    // ---------------------------------------------------------------- helpers

    /** What a two-thread race left behind: how many calls returned normally. */
    private record Outcome(int succeeded, List<String> refusals) {
    }

    /** Two calls, one latch, and no ordering between them - the only way a check-then-act shows itself. */
    private Outcome raceOn(Callable<?> first, Callable<?> second) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        List<String> refusals = Collections.synchronizedList(new ArrayList<>());

        for (Callable<?> call : List.of(first, second)) {
            pool.submit(() -> {
                start.await();
                try {
                    call.call();
                    succeeded.incrementAndGet();
                } catch (Exception refused) {
                    refusals.add(refused.getClass().getSimpleName() + ": " + refused.getMessage());
                }
                return null;
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        return new Outcome(succeeded.get(), refusals);
    }

    private static String conflictCodeOf(Runnable call) {
        try {
            call.run();
            return "no exception";
        } catch (ConflictException conflict) {
            return conflict.getErrorCode();
        }
    }

    private String submitAs(User asker, ApprovalRequestType type, String justification) {
        return approvalService.submit(type, justification, asPlayer(asker)).id();
    }

    /**
     * Points a request at an id nothing has.
     *
     * <p>An UPDATE and not a delete, because in F3.2 there is no way to delete what a request points
     * at: all three types point at the requester and {@code requested_by} is a real foreign key, so
     * the database refuses. F3.4's types point at rows nothing protects, and this is the state they
     * will reach on their own.
     */
    private void pointAtNothing(String requestId) {
        jdbcTemplate.update(
                "update approval_requests set entity_id = ? where id = ?",
                UUID.randomUUID().toString().replace("-", ""),
                requestId);
    }

    private CurrentUser asAdmin(User user) {
        return new CurrentUser(user.getId(), Set.of(PlatformRole.ADMIN.roleName()));
    }

    private CurrentUser asPlayer(User user) {
        return new CurrentUser(user.getId(), Set.of(PlatformRole.PLAYER.roleName()));
    }

    private User admin(String discordUsername) {
        User user = person(discordUsername);
        grant(user, PlatformRole.ADMIN);
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
