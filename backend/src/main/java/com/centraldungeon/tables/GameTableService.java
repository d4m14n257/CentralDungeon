package com.centraldungeon.tables;

import com.centraldungeon.adminqueue.AdminQueueClaimRule;
import com.centraldungeon.catalogs.CatalogType;
import com.centraldungeon.catalogs.TableCatalogService;
import com.centraldungeon.catalogs.dto.CatalogValueResponse;
import com.centraldungeon.notifications.NotificationService;
import com.centraldungeon.notifications.NotificationType;
import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.exception.ForbiddenActionException;
import com.centraldungeon.common.exception.InvalidRequestException;
import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.common.search.SearchQuery;
import com.centraldungeon.common.search.SearchQueryParser;
import com.centraldungeon.common.search.SearchTerm;
import com.centraldungeon.common.text.RichTextSanitizer;
import com.centraldungeon.files.TableFileService;
import com.centraldungeon.registrations.TablePlayerCount;
import com.centraldungeon.registrations.TableRegistration;
import com.centraldungeon.registrations.TableRegistrationRepository;
import com.centraldungeon.registrations.TableRegistrationStatus;
import com.centraldungeon.tables.dto.AddMasterRequest;
import com.centraldungeon.tables.dto.AdminTableSummaryResponse;
import com.centraldungeon.tables.dto.AssignMastersRequest;
import com.centraldungeon.tables.dto.AttendanceSummaryResponse;
import com.centraldungeon.tables.dto.ChangeTableStatusRequest;
import com.centraldungeon.tables.dto.CreateGameTableRequest;
import com.centraldungeon.tables.dto.GameTableDetailResponse;
import com.centraldungeon.tables.dto.GameTableHistoryResponse;
import com.centraldungeon.tables.dto.GameTableSummaryResponse;
import com.centraldungeon.tables.dto.MasterSummaryResponse;
import com.centraldungeon.tables.dto.TableScheduleEntry;
import com.centraldungeon.tables.dto.TableStatusChangeResponse;
import com.centraldungeon.tables.dto.UpdateGameTableRequest;
import com.centraldungeon.users.PlatformRole;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserService;
import com.centraldungeon.users.dto.UserSummaryResponse;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The table's whole lifecycle, and the only place its nine states move (decisiones.md, the table's
 * life cycle). Every transition below follows the same three steps: lock the table, refuse if it is
 * not in the one status the transition starts from, and record the change with its author.
 *
 * <p>Two things it never leaves to the caller. <b>Pertenencia</b>: who may act on a concrete table
 * is a row in {@code masters}, not a role, so it is checked here rather than in a
 * {@code @PreAuthorize} that cannot see the resource (#17, #121, #135). And <b>locking</b>: the
 * invariants MySQL cannot express as constraints - one live Primary (#73), one active registration
 * per pair (#28), the player cap (#34) - are held by taking a pessimistic lock on the table row.
 */
@Service
public class GameTableService {

    private static final List<GameTableStatus> VISIBLE_STATUSES = List.of(GameTableStatus.Opened, GameTableStatus.InProgress);

    /**
     * /my/tables: the statuses that still count as "vivo" (#133a). {@code Pause} belongs here and
     * not with {@link #HISTORY_STATUSES} - it is a table frozen, not one that ended (#32); the
     * player who plays there still needs to find it among "mine".
     */
    private static final List<GameTableStatus> LIVE_MINE_STATUSES = List.of(
            GameTableStatus.Opened, GameTableStatus.InProgress,
            // PauseRequested joined the list in F3.4, when it stopped being a state nothing could
            // produce. A table whose master has asked for a pause is still being played - nothing has
            // been decided yet - so dropping it from "mine" would make a player's table vanish while
            // an admin thinks it over, and reappear either way.
            GameTableStatus.PauseRequested,
            GameTableStatus.Pause);

    /** /my/tables/history: the two ways a run is over, and nothing else (#133a). */
    private static final List<GameTableStatus> HISTORY_STATUSES = List.of(GameTableStatus.Finished, GameTableStatus.Canceled);

    /**
     * /admin/tables: every status a table can actually be seen in - which is all of them but
     * {@code Deleted} (#25, #176).
     *
     * <p>It replaces the three-status default that used to live here, whose own comment said «until
     * the queue screen exists». The queue screen exists (F3.3), and with it the review work moved to
     * {@code /admin/queue}: what is left of {@code /admin/tables} is the management listing, and a
     * management listing that hides two thirds of the platform is not one. Narrowing is still
     * available - {@code ?status=} and now {@code /table_status} inside {@code ?q=} - but it is the
     * caller's to ask for, not a filter hidden in the endpoint.
     */
    private static final List<GameTableStatus> ADMIN_LISTABLE_STATUSES =
            Arrays.stream(GameTableStatus.values()).filter(status -> status != GameTableStatus.Deleted).toList();

    /**
     * A table can be deleted only while it was never public (decisiones.md #175): nobody saw it, so
     * there is no history worth keeping. Everything past this point is cancelled instead, because a
     * table other people looked at, applied to or played is a record of something that happened.
     */
    private static final Set<GameTableStatus> DELETABLE_STATUSES = Set.of(
            GameTableStatus.Draft, GameTableStatus.Unassigned, GameTableStatus.Preparation,
            GameTableStatus.ChangesRequested);

    /**
     * The statuses in which a table is still its master's to rewrite (#245).
     *
     * <p><b>`Preparation` is not one of them.</b> A table that was sent to review is being looked at
     * by somebody else, and moving it while they read is how a reviewer ends up approving something
     * that no longer exists. The two ways back into editing are the two that mean "it is yours
     * again": it has not been sent yet, or it came back with changes requested.
     *
     * <p>An admin editing during review is a different door, and it is in {@link #update} itself:
     * this set is about the master.
     */
    private static final Set<GameTableStatus> EDITABLE_STATUSES =
            Set.of(GameTableStatus.Draft, GameTableStatus.ChangesRequested);

    private static final List<TableRegistrationStatus> ACTIVE_REGISTRATION_STATUSES =
            List.of(TableRegistrationStatus.Candidate, TableRegistrationStatus.Player);

    /** cancel() is the one transition with more than one valid "from" (docs/decisiones.md, the table's life cycle). */
    private static final Set<GameTableStatus> CANCELABLE_STATUSES = Set.of(
            GameTableStatus.Unassigned, GameTableStatus.Preparation, GameTableStatus.ChangesRequested,
            GameTableStatus.Opened, GameTableStatus.InProgress,
            // Added with F3.4: until the state had a producer nothing could sit in it, and a table
            // waiting on a pause it may never get is one its master has to be able to end.
            GameTableStatus.PauseRequested,
            GameTableStatus.Pause);

    private final GameTableRepository gameTableRepository;
    private final TableTypeRepository tableTypeRepository;
    private final TableRegistrationRepository tableRegistrationRepository;
    private final TableStatusChangeRepository tableStatusChangeRepository;
    private final MasterService masterService;
    private final GameTableMapper gameTableMapper;
    private final UserService userService;
    private final TableScheduleService tableScheduleService;
    private final ScheduleConflictService scheduleConflictService;
    private final TableCatalogService tableCatalogService;
    private final TableSessionService tableSessionService;
    private final TableFileService tableFileService;
    private final RichTextSanitizer richTextSanitizer;
    private final NotificationService notificationService;
    private final GameTableSearchResolver gameTableSearchResolver;
    private final TableVisibilityService tableVisibilityService;

    /**
     * @param gameTableRepository        the {@code game_tables} table, and the row everything locks on
     * @param tableTypeRepository        resolves the type a draft names
     * @param tableRegistrationRepository counts players and finds out whether anybody is involved yet
     * @param tableStatusChangeRepository the lifecycle's audit trail
     * @param masterService              answers pertenencia and keeps the one-Primary invariant
     * @param gameTableMapper            entity to DTO
     * @param userService                resolves the actor, and their roles for the admin checks
     * @param tableScheduleService       owns the weekly agenda and the clash check that guards it (#178)
     * @param scheduleConflictService    computes the warning the explorer's cards show (#178)
     * @param tableCatalogService        links the table to its systems, tags and platforms (#56)
     * @param tableSessionService        materializes the calendar when the table opens, and re-lays it
     *                                   when it comes back from a pause (#26, #33)
     * @param tableFileService           the files the table shares, which ride along with the detail
     *                                   for the same reason the calendar does (#29, #79)
     * @param richTextSanitizer          cleans the rich text on the way in and on the way out (#62)
     * @param notificationService        tells the masters how the review ended (#244)
     * @param gameTableSearchResolver    expands the explorer's catalog criteria into synonym groups
     *                                   before the query is built (#54, #56, #246)
     * @param tableVisibilityService     the one gate every read of a concrete table goes through -
     *                                   soft delete and veto, decided in one place (#25, #29)
     */
    public GameTableService(
            GameTableRepository gameTableRepository,
            TableTypeRepository tableTypeRepository,
            TableRegistrationRepository tableRegistrationRepository,
            TableStatusChangeRepository tableStatusChangeRepository,
            MasterService masterService,
            GameTableMapper gameTableMapper,
            UserService userService,
            TableScheduleService tableScheduleService,
            ScheduleConflictService scheduleConflictService,
            TableCatalogService tableCatalogService,
            TableSessionService tableSessionService,
            TableFileService tableFileService,
            RichTextSanitizer richTextSanitizer,
            NotificationService notificationService,
            GameTableSearchResolver gameTableSearchResolver,
            TableVisibilityService tableVisibilityService) {
        this.gameTableRepository = gameTableRepository;
        this.tableTypeRepository = tableTypeRepository;
        this.tableRegistrationRepository = tableRegistrationRepository;
        this.tableStatusChangeRepository = tableStatusChangeRepository;
        this.masterService = masterService;
        this.gameTableMapper = gameTableMapper;
        this.userService = userService;
        this.tableScheduleService = tableScheduleService;
        this.scheduleConflictService = scheduleConflictService;
        this.tableCatalogService = tableCatalogService;
        this.tableSessionService = tableSessionService;
        this.tableFileService = tableFileService;
        this.richTextSanitizer = richTextSanitizer;
        this.notificationService = notificationService;
        this.gameTableSearchResolver = gameTableSearchResolver;
        this.tableVisibilityService = tableVisibilityService;
    }

    /**
     * Tells every master of the table how its review ended (#244).
     *
     * <p>Every one of them and not only the Primary: a co-master runs the table too, and "it opened"
     * or "it came back" is news for whoever is going to run it, the same way {@code notifyNewCandidate}
     * reaches all of them.
     */
    private void announceReviewOutcome(GameTable gameTable, NotificationType outcome) {
        for (Master master : masterService.findByGameTable(gameTable.getId())) {
            notificationService.notifyReviewOutcome(master.getUser().getId(), gameTable, outcome);
        }
    }

    /** The creator becomes the table's Primary master (#73); a Master row is the source of pertenencia, not the role alone (#135). */
    @Transactional
    public GameTableDetailResponse create(CreateGameTableRequest request, String creatorId) {
        requireRunnableDraft(request.systemIds(), request.tagIds(), request.platformIds(), request.schedule());
        User creator = userService.getById(creatorId);
        GameTable gameTable = buildTable(request, creator);

        gameTable = gameTableRepository.save(gameTable);
        masterService.createPrimary(gameTable, creator);
        applyCatalogs(gameTable.getId(), request.systemIds(), request.tagIds(), request.platformIds());
        // After the Master row exists: R1 measures the agenda against what the actor is already
        // committed to, and creating the table is itself one of those commitments (#178).
        tableScheduleService.replace(gameTable, orEmpty(request.schedule()), creatorId);

        return toDetail(gameTable);
    }

    /**
     * The master editing their own table: the wizard's second pass, and how a table sent back with
     * {@code ChangesRequested} is corrected.
     *
     * <p>Pertenencia, not role (#17, #121, #135): running <em>this</em> table is what authorizes the
     * edit, and it is checked here because no {@code @PreAuthorize} can see the resource. Only the
     * statuses in which a table is still being written are editable - once it is open, people have
     * applied on the strength of what it says, and changing it out from under them is a different
     * flow.
     *
     * <p>The agenda goes last on purpose: {@code duration} is what gives a slot its length, so the
     * clash check has to run against the duration the table is about to have and not the one it had
     * (#178).
     *
     * @param gameTableId the table to edit
     * @param request     the whole table as it should end up. Absent means empty, not unchanged
     * @param actorId     the actor, from the token; has to run the table
     * @return the table after the edit
     * @throws ForbiddenActionException if the actor does not run the table
     * @throws ConflictException if the table is past the point where its own master may rewrite it,
     *         or if the new agenda clashes with something the actor is committed to (#178)
     */
    @Transactional
    public GameTableDetailResponse update(String gameTableId, UpdateGameTableRequest request, String actorId) {
        GameTable gameTable = lockTable(gameTableId);
        if (!masterService.isMasterOf(gameTableId, actorId)) {
            throw new ForbiddenActionException("Only a master of this table can edit it");
        }
        if (!EDITABLE_STATUSES.contains(gameTable.getStatus())) {
            throw new ConflictException("A table in status " + gameTable.getStatus() + " can no longer be edited by its master");
        }
        // Authorized first, then the draft itself: somebody who does not run this table gets a 403,
        // not a complaint about its agenda. Same three requirements as create (#226) - a rewrite that
        // empties the agenda or the catalogs would leave the table in a state creating it could never
        // have reached.
        requireRunnableDraft(request.systemIds(), request.tagIds(), request.platformIds(), request.schedule());

        gameTable.setName(request.name());
        gameTable.setDescription(richTextSanitizer.sanitize(request.description()));
        gameTable.setPermitted(richTextSanitizer.sanitize(request.permitted()));
        gameTable.setRequirements(richTextSanitizer.sanitize(request.requirements()));
        gameTable.setStartDate(request.startDate());
        gameTable.setTotalSessions(request.totalSessions());
        gameTable.setMaxPlayers(request.maxPlayers());
        gameTable.setTableType(resolveTableType(request.tableTypeId()));

        applyCatalogs(gameTableId, request.systemIds(), request.tagIds(), request.platformIds());
        tableScheduleService.replace(gameTable, orEmpty(request.schedule()), actorId);

        return toDetail(gameTable);
    }

    /**
     * A table an admin creates without running it (#72): it is born directly in Unassigned, with no
     * Primary. {@link #assignInitialMasters} is what moves it to Opened.
     *
     * @param request the draft
     * @param actorId the admin, from the token. Recorded as the author, not as a master
     * @return the created table, in Unassigned
     */
    @Transactional
    public GameTableDetailResponse createUnassigned(CreateGameTableRequest request, String actorId) {
        User creator = userService.getById(actorId);
        GameTable gameTable = buildTable(request, creator);
        gameTable.setStatus(GameTableStatus.Unassigned);

        gameTable = gameTableRepository.save(gameTable);
        applyCatalogs(gameTable.getId(), request.systemIds(), request.tagIds(), request.platformIds());
        // No owner yet, so there is nobody whose commitments the agenda could clash with. R1 is
        // checked when the table gets its masters, in assignInitialMasters (#178).
        tableScheduleService.replace(gameTable, orEmpty(request.schedule()), null);
        return toDetail(gameTable);
    }

    /** Assigning masters to an Unassigned table skips review entirely and opens it directly (#72). */
    @Transactional
    public GameTableDetailResponse assignInitialMasters(String gameTableId, AssignMastersRequest request, String actorId) {
        GameTable gameTable = lockTable(gameTableId);
        if (gameTable.getStatus() != GameTableStatus.Unassigned) {
            throw new ConflictException("Cannot assign masters to a table in status " + gameTable.getStatus());
        }
        List<String> secondaries = request.secondaryUserIds() != null ? request.secondaryUserIds() : List.of();

        // R1, deferred from creation: an Unassigned table had no master to clash with, and this is
        // the moment it gets one. Refusing here is better than opening a table its own master cannot
        // actually run (#178).
        CommittedTable clash = scheduleConflictService.findClash(
                request.primaryUserId(), gameTableId, scheduleConflictService.intervalsOf(gameTable));
        if (clash != null) {
            throw new ConflictException(
                    "Agenda overlaps table " + clash.name() + ", which the assigned master is already committed to",
                    ConflictException.SCHEDULE_CONFLICT,
                    Map.of(ConflictException.PARAM_OTHER_TABLE_NAME, clash.name()));
        }

        masterService.assignInitialMasters(gameTable, request.primaryUserId(), secondaries);
        // **They are told they now run it** (#244). This is the one assignment nobody asked for: an
        // admin created the table and handed it over, so without the bell the person finds out by
        // stumbling on a table they had never seen. Co-masters too - being added is news for whoever
        // is added.
        notificationService.notifyMasterAssigned(request.primaryUserId(), gameTable);
        for (String secondaryId : secondaries) {
            notificationService.notifyMasterAssigned(secondaryId, gameTable);
        }
        recordStatusChange(gameTable, GameTableStatus.Unassigned, GameTableStatus.Opened, actorId, null);
        tableSessionService.materialize(gameTable);
        return toDetail(gameTable);
    }

    /**
     * An admin approving a master's draft (#27) - the only way Preparation reaches Opened now.
     *
     * <p>Opening is what materializes the calendar (#26, #33): from here on the table has dates and
     * not only a weekly shape. A table missing a start date, an agenda or a session count opens with
     * no sessions rather than being refused (#196) - materializing is a consequence of opening, not a
     * precondition for it.
     *
     * <p><b>It refuses a table another admin has reserved</b> since F3.3 ({@link AdminQueueClaimRule},
     * #100). Not "reserved by the actor": an unreserved table is nobody's and approving it is an
     * implicit claim - the screen that offers this moved to {@code /admin/queue}, but the endpoint
     * stayed here because approving is an operation on the table aggregate and nowhere else (#176),
     * and it has to keep working for whoever reaches it without passing through the tray. What the
     * check buys is that a stale link or a second tab cannot take a review out from under the colleague
     * who took it.
     *
     * @param gameTableId the table to approve
     * @param actorId     the admin, from the token; recorded in the status history
     * @return the table, now Opened, with its sessions materialized
     * @throws ConflictException if the table was not awaiting review, or 409
     *                           {@code ITEM_ALREADY_CLAIMED} if another admin reserved it from the
     *                           shared queue
     */
    @Transactional
    public GameTableDetailResponse approve(String gameTableId, String actorId) {
        GameTable gameTable = lockTable(gameTableId);
        if (gameTable.getStatus() != GameTableStatus.Preparation) {
            throw new ConflictException("Cannot approve a table in status " + gameTable.getStatus());
        }
        // After the status check: when both are wrong, "this table is not in review" is the truer
        // sentence, and naming a colleague who reserved something that no longer needs reviewing
        // would send the reader to ask them about nothing.
        AdminQueueClaimRule.requireNotHeldByAnother(gameTable.getClaimedBy(), actorId, "table " + gameTableId);
        recordStatusChange(gameTable, GameTableStatus.Preparation, GameTableStatus.Opened, actorId, null);
        tableSessionService.materialize(gameTable);
        announceReviewOutcome(gameTable, NotificationType.TableApproved);
        return toDetail(gameTable);
    }

    /**
     * An admin sending a draft back to its master, with a reason. Preparation to ChangesRequested.
     *
     * <p>Same reservation check as {@link #approve} and for the same reason (#100): the two answers to
     * a review are the same act seen from two sides, and a rule that held for one of them only would
     * be a door left open.
     *
     * @param gameTableId the table
     * @param actorId     the admin, from the token
     * @param request     the justification, which the master reads on the status tab
     * @return the table after the change
     * @throws ConflictException if the table was not awaiting review, or 409
     *                           {@code ITEM_ALREADY_CLAIMED} if another admin reserved it from the
     *                           shared queue
     */
    @Transactional
    public GameTableDetailResponse requestChanges(String gameTableId, String actorId, ChangeTableStatusRequest request) {
        GameTable gameTable = lockTable(gameTableId);
        if (gameTable.getStatus() != GameTableStatus.Preparation) {
            throw new ConflictException("Cannot request changes on a table in status " + gameTable.getStatus());
        }
        AdminQueueClaimRule.requireNotHeldByAnother(gameTable.getClaimedBy(), actorId, "table " + gameTableId);
        recordStatusChange(gameTable, GameTableStatus.Preparation, GameTableStatus.ChangesRequested, actorId, request.justification());
        announceReviewOutcome(gameTable, NotificationType.TableChangesRequested);
        return toDetail(gameTable);
    }

    /**
     * The master sending their draft to review for the first time. Draft to Preparation (#245).
     *
     * <p><b>This is the act that makes the table exist for anybody else.</b> Up to here it was theirs
     * alone; from here an admin has it, which is also why the master stops being able to edit it —
     * {@link #EDITABLE_STATUSES}. Deliberate and not automatic on create: a table is written over
     * several sittings, and a wizard that filed it for review the moment it was saved would put
     * half-written drafts in front of a reviewer.
     *
     * @param gameTableId the table
     * @param actorId     the actor, from the token; has to be the table's Primary
     * @return the table after the change
     * @throws com.centraldungeon.common.exception.ForbiddenActionException if the actor is not its Primary
     * @throws ConflictException if the table was not a draft, or the draft is not runnable yet
     */
    @Transactional
    public GameTableDetailResponse submitForReview(String gameTableId, String actorId) {
        GameTable gameTable = lockTable(gameTableId);
        requirePrimaryOf(gameTableId, actorId, "submit for review");
        if (gameTable.getStatus() != GameTableStatus.Draft) {
            throw new ConflictException("Cannot send a table in status " + gameTable.getStatus() + " to review");
        }
        recordStatusChange(gameTable, GameTableStatus.Draft, GameTableStatus.Preparation, actorId, null);
        return toDetail(gameTable);
    }

    /**
     * The master sending a corrected draft back for review. ChangesRequested to Preparation.
     *
     * @param gameTableId the table
     * @param actorId     the actor, from the token; has to be the table's Primary
     * @return the table after the change
     * @throws com.centraldungeon.common.exception.ForbiddenActionException if the actor is not its
     *         Primary
     * @throws ConflictException if the table was not in ChangesRequested
     */
    @Transactional
    public GameTableDetailResponse resubmit(String gameTableId, String actorId) {
        GameTable gameTable = lockTable(gameTableId);
        requirePrimaryOf(gameTableId, actorId, "resubmit");
        if (gameTable.getStatus() != GameTableStatus.ChangesRequested) {
            throw new ConflictException("Cannot resubmit a table in status " + gameTable.getStatus());
        }
        recordStatusChange(gameTable, GameTableStatus.ChangesRequested, GameTableStatus.Preparation, actorId, null);
        return toDetail(gameTable);
    }

    /**
     * The master declaring play has begun. Opened to InProgress.
     *
     * @param gameTableId the table
     * @param actorId     the actor, from the token; has to be the table's Primary
     * @return the table after the change
     * @throws com.centraldungeon.common.exception.ForbiddenActionException if the actor is not its
     *         Primary
     * @throws ConflictException if the table was not Opened
     */
    @Transactional
    public GameTableDetailResponse start(String gameTableId, String actorId) {
        GameTable gameTable = lockTable(gameTableId);
        requirePrimaryOf(gameTableId, actorId, "start");
        if (gameTable.getStatus() != GameTableStatus.Opened) {
            throw new ConflictException("Cannot start a table in status " + gameTable.getStatus());
        }
        recordStatusChange(gameTable, GameTableStatus.Opened, GameTableStatus.InProgress, actorId, null);
        return toDetail(gameTable);
    }

    /**
     * The master closing a table that ran its course. InProgress to Finished.
     *
     * <p>This is where {@code closed_at} gets stamped (#180). It is not bookkeeping: it starts the
     * two-week window in which the people who shared the table can still see each other's profiles
     * (#44), and a phase that delivers the end of a table without recording when it ended has not
     * delivered it.
     *
     * @param gameTableId the table
     * @param actorId     the actor, from the token; has to be the table's Primary
     * @return the table after the change
     * @throws com.centraldungeon.common.exception.ForbiddenActionException if the actor is not its
     *         Primary
     * @throws ConflictException if the table was not InProgress
     */
    @Transactional
    public GameTableDetailResponse finish(String gameTableId, String actorId) {
        GameTable gameTable = lockTable(gameTableId);
        requirePrimaryOf(gameTableId, actorId, "finish");
        if (gameTable.getStatus() != GameTableStatus.InProgress) {
            throw new ConflictException("Cannot finish a table in status " + gameTable.getStatus());
        }
        sealClosedAt(gameTable);
        recordStatusChange(gameTable, GameTableStatus.InProgress, GameTableStatus.Finished, actorId, null);
        return toDetail(gameTable);
    }

    /** Either the table's own Primary or a platform admin may cancel it (#27) - the only transition either can trigger. */
    @Transactional
    public GameTableDetailResponse cancel(String gameTableId, String actorId, ChangeTableStatusRequest request) {
        GameTable gameTable = lockTable(gameTableId);
        if (!masterService.isPrimaryOf(gameTableId, actorId) && !isAdmin(actorId)) {
            throw new ForbiddenActionException("Only the Primary master or an admin can cancel this table");
        }
        GameTableStatus from = gameTable.getStatus();
        if (!CANCELABLE_STATUSES.contains(from)) {
            throw new ConflictException("Cannot cancel a table in status " + from);
        }
        sealClosedAt(gameTable);
        recordStatusChange(gameTable, from, GameTableStatus.Canceled, actorId, request.justification());
        return toDetail(gameTable);
    }

    /** Immediate pause by an admin (#32) - a master asking for one goes through approval_requests instead (F3). */
    /**
     * Soft delete of a table that never went public (#25, #175). Same actors as cancel - the Primary
     * or an admin - and the same lock, but a different meaning: cancel closes a table that existed
     * for other people, this one removes a draft that did not.
     *
     * <p>The cascade is explicit and in one transaction, as #25 requires: the master rows and the
     * registrations of the table fall with it, all stamped with the same instant. The status change
     * is recorded too - the trail survives even when the table does not.
     */
    @Transactional
    public void delete(String gameTableId, String actorId) {
        GameTable gameTable = lockTable(gameTableId);
        if (!masterService.isPrimaryOf(gameTableId, actorId) && !isAdmin(actorId)) {
            throw new ForbiddenActionException("Only the Primary master or an admin can delete this table");
        }
        GameTableStatus from = gameTable.getStatus();
        if (!DELETABLE_STATUSES.contains(from)) {
            throw new ConflictException("A table in status " + from + " cannot be deleted - cancel it instead");
        }
        if (tableRegistrationRepository.existsByGameTable_IdAndStatusIn(gameTableId, ACTIVE_REGISTRATION_STATUSES)) {
            throw new ConflictException("A table with candidates or players cannot be deleted - cancel it instead");
        }

        LocalDateTime deletedAt = LocalDateTime.now();
        for (TableRegistration registration : tableRegistrationRepository.findByGameTable_Id(gameTableId)) {
            registration.setStatus(TableRegistrationStatus.Deleted);
        }
        masterService.softDeleteAllOfTable(gameTableId, deletedAt);
        gameTable.setDeletedAt(deletedAt);
        recordStatusChange(gameTable, from, GameTableStatus.Deleted, actorId, null);
    }

    /**
     * An admin pausing a table directly. InProgress to Pause.
     *
     * <p>The other road to Pause - a master <em>asking</em> for one - needs
     * {@code approval_requests} and lands in F3. Freezing the agenda while paused (#32, #33) is
     * F1.3.
     *
     * @param gameTableId the table
     * @param actorId     the admin, from the token
     * @param request     the justification
     * @return the table after the change
     * @throws ConflictException if the table was not InProgress
     */
    @Transactional
    public GameTableDetailResponse pauseDirect(String gameTableId, String actorId, ChangeTableStatusRequest request) {
        GameTable gameTable = lockTable(gameTableId);
        if (gameTable.getStatus() != GameTableStatus.InProgress) {
            throw new ConflictException("Cannot pause a table in status " + gameTable.getStatus());
        }
        recordStatusChange(gameTable, GameTableStatus.InProgress, GameTableStatus.Pause, actorId, request.justification());
        return toDetail(gameTable);
    }

    /**
     * The master <em>asking</em> for a pause. InProgress to PauseRequested (#32).
     *
     * <p><b>This is the producer {@code PauseRequested} never had.</b> F1.7 relevó it as an orphan -
     * a state declared in the enum that no endpoint could reach - and it stayed that way until
     * {@code approval_requests} existed to carry the asking.
     *
     * <p>It only moves the table. The request itself is written by {@code ApprovalService}, in the
     * same transaction and as the caller of this method, because the mechanism has to stay single
     * (#42): a second place that created approval rows would be a second set of rules about them.
     *
     * <p><b>The table keeps its slot while it waits.</b> {@code ScheduleConflictService} counts
     * {@code PauseRequested} among the committing statuses on purpose - nothing has been granted
     * yet, and releasing the hours before the answer arrives would let a master take on something
     * else and then be refused the resume.
     *
     * @param gameTableId the table
     * @param actorId     the actor, from the token; has to run the table
     * @return the table, now PauseRequested
     * @throws ForbiddenActionException if the actor does not run the table
     * @throws ConflictException 409 {@code PAUSE_ALREADY_REQUESTED} when it is already waiting on
     *                           one, or a plain conflict from any other status
     */
    @Transactional
    public GameTableDetailResponse markPauseRequested(String gameTableId, String actorId) {
        GameTable gameTable = lockTable(gameTableId);
        // isMasterOf and not isPrimaryOf: asking is not deciding, and a co-master who cannot run
        // next week's session is exactly the person with a reason to ask (#121, #135).
        if (!masterService.isMasterOf(gameTableId, actorId)) {
            throw new ForbiddenActionException("Only a master of this table can ask for a pause");
        }
        if (gameTable.getStatus() == GameTableStatus.PauseRequested) {
            throw new ConflictException(
                    "Table " + gameTableId + " is already waiting on a pause",
                    ConflictException.PAUSE_ALREADY_REQUESTED);
        }
        if (gameTable.getStatus() != GameTableStatus.InProgress) {
            throw new ConflictException("Cannot ask to pause a table in status " + gameTable.getStatus());
        }
        recordStatusChange(gameTable, GameTableStatus.InProgress, GameTableStatus.PauseRequested, actorId, null);
        return toDetail(gameTable);
    }

    /**
     * An admin saying yes to a requested pause. PauseRequested to Pause (#32).
     *
     * <p>Called by {@code ApprovalService} when a {@code TablePause} request is approved. The
     * resolution note becomes the justification, which is not bookkeeping: #32 makes a reason
     * mandatory for {@code Pause}, and the reason an admin gave when they approved <em>is</em> that
     * reason. Inventing a second one would leave two answers to the same question.
     *
     * <p>Freezing the agenda needs nothing here. It is derived on read
     * ({@code TableSessionService.visibleSessionsOf}) and has to be, because the pause is reversible:
     * a pause that deleted the pending sessions could not put them back.
     *
     * @param gameTableId    the table
     * @param actorId        the admin, from the token
     * @param resolutionNote why they approved it - recorded as the pause's justification
     * @return the table, now Pause
     * @throws ConflictException if the table was not waiting on a pause
     */
    @Transactional
    public GameTableDetailResponse applyApprovedPause(String gameTableId, String actorId, String resolutionNote) {
        GameTable gameTable = lockTable(gameTableId);
        if (gameTable.getStatus() != GameTableStatus.PauseRequested) {
            throw new ConflictException("Cannot grant a pause to a table in status " + gameTable.getStatus());
        }
        recordStatusChange(gameTable, GameTableStatus.PauseRequested, GameTableStatus.Pause, actorId, resolutionNote);
        return toDetail(gameTable);
    }

    /**
     * An admin saying no to a requested pause. PauseRequested back to InProgress (#32).
     *
     * <p><b>A rejection with an effect, which is new.</b> Until F3.4 no type of request did anything
     * when it was rejected - that is what made {@code ApprovalService.reject} a method with no
     * {@code switch}. A pause is different because asking for one already moved the table: leaving it
     * in {@code PauseRequested} after being told no would strand it in a waiting room with no door
     * out, and the master would have to ask again to get a second refusal.
     *
     * <p>The refusal's note goes on the transition for the same reason the approval's does: the
     * master reads the status tab and «no» without a reason is the half of the mechanism worth having.
     *
     * @param gameTableId    the table
     * @param actorId        the admin, from the token
     * @param resolutionNote why they refused, recorded on the way back
     * @return the table, back in InProgress
     * @throws ConflictException if the table was not waiting on a pause
     */
    @Transactional
    public GameTableDetailResponse revertRequestedPause(String gameTableId, String actorId, String resolutionNote) {
        GameTable gameTable = lockTable(gameTableId);
        if (gameTable.getStatus() != GameTableStatus.PauseRequested) {
            throw new ConflictException("Cannot refuse a pause for a table in status " + gameTable.getStatus());
        }
        recordStatusChange(gameTable, GameTableStatus.PauseRequested, GameTableStatus.InProgress, actorId, resolutionNote);
        return toDetail(gameTable);
    }

    /**
     * Bringing a paused table back. Pause to InProgress.
     *
     * <p>Two things happen here that do not happen anywhere else in the lifecycle.
     *
     * <p><b>The clash is checked again</b> (#178, #193). A paused table does not reserve its slot, so
     * its master was free to take on something else while it was down; coming back is the moment
     * those slots are claimed again. If they are no longer free the resume is refused with a
     * {@code 409} that names the other table - the same answer R1 gives everywhere else, because a
     * table its own master cannot attend is not a table that has resumed. The way out is to move one
     * of the two agendas.
     *
     * <p><b>The pending sessions are re-laid</b> from this instant (#33). What was played and what
     * was called off keep their dates; the run's numbering does not move.
     *
     * @param gameTableId the table
     * @param actorId     the admin, from the token
     * @return the table, back in play, with its calendar re-laid
     * @throws ConflictException if the table was not paused, or if its agenda now clashes with
     *                           something its master is committed to (#193)
     */
    @Transactional
    public GameTableDetailResponse resume(String gameTableId, String actorId) {
        GameTable gameTable = lockTable(gameTableId);
        if (gameTable.getStatus() != GameTableStatus.Pause) {
            throw new ConflictException("Cannot resume a table in status " + gameTable.getStatus());
        }

        MasterSummaryResponse primary = findPrimaryMasterOrNull(gameTableId);
        if (primary != null) {
            CommittedTable clash = scheduleConflictService.findClashWith(primary.userId(), gameTable);
            if (clash != null) {
                throw new ConflictException(
                        "Cannot resume: agenda overlaps table " + clash.name()
                                + ", which this table's master is already committed to",
                        ConflictException.SCHEDULE_CONFLICT,
                        Map.of(ConflictException.PARAM_OTHER_TABLE_NAME, clash.name()));
            }
        }

        recordStatusChange(gameTable, GameTableStatus.Pause, GameTableStatus.InProgress, actorId, null);
        tableSessionService.rescheduleAfterPause(gameTable, LocalDateTime.now());
        return toDetail(gameTable);
    }

    /**
     * The table's lifecycle history, oldest first.
     *
     * <p>Masters of the table and admins only: the history carries the reasons behind a refusal or a
     * cancellation, which are written between them and not for the public.
     *
     * @param gameTableId the table
     * @param actorId     the actor, from the token
     * @return the whole history, oldest first
     * @throws com.centraldungeon.common.exception.ForbiddenActionException if the actor neither runs
     *         the table nor is an admin
     */
    @Transactional(readOnly = true)
    public List<TableStatusChangeResponse> getStatusHistory(String gameTableId, String actorId) {
        if (!masterService.isMasterOf(gameTableId, actorId) && !isAdmin(actorId)) {
            throw new ForbiddenActionException("Only a master of this table or an admin can view its status history");
        }
        return tableStatusChangeRepository.findByGameTable_IdOrderByCreatedAtAsc(gameTableId).stream()
                .map(gameTableMapper::toStatusChangeResponse)
                .toList();
    }

    /**
     * Adds a co-master or promotes one to Primary, and answers with the table's masters afterwards -
     * which is what the section on screen re-renders.
     *
     * @param gameTableId the table
     * @param actorId     the actor, from the token; {@code MasterService} checks their pertenencia
     * @param request     who to add or promote, and as what
     * @return every master of the table after the change
     */
    @Transactional
    public List<MasterSummaryResponse> addOrPromoteMaster(String gameTableId, String actorId, AddMasterRequest request) {
        GameTable gameTable = getEntityById(gameTableId);
        // Asked *before* the change, so it can tell being added apart from being promoted (#244): the
        // first is news - a table this person did not run until now - and the second is not, because
        // they were already running it and are watching the screen that did it.
        boolean joins = !masterService.isMasterOf(gameTableId, request.userId());
        masterService.addOrPromote(gameTable, actorId, request.userId(), request.masterType());
        if (joins) {
            notificationService.notifyMasterAssigned(request.userId(), gameTable);
        }
        return masterService.findByGameTable(gameTableId).stream().map(gameTableMapper::toMasterSummary).toList();
    }

    /**
     * Removes a co-master, and answers with the table's masters afterwards - the same shape
     * {@link #addOrPromoteMaster} returns, because the section on screen re-renders from it.
     *
     * @param gameTableId  the table
     * @param actorId      the actor, from the token; {@code MasterService} checks their pertenencia
     * @param targetUserId who to remove
     * @return every master of the table after the change
     */
    @Transactional
    public List<MasterSummaryResponse> removeMaster(String gameTableId, String actorId, String targetUserId) {
        GameTable gameTable = getEntityById(gameTableId);
        masterService.removeMaster(gameTable, actorId, targetUserId);
        return masterService.findByGameTable(gameTableId).stream().map(gameTableMapper::toMasterSummary).toList();
    }

    /**
     * The people search behind adding a co-master, answered only for a table's own Primary.
     *
     * <p>The scope is the whole point (#165): the admin directory stays closed, and what opens is a
     * search that only the person who may actually add a master can run. The check is membership and
     * not a role, because a Primary an admin assigned may never have been given {@code Master}
     * (#72, #135) - and the actor comes from the token, never from the URL (#121).
     *
     * @param gameTableId the table the search is for
     * @param actorId     the actor, from the token
     * @param query       the search box, or null for a first page
     * @param pageable    page, size and sort
     * @return one page of people who could be made a master of this table
     * @throws ForbiddenActionException when the actor is not this table's Primary
     */
    @Transactional(readOnly = true)
    public PageResponse<UserSummaryResponse> searchMasterCandidates(
            String gameTableId, String actorId, @Nullable String query, Pageable pageable) {
        getEntityById(gameTableId);
        if (!masterService.isPrimaryOf(gameTableId, actorId)) {
            throw new ForbiddenActionException("Only the table's master can search for co-masters");
        }
        return userService.search(query, pageable);
    }

    /**
     * The public explorer, narrowed by what the reader typed (#164, #246).
     *
     * <p>Excludes tables the actor masters (#154): a master cannot browse their own table to apply as
     * a Player at it. It is also the one listing that carries the clash warning of #178, computed for
     * the actor of the token: this is the screen where the question "can I actually take this on?" is
     * asked.
     *
     * <p><b>The catalog criteria are resolved before the query is built</b>, not inside it: a search
     * for {@code D&D} has to find the tables tagged {@code DANDD} too, and that expansion is a read of
     * its own (#54, #56). The search only ever narrows what the visibility rules already allowed -
     * see {@code GameTableSearchSpecification} for why the two are joined and never folded together.
     *
     * @param query    the raw search box, or null when it is empty
     * @param pageable page, size and sort
     * @param actorId  the actor, from the token - never from the URL (#121)
     * @return one page of the tables the actor could apply to
     */
    @Transactional(readOnly = true)
    public PageResponse<GameTableSummaryResponse> list(@Nullable String query, Pageable pageable, String actorId) {
        SearchQuery parsed = SearchQueryParser.parse(query, GameTableSearchField.wireNames());
        Map<SearchTerm, Set<String>> catalogIds = gameTableSearchResolver.resolveCatalogTerms(parsed);
        Page<GameTable> page = gameTableRepository.findAll(
                GameTableSearchSpecification.forExplorer(parsed, catalogIds, VISIBLE_STATUSES, actorId), pageable);
        return toSummaryPage(page, actorId);
    }

    /**
     * The public detail of a table, for /tables/:id.
     *
     * <p>It carries the clash flag of #178 computed for the actor: the apply button on that screen
     * has to be able to say <em>why</em> it is disabled, and R2 is the one reason the reader can do
     * something about (principio 2 de frontend-diseno.md 1).
     *
     * <p><b>The veto of #29 is applied here and nowhere below it.</b> The gate is
     * {@link TableVisibilityService#requireVisible}, which answers {@code 404} - never {@code 403},
     * because a 403 confirms exactly what the 404 denies. The public sessions and the shared files
     * ride along inside this answer precisely so that they inherit the one decision instead of
     * repeating it somewhere it could drift (see {@link #toDetail}).
     *
     * @param gameTableId the table
     * @param actorId     the actor, from the token - never from the URL (#121)
     * @return its detail
     * @throws com.centraldungeon.common.exception.NotFoundException if it does not exist, was
     *         deleted, or the actor is vetoed on it - the same 404 for all three (#25, #29, #175)
     */
    @Transactional(readOnly = true)
    public GameTableDetailResponse getDetail(String gameTableId, String actorId) {
        return toDetail(tableVisibilityService.requireVisible(gameTableId, actorId), actorId);
    }

    /**
     * /master/tables/:id - a different endpoint from getDetail on purpose (decisiones.md #152):
     * getDetail is deliberately public (any player reads it to decide whether to apply), so
     * reusing it here would mean the full table body travels over the network before the
     * frontend ever gets to decide whether to render it. This one checks pertenencia first and
     * never touches the mapper if the actor isn't a master of this table.
     */
    @Transactional(readOnly = true)
    public GameTableDetailResponse getManagedDetail(String gameTableId, String actorId) {
        GameTable gameTable = getEntityById(gameTableId);
        if (!masterService.isMasterOf(gameTableId, actorId)) {
            throw new ForbiddenActionException("Only a master of this table can view its management detail");
        }
        return toDetail(gameTable);
    }

    /**
     * /my/tables: only the tables where the actor currently holds an active Player registration,
     * <b>and only while the table itself is still live</b> (#133a).
     *
     * <p>The registration's own status never moves once somebody is accepted - it stays
     * {@code Player} whether the table is still running, was finished, or was cancelled. Filtering
     * by it alone, as this used to, left a table sitting among "mine" forever after it closed. What
     * decides "still mine to see here" is the table's own status; a closed run moves to
     * {@link #listMineHistory} instead. {@code Pause} counts as live - the table is frozen, not
     * over (#32).
     *
     * @param actorId  the actor, from the token (#121)
     * @param pageable page, size and sort
     * @return one page of the tables they play at that have not ended
     */
    @Transactional(readOnly = true)
    public PageResponse<GameTableSummaryResponse> listMine(String actorId, Pageable pageable) {
        Page<TableRegistration> page = tableRegistrationRepository.findByUser_IdAndStatusAndGameTable_StatusIn(
                actorId, TableRegistrationStatus.Player, LIVE_MINE_STATUSES, pageable);
        return toSummaryPage(page.map(TableRegistration::getGameTable), null);
    }

    /**
     * /my/tables/history: the tables the actor played at that are now over - {@code Finished} or
     * {@code Canceled} - with when each one closed and the actor's own final attendance (#133a).
     *
     * <p><b>A different shape from {@link #listMine}, not the same read with another filter</b>: a
     * closed table's card has no cupo to show and cannot clash with anything the actor is
     * committed to, and it carries two things a live card never needs - {@code closed_at} and the
     * attendance aggregate of #137. See {@link GameTableHistoryResponse} for the exact fields.
     *
     * <p>The attendance is resolved for the whole page in one grouped query
     * ({@link TableSessionService#summarizeByTables}), never one {@code summarize} call per row -
     * the same N+1 {@code CatalogUsageCount} and {@code FileService.usagesByFileId} already avoid.
     *
     * <p>Whether the actor left a comment on the table - the field #133 also names - is not here:
     * comments are F5, and there is nowhere yet to read that from.
     *
     * @param actorId  the actor, from the token (#121)
     * @param pageable page, size and sort
     * @return one page of the tables they played at that have ended
     */
    @Transactional(readOnly = true)
    public PageResponse<GameTableHistoryResponse> listMineHistory(String actorId, Pageable pageable) {
        Page<TableRegistration> page = tableRegistrationRepository.findByUser_IdAndStatusAndGameTable_StatusIn(
                actorId, TableRegistrationStatus.Player, HISTORY_STATUSES, pageable);
        List<String> tableIds = page.getContent().stream().map(registration -> registration.getGameTable().getId()).toList();
        Map<String, AttendanceSummaryResponse> attendanceByTable = tableSessionService.summarizeByTables(tableIds, actorId);

        return PageResponse.from(page.map(registration -> {
            GameTable gameTable = registration.getGameTable();
            AttendanceSummaryResponse attendance =
                    attendanceByTable.getOrDefault(gameTable.getId(), new AttendanceSummaryResponse(0, 0, 0, 0));
            return gameTableMapper.toHistory(gameTable, attendance);
        }));
    }

    /** /master/tables: every status, including Preparation - a master needs to see and open their own drafts. */
    @Transactional(readOnly = true)
    public PageResponse<GameTableSummaryResponse> listManaged(String actorId, Pageable pageable) {
        Page<GameTable> page = gameTableRepository.findByMasterUserId(actorId, pageable);
        return toSummaryPage(page, null);
    }

    /**
     * /admin/tables: the management listing, unfiltered by pertenencia - the caller is already an
     * admin (#176).
     *
     * <p><b>Every table, not only the ones waiting on a review.</b> That default moved out with F3.3:
     * reviewing is the shared tray's job now ({@code /admin/queue}), and what this screen is for is
     * finding a table - any table - and acting on it. {@code Deleted} is the one status that is never
     * listed even when it is asked for, because the filter belongs to the listing and not to the
     * caller (#25).
     *
     * <p><b>It takes the same {@code ?q=} the explorer does</b> (#164, arquitectura.md §2.5), with two
     * commands the explorer has no use for: {@code /table_status} over the closed list of states, and
     * {@code /table_master} over the masters' names. The catalog criteria are resolved to ids before
     * the query is built, exactly as {@link #list} does and for the same reason (#54, #56, #246).
     *
     * <p><b>The page is built with three queries and not with three per row</b> (F3.3 contrato §3.4):
     * one for the tables, one for their Primaries and one for their player counts. Asking per row was
     * survivable while the default was a handful of tables in review; listing every table there is
     * turned it into a query storm.
     *
     * @param rawQuery the search box, or null when it is empty. An unrecognized value matches nothing
     *                 and is never a 400
     * @param statuses which statuses to list, or null/empty for all of them but {@code Deleted}
     * @param pageable page, size and sort, with a tie-break by id (#171, #173)
     * @return one page of tables, as an admin sees them
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminTableSummaryResponse> listForAdmin(
            @Nullable String rawQuery, @Nullable List<GameTableStatus> statuses, Pageable pageable) {
        List<GameTableStatus> effective = (statuses == null || statuses.isEmpty())
                ? ADMIN_LISTABLE_STATUSES
                : statuses.stream().filter(status -> status != GameTableStatus.Deleted).toList();

        SearchQuery parsed = SearchQueryParser.parse(rawQuery, GameTableSearchField.wireNames());
        Map<SearchTerm, Set<String>> catalogIds = gameTableSearchResolver.resolveCatalogTerms(parsed);
        Page<GameTable> page =
                gameTableRepository.findAll(GameTableSearchSpecification.forAdmin(parsed, catalogIds, effective), pageable);
        return toAdminSummaryPage(page);
    }

    /**
     * The master-side lookup: a table that is there, for a read whose actor cannot be vetoed on it.
     *
     * <p><b>It used to claim to be «the single lookup every read goes through» and it was not</b> -
     * {@code TableSessionService} and {@code TableTaskService} each had their own copy of the same
     * four lines. F3.4 made the claim true by moving the lookup to
     * {@link TableVisibilityService}, which is where the veto of #29 is now written once; this
     * method is a name the callers inside this class already use, and nothing more.
     *
     * <p>Every caller left is a master's or an admin's operation on a table they run, where
     * pertenencia is checked on the next line and being vetoed is not a question that can be asked
     * (#154). The read a <em>player</em> performs is {@link #getDetail}, and it goes through
     * {@link TableVisibilityService#requireVisible}.
     */
    @Transactional(readOnly = true)
    public GameTable getEntityById(String gameTableId) {
        return tableVisibilityService.requireExisting(gameTableId);
    }

    private GameTable buildTable(CreateGameTableRequest request, User creator) {
        GameTable gameTable = new GameTable(request.name(), creator);
        gameTable.setDescription(richTextSanitizer.sanitize(request.description()));
        gameTable.setPermitted(richTextSanitizer.sanitize(request.permitted()));
        gameTable.setRequirements(richTextSanitizer.sanitize(request.requirements()));
        gameTable.setStartDate(request.startDate());
        gameTable.setTotalSessions(request.totalSessions());
        gameTable.setMaxPlayers(request.maxPlayers());
        gameTable.setTableType(resolveTableType(request.tableTypeId()));
        return gameTable;
    }

    /** Resolves the type a draft names, or clears it when the draft names none. */
    private @Nullable TableType resolveTableType(@Nullable String tableTypeId) {
        if (tableTypeId == null) {
            return null;
        }
        return tableTypeRepository
                .findById(tableTypeId)
                .orElseThrow(() -> new NotFoundException("Table type not found: " + tableTypeId));
    }

    /** Sets the three catalogs of a table in one step - each of them a full replacement (#56). */
    private void applyCatalogs(
            String gameTableId, @Nullable List<String> systemIds, @Nullable List<String> tagIds, @Nullable List<String> platformIds) {
        tableCatalogService.replaceLinks(gameTableId, CatalogType.SYSTEMS, orEmpty(systemIds));
        tableCatalogService.replaceLinks(gameTableId, CatalogType.TAGS, orEmpty(tagIds));
        tableCatalogService.replaceLinks(gameTableId, CatalogType.PLATFORMS, orEmpty(platformIds));
    }

    /**
     * Stamps the closing instant, once. A table closes one time (#44, #180): if the column already
     * carries a date, that is the date, and no later transition gets to move it.
     */
    private void sealClosedAt(GameTable gameTable) {
        if (gameTable.getClosedAt() == null) {
            gameTable.setClosedAt(LocalDateTime.now());
        }
    }

    /** An absent list and an empty one mean the same thing on the wire; here they are the same object. */
    private <T> List<T> orEmpty(@Nullable List<T> values) {
        return values != null ? values : List.of();
    }

    private void requirePrimaryOf(String gameTableId, String actorId, String action) {
        if (!masterService.isPrimaryOf(gameTableId, actorId)) {
            throw new ForbiddenActionException("Only the Primary master can " + action + " this table");
        }
    }

    private boolean isAdmin(String actorId) {
        Set<String> roles = userService.loadAuthSnapshot(actorId).roles();
        return roles.contains(PlatformRole.ADMIN.roleName()) || roles.contains(PlatformRole.OWNER.roleName());
    }

    /** Every real transition goes through here: it is the one place that flips status and leaves its trail (#27, #32). */
    private void recordStatusChange(
            GameTable gameTable, GameTableStatus from, GameTableStatus to, String actorId, @Nullable String justification) {
        User changedBy = userService.getById(actorId);
        gameTable.setStatus(to);
        tableStatusChangeRepository.save(new TableStatusChange(gameTable, from, to, changedBy, justification));
    }

    /**
     * One page of cards, with the agendas read in a single query and - when the listing is one where
     * the question makes sense - the clash warning of #178 computed for the actor of the token.
     *
     * <p>{@code /my/tables} and {@code /master/tables} pass {@code false}: a table you already run or
     * play at is the commitment, so warning that it clashes with itself would be noise.
     */
    private PageResponse<GameTableSummaryResponse> toSummaryPage(Page<GameTable> page, @Nullable String conflictActorId) {
        List<GameTable> tables = page.getContent();
        List<String> ids = tables.stream().map(GameTable::getId).toList();
        Map<String, List<TableScheduleEntry>> schedules = tableScheduleService.findByTables(ids);
        Set<String> clashing = conflictActorId == null ? Set.of() : scheduleConflictService.clashingAmong(conflictActorId, tables);

        return PageResponse.from(page.map(gameTable -> gameTableMapper.toSummary(
                gameTable,
                countPlayers(gameTable.getId()),
                findPrimaryMaster(gameTable.getId()),
                schedules.getOrDefault(gameTable.getId(), List.of()),
                clashing.contains(gameTable.getId()))));
    }

    /**
     * The four things a table cannot be run without: what is played, how it is labelled, where, and when (#226, #229).
     *
     * <p>#196 left this exact door open - "if it is later decided that a table cannot open without a
     * calendar, it is a validation of CreateGameTableRequest and not a silent effect of approve()".
     * This is that decision, and it lands where #196 said it would.
     *
     * <p>Tags are required too since #229, which reverses that part of #226: they were left optional
     * to avoid filler labels (#59), and the owner decided that a table nobody can find by subject is
     * the worse of the two problems.
     *
     * <p>It does not apply to {@link #createUnassigned}: that one is an admin's stub, deliberately
     * created with a name and nothing else, for a master to fill in later (#72).
     *
     * @throws InvalidRequestException naming which of the three is missing, so the frontend can say
     *                                 which field to go fix rather than "invalid"
     */
    private void requireRunnableDraft(
            @Nullable List<String> systemIds,
            @Nullable List<String> tagIds,
            @Nullable List<String> platformIds,
            @Nullable List<TableScheduleEntry> schedule) {
        if (orEmpty(systemIds).isEmpty()) {
            throw new InvalidRequestException("A table must declare at least one system", "TABLE_NEEDS_SYSTEM");
        }
        if (orEmpty(platformIds).isEmpty()) {
            throw new InvalidRequestException("A table must declare at least one platform", "TABLE_NEEDS_PLATFORM");
        }
        if (orEmpty(tagIds).isEmpty()) {
            throw new InvalidRequestException("A table must carry at least one tag", "TABLE_NEEDS_TAG");
        }
        if (orEmpty(schedule).isEmpty()) {
            throw new InvalidRequestException("A table must declare at least one weekly slot", "TABLE_NEEDS_SCHEDULE");
        }
    }

    /**
     * One page of the admin listing, with the two expensive per-row reads done once for the whole page.
     *
     * <p>This is the N+1 the F3.3 contract §3.4 names. The old {@code toAdminSummary} asked
     * {@code findPrimaryMasterOrNull} and {@code countPlayers} <b>per row</b>: forty-one queries for a
     * page of twenty, on a screen that had just stopped being limited to the tables in review. The
     * shape of the fix is the one {@code listMineHistory} and {@code FileService.usagesByFileId}
     * already use - resolve the whole page in one grouped read, then map from a map.
     *
     * <p><b>Two batched reads, not every association.</b> {@code tableType} and {@code claimedBy} are
     * still {@code LAZY} and still resolved row by row, and that is left alone on purpose rather than
     * overlooked: both are small closed sets - a handful of table types, a handful of admins - so the
     * first-level cache answers the repeats within the page, and a {@code join fetch} would buy a
     * query or two at the cost of widening every row. The two that were batched are the two where
     * every row names something different, which is where the cache cannot help.
     */
    private PageResponse<AdminTableSummaryResponse> toAdminSummaryPage(Page<GameTable> page) {
        List<String> ids = page.getContent().stream().map(GameTable::getId).toList();
        Map<String, Master> primaries = masterService.findPrimariesByTables(ids);
        Map<String, Long> playerCounts = ids.isEmpty()
                ? Map.of()
                : tableRegistrationRepository.countPlayersByTables(ids, TableRegistrationStatus.Player).stream()
                        .collect(Collectors.toMap(TablePlayerCount::gameTableId, TablePlayerCount::players));

        return PageResponse.from(page.map(gameTable -> toAdminSummary(
                gameTable,
                primaries.get(gameTable.getId()),
                playerCounts.getOrDefault(gameTable.getId(), 0L).intValue())));
    }

    /**
     * @param primary     the table's live Primary, or null for an {@code Unassigned} one (#72)
     * @param playerCount how many people are accepted, already resolved for the whole page
     */
    private AdminTableSummaryResponse toAdminSummary(
            GameTable gameTable, @Nullable Master primary, int playerCount) {
        User claimedBy = gameTable.getClaimedBy();
        return new AdminTableSummaryResponse(
                gameTable.getId(),
                gameTable.getName(),
                gameTable.getStatus().name(),
                gameTable.getTableType() != null ? gameTable.getTableType().getName() : null,
                gameTable.getTableType() != null ? gameTable.getTableType().getCode() : null,
                gameTable.getMaxPlayers(),
                playerCount,
                primary != null ? gameTableMapper.toMasterSummary(primary).name() : null,
                // A reserved table reads the same way a reserved request does (#100), so an admin
                // scanning this listing can see that somebody is already on it.
                claimedBy != null ? displayNameOf(claimedBy) : null,
                gameTable.getCreatedAt());
    }

    /** The screen shows a person, not an id - the same fallback every mapper of the project uses. */
    private static String displayNameOf(User user) {
        return user.getName() != null ? user.getName() : user.getDiscordUsername();
    }

    /** Every transition answers with the table; none of them is the read where the clash matters. */
    private GameTableDetailResponse toDetail(GameTable gameTable) {
        return toDetail(gameTable, null);
    }

    /**
     * @param conflictActorId whose commitments to measure the agenda against, or null when the read
     *                        has no actor for whom the question means anything (#121, #178)
     */
    private GameTableDetailResponse toDetail(GameTable gameTable, @Nullable String conflictActorId) {
        int playerCount = countPlayers(gameTable.getId());
        List<MasterSummaryResponse> masters =
                masterService.findByGameTable(gameTable.getId()).stream().map(gameTableMapper::toMasterSummary).toList();
        Map<CatalogType, List<CatalogValueResponse>> catalogs = tableCatalogService.findLinks(gameTable.getId());

        // Sanitized on the way out as well as on the way in (#62): a row written before this gate
        // existed, or by any path that ever skips it, still reaches the browser through here. The
        // cleaned strings are passed to the mapper and not written back - a read stays a read.
        return gameTableMapper.toDetail(
                gameTable,
                playerCount,
                masters,
                tableScheduleService.findByTable(gameTable.getId()),
                // The calendar rides along with the detail rather than on an endpoint of its own:
                // this read already decides who may see the table at all, and the sessions inherit
                // that single answer instead of repeating the veto check somewhere it could drift.
                tableSessionService.findPublicSessions(gameTable),
                catalogs.get(CatalogType.SYSTEMS),
                catalogs.get(CatalogType.TAGS),
                catalogs.get(CatalogType.PLATFORMS),
                // Same call, same reason as the calendar above: only what the table shares, and only
                // once this read has already established the reader may see the table at all (#29, #79).
                tableFileService.sharedFilesOf(gameTable.getId()),
                richTextSanitizer.sanitize(gameTable.getDescription()),
                richTextSanitizer.sanitize(gameTable.getPermitted()),
                richTextSanitizer.sanitize(gameTable.getRequirements()),
                conflictActorId != null && scheduleConflictService.findClashWith(conflictActorId, gameTable) != null);
    }

    private int countPlayers(String gameTableId) {
        return (int) tableRegistrationRepository.countByGameTable_IdAndStatus(gameTableId, TableRegistrationStatus.Player);
    }

    private MasterSummaryResponse findPrimaryMaster(String gameTableId) {
        return masterService.findByGameTable(gameTableId).stream()
                .filter(master -> master.getMasterType() == MasterType.Primary)
                .findFirst()
                .map(gameTableMapper::toMasterSummary)
                .orElseThrow(() -> new IllegalStateException("Table has no Primary master: " + gameTableId));
    }

    private @Nullable MasterSummaryResponse findPrimaryMasterOrNull(String gameTableId) {
        return masterService.findByGameTable(gameTableId).stream()
                .filter(master -> master.getMasterType() == MasterType.Primary)
                .findFirst()
                .map(gameTableMapper::toMasterSummary)
                .orElse(null);
    }

    private GameTable lockTable(String gameTableId) {
        return gameTableRepository.findByIdForUpdate(gameTableId).orElseThrow(() -> new NotFoundException("Table not found: " + gameTableId));
    }
}
