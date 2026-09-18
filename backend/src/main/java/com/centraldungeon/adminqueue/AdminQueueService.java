package com.centraldungeon.adminqueue;

import com.centraldungeon.adminqueue.dto.AdminQueueItemResponse;
import com.centraldungeon.approvals.ApprovalRequest;
import com.centraldungeon.approvals.ApprovalRequestRepository;
import com.centraldungeon.approvals.ApprovalStatus;
import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.tables.GameTable;
import com.centraldungeon.tables.GameTableRepository;
import com.centraldungeon.tables.GameTableStatus;
import com.centraldungeon.tables.Master;
import com.centraldungeon.tables.MasterService;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The shared tray of the admins: everything waiting for an admin, wherever it lives (#100).
 *
 * <p><b>There is no {@code admin_queue} table and there is not going to be</b> (#11). The tray is a
 * view over work that already exists in its own table, so nothing here is ever the source of truth
 * for what is pending - a pending request <em>is</em> a row in {@code approval_requests}, a table in
 * review <em>is</em> a row in {@code game_tables}. A table of its own would be a second place to keep
 * in step with the first, and the two would drift.
 *
 * <p><b>N queries and a merge in Java, not a native {@code UNION ALL}.</b> #100 asked for «un
 * {@code UNION ALL} de cuatro consultas pequeñas, normalizadas a un DTO común en el service», and
 * this is that, minus the native SQL. Three reasons, in order of weight:
 *
 * <ul>
 *   <li>There is not one {@code nativeQuery} in the whole application. A union over tables with
 *       different columns would be the first, and it would have to be rewritten by hand in F5 when
 *       {@code comments} and {@code system_feedback} join.</li>
 *   <li>The project's own precedent for exactly this problem is {@code MasterDashboardService}: four
 *       private reads concatenated into one list and sorted in Java (#136).</li>
 *   <li>What the tray unions is <em>pending work</em>, which is bounded by what it means. A tray with
 *       thousands of items is a staffing problem, not a paging one.</li>
 * </ul>
 *
 * <p><b>Bounded anyway.</b> Each source contributes at most {@link #QUEUE_SOURCE_CAP} rows and says
 * so in a {@code WARN} when it touches the ceiling. A merge with no ceiling is a memory load nobody
 * declared, and "it cannot get big" is the sentence every unbounded read was written under.
 *
 * <p><b>Adding the third source is adding one method.</b> {@link #pendingApprovalRequests} and
 * {@link #tablesWaitingReview} each answer with a {@code List<AdminQueueItemResponse>} and
 * {@link #list} concatenates them; F5 writes {@code commentsUnderReview} and adds one line. That is
 * said here so it does not have to be discovered later.
 */
@Service
public class AdminQueueService {

    private static final Logger log = LoggerFactory.getLogger(AdminQueueService.class);

    /**
     * How many rows one source may contribute to a single merge.
     *
     * <p>The same bound, and the same reasoning, as {@code FileRetentionService.BATCH_SIZE} and
     * {@code ApprovalOrphanCheckService.BATCH_SIZE}. If a source ever reaches it the tray under-reports
     * - which is why hitting it is logged with the source's name rather than absorbed quietly.
     */
    static final int QUEUE_SOURCE_CAP = 200;

    /** The ceiling, as the repositories want it. Page zero of size {@link #QUEUE_SOURCE_CAP}. */
    private static final Pageable SOURCE_CEILING = PageRequest.of(0, QUEUE_SOURCE_CAP);

    /** The requests nobody has answered - one of the two live sources. */
    private final ApprovalRequestRepository approvalRequestRepository;

    /** The tables sitting in review - the other one. */
    private final GameTableRepository gameTableRepository;

    /** Resolves the Primary of a whole page of tables in one query, instead of one per row. */
    private final MasterService masterService;

    /** Loads the admin doing the reserving, from the token's id and never from a parameter (#121). */
    private final UserService userService;

    /**
     * @param approvalRequestRepository the {@code approval_requests} source, and the row a claim locks
     * @param gameTableRepository       the {@code game_tables} source, and the row a claim locks
     * @param masterService             who runs each table of the page, batched
     * @param userService               loads the admin taking an item
     */
    public AdminQueueService(
            ApprovalRequestRepository approvalRequestRepository,
            GameTableRepository gameTableRepository,
            MasterService masterService,
            UserService userService) {
        this.approvalRequestRepository = approvalRequestRepository;
        this.gameTableRepository = gameTableRepository;
        this.masterService = masterService;
        this.userService = userService;
    }

    /**
     * The tray, for one admin.
     *
     * <p><b>The one who has been waiting longest goes first</b>, and ties break by id so page two is
     * stable (#136, #171). The order is the tray's rule and not the caller's choice: a work tray whose
     * order can be changed from the URL is a listing, and the thing that makes this a tray is that it
     * always answers "what should I do next" the same way.
     *
     * <p>The total is exact and costs nothing: the merge is already in memory, so it is sorted,
     * counted and cut (#173).
     *
     * <p><b>No {@code ?q=}.</b> A tray sorts itself by age and empties; a search box over it would be
     * solving the wrong problem. {@code /admin/tables} is the screen that searches (#176).
     *
     * @param actorId  the admin reading it, always from the token (#121). It decides what they see:
     *                 an item another admin reserved is not in their tray at all
     * @param pageable which page and how big. Its sort is deliberately ignored - see above
     * @return one page of the tray. Empty is a success and the screen says so (#136)
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminQueueItemResponse> list(String actorId, Pageable pageable) {
        List<AdminQueueItemResponse> items = new ArrayList<>();
        items.addAll(pendingApprovalRequests(actorId));
        items.addAll(tablesWaitingReview(actorId));
        // F5: items.addAll(commentsUnderReview(actorId)); items.addAll(newSystemFeedback(actorId));

        items.sort(Comparator.comparing(AdminQueueItemResponse::waitingSince)
                .thenComparing(AdminQueueItemResponse::id));
        return pageOf(items, pageable);
    }

    /**
     * Reserves an item for an admin (#100).
     *
     * <p><b>Idempotent for the same admin, and it must be</b>: a double click, a retry after a flaky
     * connection, or the tray refetching and the admin clicking again all end up here, and every one
     * of them should leave the reservation exactly as it was. In particular {@code claimed_at} is
     * <em>not</em> refreshed - otherwise an admin clicking every fourteen minutes would hold an item
     * for ever and the release job would never reach it.
     *
     * <p>Under the row's own lock, and the locking read is the first thing this transaction does with
     * the row. This is a check-then-act - read who holds it, then write - and it is the third one in
     * this phase to turn out to be a real race rather than a theoretical one (#252, #256): two admins
     * opening the same tray and clicking the same item at the same instant is the ordinary case, not
     * the exotic one.
     *
     * @param type    which table the item lives in: {@code approval_request} or {@code game_table}
     * @param id      the row's id in that table
     * @param actorId the admin, from the token (#121)
     * @return the item as it now stands, so the row on screen re-renders from the answer
     * @throws NotFoundException 404 when the type names nothing, or no row has that id
     * @throws ConflictException 409 {@code ITEM_ALREADY_CLAIMED} when another admin holds it, or a
     *                           plain {@code CONFLICT} when the item is no longer waiting on anybody
     */
    @Transactional
    public AdminQueueItemResponse claim(String type, String id, String actorId) {
        AdminQueueSource source = AdminQueueSource.require(type);
        User admin = userService.getById(actorId);
        return switch (source) {
            case APPROVAL_REQUEST -> claimApprovalRequest(id, admin);
            case GAME_TABLE -> claimGameTable(id, admin);
        };
    }

    /**
     * Gives an item back (#100).
     *
     * <p><b>Idempotent in the other direction</b>: releasing something nobody holds is a 204, because
     * the state the caller asked for is already true. Releasing something another admin holds is not -
     * that is {@code ITEM_ALREADY_CLAIMED}, and it is the only way one admin could take work away from
     * another mid-review.
     *
     * <p>Unlike {@link #claim}, this does not care what state the item is in. Giving back is the undo,
     * and an undo that can itself be refused leaves people stuck.
     *
     * @param type    which table the item lives in
     * @param id      the row's id in that table
     * @param actorId the admin, from the token (#121)
     * @throws NotFoundException 404 when the type names nothing, or no row has that id
     * @throws ConflictException 409 {@code ITEM_ALREADY_CLAIMED} when another admin holds it
     */
    @Transactional
    public void release(String type, String id, String actorId) {
        AdminQueueSource source = AdminQueueSource.require(type);
        switch (source) {
            case APPROVAL_REQUEST -> {
                ApprovalRequest request = lockApprovalRequest(id);
                if (releasable(request.getClaimedBy(), actorId, "request " + id)) {
                    request.releaseClaim();
                }
            }
            case GAME_TABLE -> {
                GameTable table = lockGameTable(id);
                if (releasable(table.getClaimedBy(), actorId, "table " + id)) {
                    table.releaseClaim();
                }
            }
        }
    }

    // ------------------------------------------------------------------ the sources

    /**
     * {@code approval_requests} in {@code Pending} - the requests nobody has answered.
     *
     * @param actorId the admin reading the tray
     * @return their lines of the tray, oldest first
     */
    private List<AdminQueueItemResponse> pendingApprovalRequests(String actorId) {
        List<ApprovalRequest> rows =
                approvalRequestRepository.findQueueItems(ApprovalStatus.Pending, actorId, SOURCE_CEILING);
        warnIfCapped(AdminQueueSource.APPROVAL_REQUEST, rows.size());
        return rows.stream().map(AdminQueueService::toItem).toList();
    }

    /**
     * {@code game_tables} in {@code Preparation}, <b>and nothing else</b> (modelo-datos.md §5, #245).
     *
     * <p>This is the part of the tray that a loose reading of #176 gets wrong. {@code Draft} does not
     * enter: nobody sent it, and putting half-written drafts in front of a reviewer is exactly what
     * {@code submitForReview} exists to prevent. {@code ChangesRequested} does not enter either: an
     * admin already answered it and the ball is with the master. And {@code Unassigned} does not
     * enter, which is the subtle one - it <em>is</em> waiting on an admin, but for a master to be
     * assigned (#72), which is a different action on a different screen, not a review.
     *
     * <p>Who is waiting is the table's Primary - the master who sent it - resolved in one batched
     * query for the whole source rather than one per row. A table with no live Primary falls back to
     * whoever created the row: the answer is still true and the tray never prints an id (#136).
     *
     * @param actorId the admin reading the tray
     * @return their lines of the tray, oldest first
     */
    private List<AdminQueueItemResponse> tablesWaitingReview(String actorId) {
        List<GameTable> rows =
                gameTableRepository.findQueueItems(GameTableStatus.Preparation, actorId, SOURCE_CEILING);
        warnIfCapped(AdminQueueSource.GAME_TABLE, rows.size());
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<String, Master> primaries =
                masterService.findPrimariesByTables(rows.stream().map(GameTable::getId).toList());
        return rows.stream().map(table -> toItem(table, primaries.get(table.getId()))).toList();
    }

    /**
     * Says so when a source under-reports, with the name of the source.
     *
     * @param source  which source hit the ceiling
     * @param fetched how many rows it brought back
     */
    private void warnIfCapped(AdminQueueSource source, int fetched) {
        if (fetched >= QUEUE_SOURCE_CAP) {
            log.warn(
                    "Admin queue source '{}' returned its ceiling of {} items - the tray is under-reporting, "
                            + "and either the source needs paging of its own or nobody is working the queue",
                    source.wireName(),
                    QUEUE_SOURCE_CAP);
        }
    }

    // ------------------------------------------------------------------ one item

    /**
     * One pending request as a line of the tray.
     *
     * @param request the request
     * @return the line
     */
    private static AdminQueueItemResponse toItem(ApprovalRequest request) {
        return new AdminQueueItemResponse(
                AdminQueueSource.APPROVAL_REQUEST.wireName(),
                request.getId(),
                AdminQueueItemKind.ApprovalRequest.wireName(),
                // The kind of request is the title: it is what the reader needs to know before opening
                // it, the way a table's name is.
                request.getRequestType().wireName(),
                displayNameOf(request.getRequestedBy()),
                request.getJustification(),
                request.getCreatedAt(),
                displayNameOrNull(request.getClaimedBy()),
                request.getClaimedAt());
    }

    /**
     * One table in review as a line of the tray.
     *
     * @param table   the table
     * @param primary its live Primary, or null when it has none
     * @return the line
     */
    private static AdminQueueItemResponse toItem(GameTable table, @Nullable Master primary) {
        User waiting = primary != null ? primary.getUser() : table.getCreatedBy();
        return new AdminQueueItemResponse(
                AdminQueueSource.GAME_TABLE.wireName(),
                table.getId(),
                AdminQueueItemKind.TableWaitingReview.wireName(),
                table.getName(),
                displayNameOf(waiting),
                // No detail, and not an omission: a table's justification is the table, and it is read
                // by opening it. A truncated description here would be a worse version of that.
                null,
                table.getCreatedAt(),
                displayNameOrNull(table.getClaimedBy()),
                table.getClaimedAt());
    }

    // ------------------------------------------------------------------ claiming

    private AdminQueueItemResponse claimApprovalRequest(String id, User admin) {
        ApprovalRequest request = lockApprovalRequest(id);
        if (request.getStatus() != ApprovalStatus.Pending) {
            throw new ConflictException(
                    "Request " + id + " is " + request.getStatus() + " and is no longer in the admin queue");
        }
        if (claimable(request.getClaimedBy(), admin.getId(), "request " + id)) {
            request.claim(admin, LocalDateTime.now());
        }
        return toItem(request);
    }

    private AdminQueueItemResponse claimGameTable(String id, User admin) {
        GameTable table = lockGameTable(id);
        if (table.getStatus() != GameTableStatus.Preparation) {
            throw new ConflictException(
                    "Table " + id + " is " + table.getStatus() + " and is no longer in the admin queue");
        }
        if (claimable(table.getClaimedBy(), admin.getId(), "table " + id)) {
            table.claim(admin, LocalDateTime.now());
        }
        return toItem(table, masterService.findPrimariesByTables(List.of(id)).get(id));
    }

    /**
     * Decides whether a reservation has to be written, and refuses the one case that must not be.
     *
     * @param claimedBy who holds it now, or null when nobody does
     * @param actorId   the admin asking for it
     * @param what      how to name the item in the log line
     * @return true when the reservation has to be taken; false when this admin already holds it, in
     *         which case nothing is written and {@code claimed_at} keeps its original value
     * @throws ConflictException 409 {@code ITEM_ALREADY_CLAIMED} when somebody else holds it
     */
    private static boolean claimable(@Nullable User claimedBy, String actorId, String what) {
        if (claimedBy == null) {
            return true;
        }
        if (claimedBy.getId().equals(actorId)) {
            return false;
        }
        throw alreadyClaimed(claimedBy, what);
    }

    /**
     * The same three-way answer for giving an item back.
     *
     * @param claimedBy who holds it now, or null when nobody does
     * @param actorId   the admin giving it back
     * @param what      how to name the item in the log line
     * @return true when there is a reservation of the actor's to clear; false when there is none,
     *         which is already the state they asked for
     * @throws ConflictException 409 {@code ITEM_ALREADY_CLAIMED} when somebody else holds it - one
     *                           admin does not take work off another's desk
     */
    private static boolean releasable(@Nullable User claimedBy, String actorId, String what) {
        if (claimedBy == null) {
            return false;
        }
        if (claimedBy.getId().equals(actorId)) {
            return true;
        }
        throw alreadyClaimed(claimedBy, what);
    }

    private static ConflictException alreadyClaimed(User claimedBy, String what) {
        return new ConflictException(
                "The " + what + " is already claimed by " + claimedBy.getId(),
                ConflictException.ITEM_ALREADY_CLAIMED);
    }

    // ------------------------------------------------------------------ plumbing

    /**
     * The locking read, and it is also the read of who holds the item (#252).
     *
     * <p>A plain {@code findById} beforehand would put a stale instance in the persistence context
     * and the lock would refresh nothing: under MySQL's REPEATABLE READ the transaction that waited
     * wakes up and answers from the snapshot it opened <em>before</em> it began waiting - cheerfully
     * reading "free" on a row the transaction it just waited for has already claimed.
     */
    private ApprovalRequest lockApprovalRequest(String id) {
        return approvalRequestRepository
                .lockById(id)
                .orElseThrow(() -> new NotFoundException("Approval request not found: " + id));
    }

    /** Same, for a table - and a soft-deleted one does not exist here either (#25, #175). */
    private GameTable lockGameTable(String id) {
        GameTable table = gameTableRepository
                .findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Table not found: " + id));
        if (table.getStatus() == GameTableStatus.Deleted) {
            throw new NotFoundException("Table not found: " + id);
        }
        return table;
    }

    /**
     * Cuts the merged list into the page that was asked for, with a real total.
     *
     * @param items    every item of the tray, already ordered
     * @param pageable which page and how big
     * @return the page, with {@code totalElements} counting the whole tray and not the slice
     */
    private static PageResponse<AdminQueueItemResponse> pageOf(
            List<AdminQueueItemResponse> items, Pageable pageable) {
        int from = (int) Math.min(pageable.getOffset(), items.size());
        int to = Math.min(from + pageable.getPageSize(), items.size());
        return PageResponse.from(new PageImpl<>(items.subList(from, to), pageable, items.size()));
    }

    /** The tray shows a person, not an id - the same fallback every other mapper uses. */
    private static String displayNameOf(User user) {
        return user.getName() != null ? user.getName() : user.getDiscordUsername();
    }

    /** Null stays null: an unreserved item has nobody to name. */
    private static @Nullable String displayNameOrNull(@Nullable User user) {
        return user == null ? null : displayNameOf(user);
    }
}
