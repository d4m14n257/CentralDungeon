package com.centraldungeon.adminqueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.centraldungeon.adminqueue.dto.AdminQueueItemResponse;
import com.centraldungeon.approvals.ApprovalRequest;
import com.centraldungeon.approvals.ApprovalRequestRepository;
import com.centraldungeon.approvals.ApprovalRequestType;
import com.centraldungeon.approvals.ApprovalStatus;
import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.tables.GameTable;
import com.centraldungeon.tables.GameTableRepository;
import com.centraldungeon.tables.GameTableStatus;
import com.centraldungeon.tables.Master;
import com.centraldungeon.tables.MasterService;
import com.centraldungeon.tables.MasterType;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Every rule of the shared tray (contrato F3.3 §1 y §2), one test each.
 *
 * <p>Three of them are what the slice is actually about and the rest exists so they cannot be broken
 * quietly:
 *
 * <ul>
 *   <li><b>What enters is taxative</b> (modelo-datos.md §5): {@code Pending} requests and tables in
 *       {@code Preparation}, <em>and nothing else</em>. A loose reading of #176 would also list
 *       {@code Draft}, {@code ChangesRequested} and {@code Unassigned}, and each of those is work that
 *       is not an admin's to do right now.</li>
 *   <li><b>A reserved item disappears for the rest and stays for whoever took it</b> - the filter the
 *       whole mechanism of #100 rests on, asserted as the actor reaching the query.</li>
 *   <li><b>Reserving is idempotent for the same admin and a 409 for anybody else.</b> Including the
 *       part that is easy to get wrong: the second click must not push {@code claimed_at} forward, or
 *       an admin clicking every fourteen minutes would hold an item for ever.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminQueueServiceTest {

    private static final String ACTOR = "admin-1";

    private static final Pageable FIRST_PAGE = PageRequest.of(0, 20);

    @Mock
    private ApprovalRequestRepository approvalRequestRepository;

    @Mock
    private GameTableRepository gameTableRepository;

    @Mock
    private MasterService masterService;

    @Mock
    private UserService userService;

    private AdminQueueService service() {
        return new AdminQueueService(approvalRequestRepository, gameTableRepository, masterService, userService);
    }

    // ------------------------------------------------------------------ what enters

    /**
     * «La bandeja es un UNION sobre {@code approval_requests} (Pending) y {@code game_tables}
     * (Preparation)» - and nothing else (modelo-datos.md §5, #245).
     *
     * <p>Asserted on the arguments each query receives rather than on what comes back, because what
     * has to be pinned is <em>what is being asked</em>. A test that built fake rows and counted the
     * ones that came out would stay green the day the filter widened.
     */
    @Test
    void onlyPendingRequestsAndTablesInPreparationGetIn() {
        noQueue();

        service().list(ACTOR, FIRST_PAGE);

        verify(approvalRequestRepository).findQueueItems(eq(ApprovalStatus.Pending), any(), eq(ACTOR), any());
        verify(gameTableRepository).findQueueItems(eq(GameTableStatus.Preparation), eq(ACTOR), any());
        // Draft, ChangesRequested and Unassigned do not enter: nobody sent them, the ball is with the
        // master, or what they are missing is a master and not a review.
        verify(gameTableRepository, never()).findQueueItems(eq(GameTableStatus.Draft), anyString(), any());
        verify(gameTableRepository, never()).findQueueItems(eq(GameTableStatus.ChangesRequested), anyString(), any());
        verify(gameTableRepository, never()).findQueueItems(eq(GameTableStatus.Unassigned), anyString(), any());
    }

    /**
     * The actor enters the WHERE of both sources (#121, #100): an item another admin took is not in
     * this answer, and one this admin took is.
     */
    @Test
    void theActorEntersEverySourcesQuery() {
        noQueue();

        service().list("admin-7", FIRST_PAGE);

        verify(approvalRequestRepository).findQueueItems(any(), any(), eq("admin-7"), any());
        verify(gameTableRepository).findQueueItems(any(), eq("admin-7"), any());
    }

    /**
     * The per-source ceiling of §1.a of the contract. A merge with no ceiling is a memory load nobody
     * declared, and «it cannot grow» is the sentence every unbounded read was ever written under.
     */
    @Test
    void everySourceBringsAtMostTwoHundredRows() {
        noQueue();

        service().list(ACTOR, FIRST_PAGE);

        ArgumentCaptor<Pageable> ceiling = ArgumentCaptor.forClass(Pageable.class);
        verify(approvalRequestRepository).findQueueItems(any(), any(), anyString(), ceiling.capture());
        assertThat(ceiling.getValue().getPageSize()).isEqualTo(AdminQueueService.QUEUE_SOURCE_CAP);
        assertThat(ceiling.getValue().getPageNumber()).isZero();
    }

    /**
     * And when it hits it, <b>it says so, naming the source</b>. That is the half that matters: a
     * ceiling reached in silence is a tray that under-reports without anybody noticing, which is exactly
     * how this limit would turn from a protection into a bug.
     */
    @Test
    void aSourceThatHitsTheCeilingIsLoggedByName() {
        List<ApprovalRequest> lleno = new java.util.ArrayList<>();
        for (int i = 0; i < AdminQueueService.QUEUE_SOURCE_CAP; i++) {
            lleno.add(pendingRequest(String.format("req-%03d", i), "2026-09-01T09:00"));
        }
        queue(lleno, List.of());

        List<String> warnings = capturingWarnings(() -> service().list(ACTOR, FIRST_PAGE));

        assertThat(warnings).anySatisfy(line -> assertThat(line).contains("approval_request"));
    }

    // ------------------------------------------------------------------ the order and the page

    /**
     * «El que espera hace más tiempo va primero», the same as the master's tray (#136): urgency here is
     * time and not volume. And the ordering is <b>across sources</b>, which is what makes this one tray
     * rather than two listings stuck together.
     */
    @Test
    void whateverHasWaitedLongestComesFirst() {
        ApprovalRequest viejo = pendingRequest("req-viejo", "2026-09-01T09:00");
        ApprovalRequest nuevo = pendingRequest("req-nuevo", "2026-09-03T09:00");
        GameTable mesa = tableInReview("table-1", "2026-09-02T09:00");
        queue(List.of(nuevo, viejo), List.of(mesa));

        PageResponse<AdminQueueItemResponse> page = service().list(ACTOR, FIRST_PAGE);

        assertThat(page.content())
                .extracting(AdminQueueItemResponse::id)
                .containsExactly("req-viejo", "table-1", "req-nuevo");
    }

    /** Tie-break by id, so page 2 is the remainder and not a fresh shuffle (#171). */
    @Test
    void theTieIsBrokenByIdSoThatPageTwoIsStable() {
        ApprovalRequest primero = pendingRequest("req-aaa", "2026-09-01T09:00");
        ApprovalRequest segundo = pendingRequest("req-bbb", "2026-09-01T09:00");
        queue(List.of(segundo, primero), List.of());

        PageResponse<AdminQueueItemResponse> page = service().list(ACTOR, FIRST_PAGE);

        assertThat(page.content()).extracting(AdminQueueItemResponse::id).containsExactly("req-aaa", "req-bbb");
    }

    /**
     * The total is the whole tray's and not the slice's: the merge is already in memory, so counting it
     * exactly is free (#173).
     */
    @Test
    void theTotalIsTheWholeTrayAndThePageIsTheSlice() {
        queue(
                List.of(
                        pendingRequest("req-1", "2026-09-01T09:00"),
                        pendingRequest("req-2", "2026-09-02T09:00"),
                        pendingRequest("req-3", "2026-09-03T09:00")),
                List.of());

        PageResponse<AdminQueueItemResponse> page = service().list(ACTOR, PageRequest.of(1, 2));

        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.content()).extracting(AdminQueueItemResponse::id).containsExactly("req-3");
    }

    /** A page beyond the end is empty, not an exception: the frontend can ask for it from the URL (#185). */
    @Test
    void aPageBeyondTheEndIsEmpty() {
        queue(List.of(pendingRequest("req-1", "2026-09-01T09:00")), List.of());

        PageResponse<AdminQueueItemResponse> page = service().list(ACTOR, PageRequest.of(5, 20));

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isEqualTo(1);
    }

    /** An empty tray is good news and not a broken screen (#136). */
    @Test
    void anEmptyTrayIsAnOrdinaryAnswer() {
        noQueue();

        PageResponse<AdminQueueItemResponse> page = service().list(ACTOR, FIRST_PAGE);

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
    }

    // ------------------------------------------------------------------ the shape of an item

    @Test
    void aRequestIsPublishedWithItsTypeItsReasonAndWhoAsked() {
        ApprovalRequest request = pendingRequest("req-1", "2026-09-01T09:00");
        queue(List.of(request), List.of());

        AdminQueueItemResponse item = service().list(ACTOR, FIRST_PAGE).content().getFirst();

        assertThat(item.type()).isEqualTo("approval_request");
        assertThat(item.kind()).isEqualTo("ApprovalRequest");
        assertThat(item.title()).isEqualTo("MasterGrant");
        assertThat(item.detail()).isEqualTo("quiero dirigir");
        assertThat(item.requestedByName()).isEqualTo("carla");
        assertThat(item.waitingSince()).isEqualTo(LocalDateTime.parse("2026-09-01T09:00"));
        assertThat(item.claimedByName()).isNull();
    }

    /**
     * A table is published with its name and the master who sent it, resolved in one batch. <b>With no
     * detail</b>, and that is no oversight: a table's justification is the table, read by opening it.
     */
    @Test
    void aTableIsPublishedWithItsNameAndItsMasterAndNoDetail() {
        GameTable table = tableInReview("table-1", "2026-09-01T09:00");
        User primary = user("master-1", "ana");
        queue(List.of(), List.of(table));
        when(masterService.findPrimariesByTables(List.of("table-1")))
                .thenReturn(Map.of("table-1", new Master(table, primary, MasterType.Primary)));

        AdminQueueItemResponse item = service().list(ACTOR, FIRST_PAGE).content().getFirst();

        assertThat(item.type()).isEqualTo("game_table");
        assertThat(item.kind()).isEqualTo("TableWaitingReview");
        assertThat(item.title()).isEqualTo("La Cripta");
        assertThat(item.requestedByName()).isEqualTo("ana");
        assertThat(item.detail()).isNull();
    }

    /**
     * And if the table has no live Primary it falls back to whoever created the row: still true, and the
     * tray never prints an id (#136).
     */
    @Test
    void aTableWithNoLivePrimaryNamesWhoeverCreatedIt() {
        GameTable table = tableInReview("table-1", "2026-09-01T09:00");
        queue(List.of(), List.of(table));
        when(masterService.findPrimariesByTables(List.of("table-1"))).thenReturn(Map.of());

        AdminQueueItemResponse item = service().list(ACTOR, FIRST_PAGE).content().getFirst();

        assertThat(item.requestedByName()).isEqualTo("quien-la-creo");
    }

    /** The whole page's masters are resolved in one query, not one per row (#136, contract §3.4). */
    @Test
    void thePagesMastersAreResolvedInOneQuery() {
        queue(
                List.of(),
                List.of(tableInReview("table-1", "2026-09-01T09:00"), tableInReview("table-2", "2026-09-02T09:00")));
        when(masterService.findPrimariesByTables(any())).thenReturn(Map.of());

        service().list(ACTOR, FIRST_PAGE);

        verify(masterService).findPrimariesByTables(List.of("table-1", "table-2"));
    }

    // ------------------------------------------------------------------ reservar

    @Test
    void claimingAFreeItemPutsItInTheAdminsName() {
        ApprovalRequest request = pendingRequest("req-1", "2026-09-01T09:00");
        User admin = user(ACTOR, "damian");
        when(userService.getById(ACTOR)).thenReturn(admin);
        when(approvalRequestRepository.lockById("req-1")).thenReturn(Optional.of(request));

        AdminQueueItemResponse item = service().claim("approval_request", "req-1", ACTOR);

        assertThat(request.getClaimedBy()).isEqualTo(admin);
        assertThat(request.getClaimedAt()).isNotNull();
        assertThat(item.claimedByName()).isEqualTo("damian");
    }

    /**
     * Idempotent for the same admin, and <b>without moving {@code claimed_at}</b>. The second half is
     * the one that gets forgotten: if every click refreshed the clock, an admin clicking every fourteen
     * minutes would keep the item for ever and the job would never reach it.
     */
    @Test
    void claimingTwiceAsTheSameAdminDoesNotMoveTheClock() {
        ApprovalRequest request = pendingRequest("req-1", "2026-09-01T09:00");
        User admin = user(ACTOR, "damian");
        LocalDateTime original = LocalDateTime.parse("2026-09-01T10:00");
        request.claim(admin, original);
        when(userService.getById(ACTOR)).thenReturn(admin);
        when(approvalRequestRepository.lockById("req-1")).thenReturn(Optional.of(request));

        AdminQueueItemResponse item = service().claim("approval_request", "req-1", ACTOR);

        assertThat(request.getClaimedAt()).isEqualTo(original);
        assertThat(item.claimedByName()).isEqualTo("damian");
    }

    /** If somebody else holds it, 409 with the code the screen needs to write the sentence (#197). */
    @Test
    void claimingWhatAnotherAdminHoldsIs409() {
        ApprovalRequest request = pendingRequest("req-1", "2026-09-01T09:00");
        request.claim(user("admin-2", "otra"), LocalDateTime.parse("2026-09-01T10:00"));
        when(userService.getById(ACTOR)).thenReturn(user(ACTOR, "damian"));
        when(approvalRequestRepository.lockById("req-1")).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service().claim("approval_request", "req-1", ACTOR))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo(ConflictException.ITEM_ALREADY_CLAIMED);

        assertThat(request.getClaimedBy().getId()).isEqualTo("admin-2");
    }

    /** The claim is taken under the row's lock, which is what gives the race between two admins one winner (#252, #256). */
    @Test
    void theClaimIsTakenUnderTheRowsLock() {
        ApprovalRequest request = pendingRequest("req-1", "2026-09-01T09:00");
        when(userService.getById(ACTOR)).thenReturn(user(ACTOR, "damian"));
        when(approvalRequestRepository.lockById("req-1")).thenReturn(Optional.of(request));

        service().claim("approval_request", "req-1", ACTOR);

        verify(approvalRequestRepository).lockById("req-1");
        verify(approvalRequestRepository, never()).findById(anyString());
    }

    /** The same on the tables side: {@code findByIdForUpdate} and not {@code findById}. */
    @Test
    void claimingATableGoesThroughTheLockToo() {
        GameTable table = tableInReview("table-1", "2026-09-01T09:00");
        when(userService.getById(ACTOR)).thenReturn(user(ACTOR, "damian"));
        when(gameTableRepository.findByIdForUpdate("table-1")).thenReturn(Optional.of(table));
        when(masterService.findPrimariesByTables(List.of("table-1"))).thenReturn(Map.of());

        service().claim("game_table", "table-1", ACTOR);

        assertThat(table.getClaimedBy().getId()).isEqualTo(ACTOR);
        verify(gameTableRepository).findByIdForUpdate("table-1");
    }

    /** An item nobody is waiting on is not claimed: it is in nobody's tray. */
    @Test
    void anAlreadyResolvedRequestCannotBeClaimed() {
        ApprovalRequest request = pendingRequest("req-1", "2026-09-01T09:00");
        request.resolve(ApprovalStatus.Approved, user("admin-2", "otra"), "listo");
        when(userService.getById(ACTOR)).thenReturn(user(ACTOR, "damian"));
        when(approvalRequestRepository.lockById("req-1")).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service().claim("approval_request", "req-1", ACTOR))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void aTableNoLongerUnderReviewCannotBeClaimed() {
        GameTable table = tableInReview("table-1", "2026-09-01T09:00");
        table.setStatus(GameTableStatus.Opened);
        when(userService.getById(ACTOR)).thenReturn(user(ACTOR, "damian"));
        when(gameTableRepository.findByIdForUpdate("table-1")).thenReturn(Optional.of(table));

        assertThatThrownBy(() -> service().claim("game_table", "table-1", ACTOR))
                .isInstanceOf(ConflictException.class);
    }

    /** A deleted table does not exist here either (#25, #175): 404 and not 403 - «fue borrada» is not told. */
    @Test
    void aDeletedTableDoesNotExistForTheTray() {
        GameTable table = tableInReview("table-1", "2026-09-01T09:00");
        table.setStatus(GameTableStatus.Deleted);
        when(userService.getById(ACTOR)).thenReturn(user(ACTOR, "damian"));
        when(gameTableRepository.findByIdForUpdate("table-1")).thenReturn(Optional.of(table));

        assertThatThrownBy(() -> service().claim("game_table", "table-1", ACTOR))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void claimingAnUnknownKindIs404() {
        assertThatThrownBy(() -> service().claim("comment", "c-1", ACTOR)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void claimingSomethingThatIsNotThereIs404() {
        when(userService.getById(ACTOR)).thenReturn(user(ACTOR, "damian"));
        when(approvalRequestRepository.lockById("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().claim("approval_request", "nope", ACTOR))
                .isInstanceOf(NotFoundException.class);
    }

    // ------------------------------------------------------------------ devolver

    @Test
    void releasingYourOwnLeavesItFree() {
        ApprovalRequest request = pendingRequest("req-1", "2026-09-01T09:00");
        request.claim(user(ACTOR, "damian"), LocalDateTime.parse("2026-09-01T10:00"));
        when(approvalRequestRepository.lockById("req-1")).thenReturn(Optional.of(request));

        service().release("approval_request", "req-1", ACTOR);

        assertThat(request.getClaimedBy()).isNull();
        assertThat(request.getClaimedAt()).isNull();
    }

    /** Releasing something nobody holds is idempotent: the state that was asked for already holds. */
    @Test
    void releasingSomethingUnclaimedIsNotAnError() {
        ApprovalRequest request = pendingRequest("req-1", "2026-09-01T09:00");
        when(approvalRequestRepository.lockById("req-1")).thenReturn(Optional.of(request));

        service().release("approval_request", "req-1", ACTOR);

        assertThat(request.getClaimedBy()).isNull();
    }

    /** Releasing somebody else's is not: it is the one way an admin would take work off another's desk. */
    @Test
    void releasingSomebodyElsesIs409() {
        ApprovalRequest request = pendingRequest("req-1", "2026-09-01T09:00");
        User otra = user("admin-2", "otra");
        request.claim(otra, LocalDateTime.parse("2026-09-01T10:00"));
        when(approvalRequestRepository.lockById("req-1")).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service().release("approval_request", "req-1", ACTOR))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo(ConflictException.ITEM_ALREADY_CLAIMED);

        assertThat(request.getClaimedBy()).isEqualTo(otra);
    }

    /**
     * Releasing does not ask what state the item is in, unlike claiming: releasing is the undo, and an
     * undo that can refuse leaves somebody stuck.
     */
    @Test
    void releasingDoesNotAskAboutTheItemsState() {
        GameTable table = tableInReview("table-1", "2026-09-01T09:00");
        table.claim(user(ACTOR, "damian"), LocalDateTime.parse("2026-09-01T10:00"));
        table.setStatus(GameTableStatus.Opened);
        when(gameTableRepository.findByIdForUpdate("table-1")).thenReturn(Optional.of(table));

        service().release("game_table", "table-1", ACTOR);

        assertThat(table.getClaimedBy()).isNull();
    }

    // ------------------------------------------------------------------ armado

    private void noQueue() {
        queue(List.of(), List.of());
    }

    /**
     * Runs something with the service's own logger tapped, and hands back the WARN lines it wrote.
     *
     * <p>The only assertion in this file that looks at a log rather than at a return value, and it is
     * the right shape for this one rule: the ceiling's whole job is to be <em>noticed</em>, so what has
     * to be true is that a line comes out naming the source. Anything else - a counter, a flag - would
     * be testing a mechanism invented for the test.
     */
    private static List<String> capturingWarnings(Runnable call) {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(AdminQueueService.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            call.run();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
        return appender.list.stream()
                .filter(event -> event.getLevel() == ch.qos.logback.classic.Level.WARN)
                .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                .toList();
    }

    /**
     * <b>{@code PlayerBan} does not enter the tray</b> (#39 over #90), and that had to be <em>built</em>:
     * the query never asked what type a row was, so new types joined for free. {@code TablePause} does
     * enter - an admin resolves it - and a veto does not, because the table's {@code Primary} resolves
     * it. A tray full of work the reader gets a 403 for taking is worse than one that under-reports.
     */
    @Test
    void vetoRequestsAreNotInTheTrayAndPauseRequestsAre() {
        noQueue();

        service().list(ACTOR, FIRST_PAGE);

        ArgumentCaptor<java.util.Collection<ApprovalRequestType>> excluded = ArgumentCaptor.captor();
        verify(approvalRequestRepository).findQueueItems(any(), excluded.capture(), anyString(), any());

        assertThat(excluded.getValue()).containsExactly(ApprovalRequestType.PlayerBan);
        assertThat(excluded.getValue()).doesNotContain(ApprovalRequestType.TablePause);
    }

    private void queue(List<ApprovalRequest> requests, List<GameTable> tables) {
        when(approvalRequestRepository.findQueueItems(any(), any(), anyString(), any())).thenReturn(requests);
        when(gameTableRepository.findQueueItems(any(), anyString(), any())).thenReturn(tables);
        when(masterService.findPrimariesByTables(any())).thenReturn(Map.of());
    }

    private static ApprovalRequest pendingRequest(String id, String createdAt) {
        ApprovalRequest request = new ApprovalRequest(
                ApprovalRequestType.MasterGrant, "user", "user-1", user("user-1", "carla"), "quiero dirigir");
        ReflectionTestUtils.setField(request, "id", id);
        ReflectionTestUtils.setField(request, "createdAt", LocalDateTime.parse(createdAt));
        return request;
    }

    private static GameTable tableInReview(String id, String createdAt) {
        GameTable table = new GameTable("La Cripta", user("creator-1", "quien-la-creo"));
        ReflectionTestUtils.setField(table, "id", id);
        ReflectionTestUtils.setField(table, "createdAt", LocalDateTime.parse(createdAt));
        table.setStatus(GameTableStatus.Preparation);
        return table;
    }

    private static User user(String id, String discordUsername) {
        User user = new User("discord-" + id, discordUsername);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
