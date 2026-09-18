package com.centraldungeon.adminqueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.centraldungeon.adminqueue.dto.AdminQueueItemResponse;
import com.centraldungeon.approvals.ApprovalRequestRepository;
import com.centraldungeon.approvals.ApprovalRequestType;
import com.centraldungeon.approvals.ApprovalService;
import com.centraldungeon.approvals.ApprovalStatus;
import com.centraldungeon.common.config.AdminQueueProperties;
import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.common.security.CurrentUser;
import com.centraldungeon.notifications.NotificationRepository;
import com.centraldungeon.tables.GameTable;
import com.centraldungeon.tables.GameTableRepository;
import com.centraldungeon.tables.GameTableStatus;
import com.centraldungeon.tables.Master;
import com.centraldungeon.tables.MasterRepository;
import com.centraldungeon.tables.MasterType;
import com.centraldungeon.users.PlatformRole;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * The shared tray against a real database: the reservation race, the release job and the cost of
 * reading the tray (#100).
 *
 * <p><b>Two admins reserving the same item at the same instant is the race this whole slice exists
 * for</b>, and it is the third check-then-act of this phase to be tested seriously rather than
 * argued about - the last owner (#252) and resolving a request (#256) both turned out to be real.
 * Here it is the ordinary case and not the exotic one: the tray is shared, every admin sees the same
 * oldest row first, and the item at the top is the one two people click.
 *
 * <p><b>The release job had never run against rows.</b> Every assertion about it lived on mocks, so
 * "releases what expired and leaves the rest" was a statement about a mocked repository's arguments.
 * Here the cutoff is computed from the timeout that {@code application.yml} actually binds, the rows
 * are real, and what is asserted is the two columns afterwards.
 *
 * <p><b>The N+1 is measured, not reasoned about.</b> {@code findQueueItems} grew a {@code join
 * fetch} whose justification is a reading of the mapping; {@link Statistics#getPrepareStatementCount}
 * is the only thing that can say whether it worked. The measurement is taken twice, over a small
 * tray and a larger one <em>with different requesters in every row</em> - one requester repeated
 * would be hidden by the first-level cache and the test would pass without the fetch.
 *
 * <p>Wired with {@code @DynamicPropertySource}, not {@code @ServiceConnection}: see
 * {@code RegistrationServiceIT} for why.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class AdminQueueServiceIT {

    @Container
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        // The only way to count statements. It is a property of this context and of nothing else:
        // production never pays for it.
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
    }

    private static final Pageable FIRST_PAGE = PageRequest.of(0, 20);

    @Autowired
    private AdminQueueService adminQueueService;

    @Autowired
    private AdminQueueClaimReleaseService claimReleaseService;

    @Autowired
    private AdminQueueProperties adminQueueProperties;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ApprovalRequestRepository approvalRequestRepository;

    @Autowired
    private GameTableRepository gameTableRepository;

    @Autowired
    private MasterRepository masterRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User adminOne;
    private User adminTwo;

    @BeforeEach
    void setUp() {
        approvalRequestRepository.deleteAll();
        notificationRepository.deleteAll();
        masterRepository.deleteAll();
        gameTableRepository.deleteAll();
        userRepository.deleteAll();

        adminOne = person("admin-one");
        adminTwo = person("admin-two");
    }

    // ---------------------------------------------------------------- the race this slice is about

    /**
     * <b>Two admins reserving the same request at the same instant.</b>
     *
     * <p>Reserving is a check-then-act - read {@code claimed_by}, decide, write it - and nothing in
     * the schema refuses the second write: {@code claimed_by} is a plain nullable column with no
     * unique index and no version. If the two transactions were allowed to interleave, both would
     * read "free", both would pass, and the second UPDATE would simply overwrite the first. Two
     * admins would then each believe the item is theirs, both would go on to resolve it, and the
     * reservation would have achieved nothing at all.
     *
     * <p>What is asserted is the contract's promise: <b>exactly one holder, exactly one
     * {@code ITEM_ALREADY_CLAIMED}</b>, and the holder recorded in the row is the one whose call
     * returned.
     */
    @Test
    @DisplayName("two admins claiming the same request at once leave one holder and one 409")
    void twoAdminsClaimingTheSameRequestAtOnceLeaveOneHolder() throws InterruptedException {
        String requestId = pendingRequest(person("the-asker"), ApprovalRequestType.General, "una consulta");

        Outcome outcome = raceOn(
                () -> adminQueueService.claim(AdminQueueSource.APPROVAL_REQUEST.wireName(), requestId, adminOne.getId()),
                () -> adminQueueService.claim(AdminQueueSource.APPROVAL_REQUEST.wireName(), requestId, adminTwo.getId()));

        assertThat(outcome.succeeded())
                .as("one reservation, not two - the loser is ITEM_ALREADY_CLAIMED. Refusals seen: " + outcome.refusals())
                .isEqualTo(1);
        assertThat(outcome.refusals()).singleElement().asString().contains("ITEM_ALREADY_CLAIMED");

        String holder = jdbcTemplate.queryForObject(
                "select claimed_by from approval_requests where id = ?", String.class, requestId);
        assertThat(holder)
                .as("the row names exactly one admin, and it is the one whose call returned")
                .isEqualTo(outcome.winnerId());
        assertThat(jdbcTemplate.queryForObject(
                        "select claimed_at from approval_requests where id = ?", LocalDateTime.class, requestId))
                .as("a reservation is two columns or neither")
                .isNotNull();
    }

    /** The same race on the other source. One rule, two tables, and the lock has to hold in both. */
    @Test
    @DisplayName("two admins claiming the same table at once leave one holder and one 409")
    void twoAdminsClaimingTheSameTableAtOnceLeaveOneHolder() throws InterruptedException {
        GameTable table = tableInReview("Curse of Strahd", person("the-master"));

        Outcome outcome = raceOn(
                () -> adminQueueService.claim(AdminQueueSource.GAME_TABLE.wireName(), table.getId(), adminOne.getId()),
                () -> adminQueueService.claim(AdminQueueSource.GAME_TABLE.wireName(), table.getId(), adminTwo.getId()));

        assertThat(outcome.succeeded())
                .as("one reservation, not two. Refusals seen: " + outcome.refusals())
                .isEqualTo(1);
        assertThat(outcome.refusals()).singleElement().asString().contains("ITEM_ALREADY_CLAIMED");
        assertThat(jdbcTemplate.queryForObject(
                        "select claimed_by from game_tables where id = ?", String.class, table.getId()))
                .isEqualTo(outcome.winnerId());
    }

    /**
     * Eight admins rather than two, because a two-thread race can be won by luck. The invariant is
     * the same one and it has to survive contention: one row, one holder.
     */
    @Test
    @DisplayName("eight admins clicking the same row produce one holder and seven refusals")
    void eightAdminsClaimingTheSameRequestLeaveOneHolder() throws InterruptedException {
        String requestId = pendingRequest(person("the-asker"), ApprovalRequestType.General, "una consulta");
        List<User> admins = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            admins.add(person("admin-" + i));
        }

        AtomicInteger succeeded = new AtomicInteger();
        List<String> refusals = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(admins.size());
        CountDownLatch start = new CountDownLatch(1);
        for (User admin : admins) {
            pool.submit(() -> {
                start.await();
                try {
                    adminQueueService.claim(AdminQueueSource.APPROVAL_REQUEST.wireName(), requestId, admin.getId());
                    succeeded.incrementAndGet();
                } catch (Exception refused) {
                    refusals.add(codeOf(refused));
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        assertThat(succeeded.get()).as("Refusals seen: " + refusals).isEqualTo(1);
        assertThat(refusals).hasSize(7).allMatch("ITEM_ALREADY_CLAIMED"::equals);
    }

    // ---------------------------------------------------------------- idempotence, both directions

    /**
     * <b>Clicking twice must not move the clock.</b> If reserving refreshed {@code claimed_at}, an
     * admin whose screen refetches every fifteen seconds - which is what the tray does - would renew
     * their own reservation for ever and the release job would never reach it. The item would be out
     * of everybody else's tray until that browser tab is closed.
     */
    @Test
    @DisplayName("claiming twice as the same admin answers twice and does not move claimed_at")
    void claimingTwiceAsTheSameAdminDoesNotMoveTheClock() throws InterruptedException {
        String requestId = pendingRequest(person("the-asker"), ApprovalRequestType.General, "una consulta");

        AdminQueueItemResponse first =
                adminQueueService.claim(AdminQueueSource.APPROVAL_REQUEST.wireName(), requestId, adminOne.getId());
        LocalDateTime taken = claimedAtOfRequest(requestId);
        // Enough for LocalDateTime.now() to be a different value: a re-write would show.
        Thread.sleep(1_100);
        AdminQueueItemResponse second =
                adminQueueService.claim(AdminQueueSource.APPROVAL_REQUEST.wireName(), requestId, adminOne.getId());

        assertThat(second.claimedByName()).isEqualTo(first.claimedByName());
        assertThat(claimedAtOfRequest(requestId))
                .as("the second click is the same reservation, not a renewed one")
                .isEqualTo(taken);
    }

    /** The same for a table, because the two sources are two code paths. */
    @Test
    @DisplayName("claiming a table twice as the same admin does not move claimed_at")
    void claimingATableTwiceAsTheSameAdminDoesNotMoveTheClock() throws InterruptedException {
        GameTable table = tableInReview("Tomb of Annihilation", person("the-master"));

        adminQueueService.claim(AdminQueueSource.GAME_TABLE.wireName(), table.getId(), adminOne.getId());
        LocalDateTime taken = claimedAtOfTable(table.getId());
        Thread.sleep(1_100);
        adminQueueService.claim(AdminQueueSource.GAME_TABLE.wireName(), table.getId(), adminOne.getId());

        assertThat(claimedAtOfTable(table.getId())).isEqualTo(taken);
    }

    /**
     * <b>One admin does not take work off another's desk.</b> Releasing is the undo of reserving, and
     * an undo that reached other people's reservations would be a way around the whole rule: click
     * release, click claim, and the item is yours while somebody else is writing their answer.
     */
    @Test
    @DisplayName("releasing what another admin holds is refused and leaves the reservation alone")
    void releasingWhatAnotherAdminHoldsIsRefused() {
        String requestId = pendingRequest(person("the-asker"), ApprovalRequestType.General, "una consulta");
        adminQueueService.claim(AdminQueueSource.APPROVAL_REQUEST.wireName(), requestId, adminOne.getId());

        assertThatThrownBy(() -> adminQueueService.release(
                        AdminQueueSource.APPROVAL_REQUEST.wireName(), requestId, adminTwo.getId()))
                .isInstanceOf(ConflictException.class)
                .extracting(refused -> ((ConflictException) refused).getErrorCode())
                .isEqualTo(ConflictException.ITEM_ALREADY_CLAIMED);

        assertThat(jdbcTemplate.queryForObject(
                        "select claimed_by from approval_requests where id = ?", String.class, requestId))
                .isEqualTo(adminOne.getId());
    }

    /** Releasing something nobody holds is the state the caller asked for: it is not an error. */
    @Test
    @DisplayName("releasing something nobody holds is accepted and changes nothing")
    void releasingSomethingNobodyHoldsIsAccepted() {
        String requestId = pendingRequest(person("the-asker"), ApprovalRequestType.General, "una consulta");

        adminQueueService.release(AdminQueueSource.APPROVAL_REQUEST.wireName(), requestId, adminOne.getId());

        assertThat(jdbcTemplate.queryForObject(
                        "select claimed_by from approval_requests where id = ?", String.class, requestId))
                .isNull();
    }

    // ---------------------------------------------------------------- what the tray shows

    /**
     * The reading end of the reservation: an item another admin took is not in this admin's tray at
     * all, and it is still in the tray of the one who took it.
     */
    @Test
    @DisplayName("a reserved item leaves everybody's tray but its holder's")
    void aReservedItemLeavesEverybodysTrayButItsHolders() {
        String requestId = pendingRequest(person("the-asker"), ApprovalRequestType.General, "una consulta");
        GameTable table = tableInReview("Dragon Heist", person("the-master"));

        assertThat(idsOf(adminTwo)).containsExactlyInAnyOrder(requestId, table.getId());

        adminQueueService.claim(AdminQueueSource.APPROVAL_REQUEST.wireName(), requestId, adminOne.getId());
        adminQueueService.claim(AdminQueueSource.GAME_TABLE.wireName(), table.getId(), adminOne.getId());

        assertThat(idsOf(adminTwo)).as("what somebody took is nobody else's to start on").isEmpty();
        assertThat(idsOf(adminOne))
                .as("and the one who took it still sees it, or they could not finish it")
                .containsExactlyInAnyOrder(requestId, table.getId());
        assertThat(adminQueueService.list(adminOne.getId(), FIRST_PAGE).content())
                .allSatisfy(item -> {
                    assertThat(item.claimedByName()).isEqualTo("admin-one");
                    assertThat(item.claimedAt()).isNotNull();
                });
    }

    /**
     * {@code Preparation} and nothing else (modelo-datos.md §5, #245). The four statuses excluded
     * here are the ones a loose reading of #176 would have let in, and each of them would be showing
     * an admin work that is not theirs to do.
     */
    @Test
    @DisplayName("only a table in Preparation is in the tray")
    void onlyATableInPreparationIsInTheTray() {
        User master = person("the-master");
        GameTable inReview = tableInReview("In Review", master);
        tableIn("Draft", master, GameTableStatus.Draft);
        tableIn("Sent Back", master, GameTableStatus.ChangesRequested);
        tableIn("No Master Yet", master, GameTableStatus.Unassigned);
        tableIn("Running", master, GameTableStatus.Opened);

        assertThat(idsOf(adminOne)).containsExactly(inReview.getId());
    }

    /**
     * The merge pages in memory, so the total has to count the whole tray and not the slice - which
     * is the number the frontend's pager reads (#173).
     */
    @Test
    @DisplayName("the page total counts the whole merged tray, across both sources")
    void thePageTotalCountsTheWholeMergedTray() {
        for (int i = 0; i < 4; i++) {
            pendingRequest(person("asker-" + i), ApprovalRequestType.General, "consulta " + i);
        }
        User master = person("the-master");
        tableInReview("Table A", master);
        tableInReview("Table B", master);

        PageResponse<AdminQueueItemResponse> firstPage = adminQueueService.list(adminOne.getId(), PageRequest.of(0, 4));

        assertThat(firstPage.content()).hasSize(4);
        assertThat(firstPage.totalElements()).isEqualTo(6);
        assertThat(firstPage.totalPages()).isEqualTo(2);
        assertThat(adminQueueService.list(adminOne.getId(), PageRequest.of(1, 4)).content())
                .as("the tail of the merge, not an empty second page")
                .hasSize(2);
    }

    /** Oldest first is the tray's whole promise (#136), and it holds across the two sources. */
    @Test
    @DisplayName("the one who has been waiting longest comes first, whichever table it lives in")
    void theOldestItemComesFirstAcrossSources() {
        String newest = pendingRequest(person("asker-new"), ApprovalRequestType.General, "la nueva");
        GameTable middle = tableInReview("Middle", person("master-mid"));
        String oldest = pendingRequest(person("asker-old"), ApprovalRequestType.General, "la vieja");

        backdateRequest(oldest, 3);
        backdateTable(middle.getId(), 2);
        backdateRequest(newest, 1);

        assertThat(idsOf(adminOne)).containsExactly(oldest, middle.getId(), newest);
    }

    // ---------------------------------------------------------------- the job, against rows

    /**
     * <b>The timeout comes from {@code application.yml} and the job uses it.</b> The binding had
     * never been exercised: a {@code @ConfigurationProperties} record that is not on
     * {@code @EnableConfigurationProperties} fails at injection, and one whose prefix is misspelled
     * silently keeps its default. Both are invisible until a job runs in a real context.
     */
    @Test
    @DisplayName("app.admin-queue.claim-timeout binds from application.yml")
    void theTimeoutIsBoundFromTheYaml() {
        assertThat(adminQueueProperties.claimTimeout()).isEqualTo(Duration.ofMinutes(15));
    }

    /**
     * <b>The sweep, over rows.</b> One reservation older than the timeout, one younger, and the two
     * sources at once: what is expired goes back and what is not is left exactly as it was.
     */
    @Test
    @DisplayName("the job releases what passed the timeout and does not touch what did not")
    void theJobReleasesTheExpiredReservationsOnly() {
        String expiredRequest = pendingRequest(person("asker-old"), ApprovalRequestType.General, "vieja");
        String freshRequest = pendingRequest(person("asker-new"), ApprovalRequestType.General, "nueva");
        User master = person("the-master");
        GameTable expiredTable = tableInReview("Expired", master);
        GameTable freshTable = tableInReview("Fresh", master);

        for (String id : List.of(expiredRequest, freshRequest)) {
            adminQueueService.claim(AdminQueueSource.APPROVAL_REQUEST.wireName(), id, adminOne.getId());
        }
        for (GameTable table : List.of(expiredTable, freshTable)) {
            adminQueueService.claim(AdminQueueSource.GAME_TABLE.wireName(), table.getId(), adminOne.getId());
        }
        // Sixteen minutes ago is past a fifteen-minute promise; fourteen is not. The two are one
        // minute either side of the value the yaml binds, so the cutoff itself is what is being read.
        ageRequestClaim(expiredRequest, 16);
        ageRequestClaim(freshRequest, 14);
        ageTableClaim(expiredTable.getId(), 16);
        ageTableClaim(freshTable.getId(), 14);

        assertThat(claimReleaseService.releaseExpiredClaims())
                .as("exactly the two that passed the timeout")
                .isEqualTo(2);

        assertThat(claimedByOfRequest(expiredRequest)).isNull();
        assertThat(claimedAtOfRequest(expiredRequest)).as("both columns or neither").isNull();
        assertThat(claimedByOfRequest(freshRequest)).isEqualTo(adminOne.getId());
        assertThat(claimedByOfTable(expiredTable.getId())).isNull();
        assertThat(claimedByOfTable(freshTable.getId())).isEqualTo(adminOne.getId());
        assertThat(idsOf(adminTwo))
                .as("and what came back is in everybody's tray again")
                .contains(expiredRequest, expiredTable.getId());
    }

    /**
     * A resolved row keeps whoever was working on it. It is in nobody's tray, so clearing the column
     * would be a write with no reader that also erases a piece of the record.
     */
    @Test
    @DisplayName("the job does not touch a request that was already answered")
    void theJobLeavesResolvedRowsAlone() {
        User asker = person("the-asker");
        String requestId = pendingRequest(asker, ApprovalRequestType.General, "una consulta");
        adminQueueService.claim(AdminQueueSource.APPROVAL_REQUEST.wireName(), requestId, adminOne.getId());
        approvalService.approve(requestId, "respondido", asAdmin(adminOne));
        ageRequestClaim(requestId, 60);

        assertThat(claimReleaseService.releaseExpiredClaims()).isZero();

        assertThat(approvalRequestRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(ApprovalStatus.Approved);
        assertThat(claimedByOfRequest(requestId))
                .as("who answered it is part of what happened")
                .isEqualTo(adminOne.getId());
    }

    /**
     * <b>The {@code @Scheduled} method, fired by the scheduler and not by this test.</b> Everything
     * else here calls {@link AdminQueueClaimReleaseService#releaseExpiredClaims()} directly, which
     * proves the rule and says nothing about the wiring: whether {@code @EnableScheduling} reaches
     * this bean, whether {@code fixedDelay} parses, and - the one that fails silently - whether the
     * {@code @Transactional} on the scheduled method itself is doing anything. Without it the
     * entities would come back detached and clearing their columns would be writes nobody applies,
     * with the job logging a count having changed no rows at all.
     *
     * <p>It waits, because there is no way not to: the job's {@code initialDelay} is thirty seconds
     * from the moment the context started. That is also why it is one test and not the shape of all
     * of them.
     */
    @Test
    @DisplayName("the scheduled job fires on its own and the release actually reaches the database")
    void theScheduledJobFiresOnItsOwnInALiveJvm() throws InterruptedException {
        String requestId = pendingRequest(person("the-asker"), ApprovalRequestType.General, "una consulta");
        adminQueueService.claim(AdminQueueSource.APPROVAL_REQUEST.wireName(), requestId, adminOne.getId());
        ageRequestClaim(requestId, 60);

        boolean released = false;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(100);
        while (System.nanoTime() < deadline) {
            if (claimedByOfRequest(requestId) == null) {
                released = true;
                break;
            }
            Thread.sleep(1_000);
        }

        assertThat(released)
                .as("nobody called the job: the scheduler did, and the write it made is visible here")
                .isTrue();
    }

    // ---------------------------------------------------------------- the N+1, counted

    /**
     * <b>Measured, because {@code join fetch} is an argument until something counts statements.</b>
     *
     * <p>Two trays, one with two rows and one with seven, every row belonging to a <em>different</em>
     * requester - which is the whole point. With one requester repeated, the first-level cache
     * answers every row after the first and a missing fetch looks identical to a present one.
     *
     * <p>The assertion is that the count does not move with the row count. An absolute number would
     * be pinning an implementation detail; "reading three times as many rows costs the same" is the
     * property that makes a tray refetched every fifteen seconds by every admin affordable.
     */
    @Test
    @DisplayName("reading the tray costs the same whether it has two rows or seven")
    void theTrayDoesNotGrowItsQueryCountWithItsRowCount() {
        for (int i = 0; i < 2; i++) {
            pendingRequest(person("asker-" + i), ApprovalRequestType.General, "consulta " + i);
        }
        tableInReview("Table 0", person("master-0"));
        long small = statementsWhileListing();

        for (int i = 2; i < 7; i++) {
            pendingRequest(person("asker-" + i), ApprovalRequestType.General, "consulta " + i);
        }
        tableInReview("Table 1", person("master-1"));
        tableInReview("Table 2", person("master-2"));
        long large = statementsWhileListing();

        assertThat(adminQueueService.list(adminOne.getId(), FIRST_PAGE).totalElements())
                .as("the second measurement really is over a bigger tray")
                .isEqualTo(10);
        assertThat(large)
                .as("a tray of ten rows took " + large + " statements where a tray of three took " + small
                        + ": the per-row read is back")
                .isEqualTo(small);
    }

    // ---------------------------------------------------------------- helpers

    /** What a race left behind: how many calls returned, what the losers said, and who won. */
    private record Outcome(int succeeded, List<String> refusals, String winnerId) {
    }

    /**
     * Two claims, one latch, and no ordering between them. Each call returns the id of the admin it
     * was made for, so the winner can be compared against the row afterwards.
     */
    private Outcome raceOn(ClaimCall first, ClaimCall second) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        List<String> refusals = Collections.synchronizedList(new ArrayList<>());
        List<String> winners = Collections.synchronizedList(new ArrayList<>());

        List<ClaimCall> calls = List.of(first, second);
        for (ClaimCall call : calls) {
            pool.submit(() -> {
                start.await();
                try {
                    AdminQueueItemResponse item = call.call();
                    succeeded.incrementAndGet();
                    winners.add(item.claimedByName());
                } catch (Exception refused) {
                    refusals.add(codeOf(refused) + ": " + refused.getMessage());
                }
                return null;
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        String winnerId = winners.size() == 1 ? idOfPersonNamed(winners.getFirst()) : null;
        return new Outcome(succeeded.get(), refusals, winnerId);
    }

    /** A claim, as the race runs it. */
    @FunctionalInterface
    private interface ClaimCall {
        AdminQueueItemResponse call();
    }

    private static String codeOf(Throwable refused) {
        return refused instanceof ConflictException conflict
                ? conflict.getErrorCode()
                : refused.getClass().getSimpleName();
    }

    private String idOfPersonNamed(String name) {
        return userRepository.findAll().stream()
                .filter(user -> name.equals(user.getName()))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    /** How many statements one read of the tray costs, from Hibernate's own counter. */
    private long statementsWhileListing() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        adminQueueService.list(adminOne.getId(), FIRST_PAGE);
        return statistics.getPrepareStatementCount();
    }

    private List<String> idsOf(User admin) {
        return adminQueueService.list(admin.getId(), FIRST_PAGE).content().stream()
                .map(AdminQueueItemResponse::id)
                .toList();
    }

    private String pendingRequest(User asker, ApprovalRequestType type, String justification) {
        return approvalService
                .submit(type, justification, new CurrentUser(asker.getId(), Set.of(PlatformRole.PLAYER.roleName())))
                .id();
    }

    private GameTable tableInReview(String name, User master) {
        return tableIn(name, master, GameTableStatus.Preparation);
    }

    private GameTable tableIn(String name, User master, GameTableStatus status) {
        GameTable table = new GameTable(name, master);
        table.setStatus(status);
        GameTable saved = gameTableRepository.save(table);
        if (status != GameTableStatus.Unassigned) {
            masterRepository.save(new Master(saved, master, MasterType.Primary));
        }
        return saved;
    }

    private void backdateRequest(String requestId, int hours) {
        jdbcTemplate.update(
                "update approval_requests set created_at = date_sub(now(), interval ? hour) where id = ?",
                hours,
                requestId);
    }

    private void backdateTable(String tableId, int hours) {
        jdbcTemplate.update(
                "update game_tables set created_at = date_sub(now(), interval ? hour) where id = ?", hours, tableId);
    }

    /**
     * Backdates a reservation, <b>on the clock the application writes with</b>.
     *
     * <p>The timestamp is computed in Java and sent as a parameter, not built with the database's
     * {@code now()}. It matters: {@code claimed_at} is a {@code LocalDateTime}, a wall-clock reading
     * with no zone attached, and every one of them this application ever writes comes from the JVM.
     * The job's cutoff is {@code LocalDateTime.now()} too, so the comparison is between two readings
     * of the same clock.
     *
     * <p>The first version of this helper used {@code date_sub(now(), interval ? minute)} and both
     * job tests failed releasing nothing. MySQL's {@code now()} answers in the session's time zone,
     * which is not the JVM's: a reservation "sixteen minutes ago" by the database's clock landed
     * <em>hours in the future</em> by the cutoff's, so nothing was ever expired. Nothing was wrong
     * with the job - the test was asking the question in a clock the production code never uses.
     */
    private void ageRequestClaim(String requestId, int minutes) {
        jdbcTemplate.update(
                "update approval_requests set claimed_at = ? where id = ?", minutesAgo(minutes), requestId);
    }

    /** Backdates a table's reservation. Same clock, same reason as {@link #ageRequestClaim}. */
    private void ageTableClaim(String tableId, int minutes) {
        jdbcTemplate.update("update game_tables set claimed_at = ? where id = ?", minutesAgo(minutes), tableId);
    }

    /** A wall-clock reading this many minutes back, as JDBC wants it. */
    private static Timestamp minutesAgo(int minutes) {
        return Timestamp.valueOf(LocalDateTime.now().minusMinutes(minutes));
    }

    private @org.jspecify.annotations.Nullable String claimedByOfRequest(String requestId) {
        return jdbcTemplate.queryForObject(
                "select claimed_by from approval_requests where id = ?", String.class, requestId);
    }

    private @org.jspecify.annotations.Nullable LocalDateTime claimedAtOfRequest(String requestId) {
        return jdbcTemplate.queryForObject(
                "select claimed_at from approval_requests where id = ?", LocalDateTime.class, requestId);
    }

    private @org.jspecify.annotations.Nullable String claimedByOfTable(String tableId) {
        return jdbcTemplate.queryForObject("select claimed_by from game_tables where id = ?", String.class, tableId);
    }

    private @org.jspecify.annotations.Nullable LocalDateTime claimedAtOfTable(String tableId) {
        return jdbcTemplate.queryForObject("select claimed_at from game_tables where id = ?", LocalDateTime.class, tableId);
    }

    private CurrentUser asAdmin(User user) {
        return new CurrentUser(user.getId(), Set.of(PlatformRole.ADMIN.roleName()));
    }

    private User person(String name) {
        User user = new User(UUID.randomUUID().toString().replace("-", ""), name);
        user.setName(name);
        return userRepository.save(user);
    }
}
