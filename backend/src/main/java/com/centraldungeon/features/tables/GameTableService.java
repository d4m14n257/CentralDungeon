package com.centraldungeon.features.tables;

import com.centraldungeon.features.queue.QueueClaimRule;
import com.centraldungeon.features.catalogs.CatalogType;
import com.centraldungeon.features.catalogs.TableCatalogService;
import com.centraldungeon.features.catalogs.dto.CatalogValueResponse;
import com.centraldungeon.features.notifications.NotificationService;
import com.centraldungeon.features.notifications.NotificationType;
import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.exception.ForbiddenActionException;
import com.centraldungeon.common.exception.InvalidRequestException;
import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.common.search.SearchQuery;
import com.centraldungeon.common.search.SearchQueryParser;
import com.centraldungeon.common.search.SearchTerm;
import com.centraldungeon.common.text.RichTextSanitizer;
import com.centraldungeon.features.files.TableFileService;
import com.centraldungeon.features.registrations.RegistrationService;
import com.centraldungeon.features.registrations.TablePlayerCount;
import com.centraldungeon.features.registrations.TableRegistration;
import com.centraldungeon.features.registrations.TableRegistrationRepository;
import com.centraldungeon.features.registrations.TableRegistrationStatus;
import com.centraldungeon.features.settings.SettingsService;
import com.centraldungeon.features.tables.dto.AddMasterRequest;
import com.centraldungeon.features.tables.dto.AdminTableSummaryResponse;
import com.centraldungeon.features.tables.dto.AssignMastersRequest;
import com.centraldungeon.features.tables.dto.AttendanceSummaryResponse;
import com.centraldungeon.features.tables.dto.ChangeTableStatusRequest;
import com.centraldungeon.features.tables.dto.CreateGameTableRequest;
import com.centraldungeon.features.tables.dto.GameTableDetailResponse;
import com.centraldungeon.features.tables.dto.GameTableHistoryResponse;
import com.centraldungeon.features.tables.dto.GameTableSummaryResponse;
import com.centraldungeon.features.tables.dto.MasterSummaryResponse;
import com.centraldungeon.features.tables.dto.SetSeekingPlayersRequest;
import com.centraldungeon.features.tables.dto.TableScheduleEntry;
import com.centraldungeon.features.tables.dto.TableSections;
import com.centraldungeon.features.tables.dto.TableStatusChangeResponse;
import com.centraldungeon.features.tables.dto.UpdateGameTableRequest;
import com.centraldungeon.features.users.PlatformRole;
import com.centraldungeon.features.users.User;
import com.centraldungeon.features.users.UserService;
import com.centraldungeon.features.users.dto.UserSummaryResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
 * The table's whole lifecycle, and the only place its nine states move. Every transition
 * below follows the same three steps: lock the table, refuse if it is
 * not in the one status the transition starts from, and record the change with its author.
 *
 * <p>Two things it never leaves to the caller. <b>Membership</b>: who may act on a concrete table
 * is a row in {@code masters}, not a role, so it is checked here rather than in a
 * {@code @PreAuthorize} that cannot see the resource. And <b>locking</b>: the
 * invariants MySQL cannot express as constraints - one live Primary, one active registration
 * per pair, the player cap - are held by taking a pessimistic lock on the table row.
 */
@Service
public class GameTableService {

    /**
     * /my/tables: the statuses that still count as "vivo". {@code Pause} belongs here and
     * not with {@link #HISTORY_STATUSES} - it is a table frozen, not one that ended; the
     * player who plays there still needs to find it among "mine".
     */
    private static final List<GameTableStatus> LIVE_MINE_STATUSES = List.of(
            GameTableStatus.Opened, GameTableStatus.InProgress,
            // PauseRequested is on the list because a table whose master has asked for a pause is still being played - nothing has
            // been decided yet - so dropping it from "mine" would make a player's table vanish while
            // an admin thinks it over, and reappear either way.
            GameTableStatus.PauseRequested,
            GameTableStatus.Pause);

    /** /my/tables/history: the two ways a run is over, and nothing else. */
    private static final List<GameTableStatus> HISTORY_STATUSES = List.of(GameTableStatus.Finished, GameTableStatus.Canceled);

    /**
     * /admin/tables: every status a table can actually be seen in - which is all of them but
     * {@code Deleted}.
     *
     * <p>It replaces the three-status default that used to live here, whose own comment said «until
     * the queue screen exists». The queue screen exists, and with it the review work moved to
     * {@code /admin/queue}: what is left of {@code /admin/tables} is the management listing, and a
     * management listing that hides two thirds of the platform is not one. Narrowing is still
     * available - {@code ?status=} and now {@code /table_status} inside {@code ?q=} - but it is the
     * caller's to ask for, not a filter hidden in the endpoint.
     */
    private static final List<GameTableStatus> ADMIN_LISTABLE_STATUSES =
            Arrays.stream(GameTableStatus.values()).filter(status -> status != GameTableStatus.Deleted).toList();

    /**
     * The statuses in which a table is still its master's to rewrite.
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

    /**
     * The statuses in which an admin may still rewrite a table: every one but the three that
     * close it.
     *
     * <p>Wider than {@link #EDITABLE_STATUSES} on purpose. The master's set is narrow because people
     * apply on the strength of what an open table says; the admin's door exists precisely for the
     * table that is already open, or running, and needs correcting - a wrong platform, an agenda that
     * moved, a description that breaks a rule - with the masters told that it happened. A finished,
     * cancelled or removed table is a record of something that happened and nobody rewrites it.
     */
    private static final Set<GameTableStatus> ADMIN_EDITABLE_STATUSES = Arrays.stream(GameTableStatus.values())
            .filter(status -> status != GameTableStatus.Finished
                    && status != GameTableStatus.Canceled
                    && status != GameTableStatus.Deleted)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    /** The statuses in which candidates can be waiting, so where an admin shrinking the capacity can fill the table. */
    private static final Set<GameTableStatus> SEAT_FILLING_STATUSES =
            Set.of(GameTableStatus.Opened, GameTableStatus.InProgress);

    private static final List<TableRegistrationStatus> ACTIVE_REGISTRATION_STATUSES =
            List.of(TableRegistrationStatus.Candidate, TableRegistrationStatus.Player);

    /** cancel() is the one transition with more than one valid "from". */
    private static final Set<GameTableStatus> CANCELABLE_STATUSES = Set.of(
            GameTableStatus.Unassigned, GameTableStatus.Preparation, GameTableStatus.ChangesRequested,
            GameTableStatus.Opened, GameTableStatus.InProgress,
            // A table waiting on a pause it may never get is one its master has to be able to end.
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

    /**
     * Where the largest allowed {@code max_players} comes from: {@code tables.max_players_cap} in
     * {@code system_settings}.
     *
     * <p>Without it {@code maxPlayers} would only be {@code @Positive}, so a
     * table could be created for two billion people; the maximum player cap is one of the values the
     * community adjusts without a deploy, and a limit that does not exist cannot be adjusted. The
     * cap gates what is <em>written</em> and never what is already stored - lowering it does not
     * shrink an existing table, and the next edit of a table already above it is what gets refused.
     */
    private final SettingsService settingsService;
    private final NotificationService notificationService;
    private final TableChangeNotifier tableChangeNotifier;
    private final GameTableSearchResolver gameTableSearchResolver;
    private final TableVisibilityService tableVisibilityService;
    private final RegistrationService registrationService;

    /**
     * @param gameTableRepository        the {@code game_tables} table, and the row everything locks on
     * @param tableTypeRepository        resolves the type a draft names
     * @param tableRegistrationRepository counts players and finds out whether anybody is involved yet
     * @param tableStatusChangeRepository the lifecycle's audit trail
     * @param masterService              answers membership and keeps the one-Primary invariant
     * @param gameTableMapper            entity to DTO
     * @param userService                resolves the actor, and their roles for the admin checks
     * @param tableScheduleService       owns the weekly agenda and the clash check that guards it
     * @param scheduleConflictService    computes the warning the explorer's cards show
     * @param tableCatalogService        links the table to its systems, tags and platforms
     * @param tableSessionService        materializes the calendar when the table opens, and re-lays it
     *                                   when it comes back from a pause
     * @param tableFileService           the files the table shares, which ride along with the detail
     *                                   for the same reason the calendar does
     * @param richTextSanitizer          cleans the rich text on the way in and on the way out
     * @param settingsService            the largest allowed player cap, editable without a deploy
     * @param notificationService        tells the masters how the review ended
     * @param tableChangeNotifier        tells everybody joined to a table that it changed
     * @param gameTableSearchResolver    expands the explorer's catalog criteria into synonym groups
     *                                   before the query is built
     * @param tableVisibilityService     the one gate every read of a concrete table goes through -
     *                                   soft delete and veto, decided in one place
     * @param registrationService        owns the rejections, so the detail can tell a rejected applicant why
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
            SettingsService settingsService,
            NotificationService notificationService,
            TableChangeNotifier tableChangeNotifier,
            GameTableSearchResolver gameTableSearchResolver,
            TableVisibilityService tableVisibilityService,
            RegistrationService registrationService) {
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
        this.settingsService = settingsService;
        this.notificationService = notificationService;
        this.tableChangeNotifier = tableChangeNotifier;
        this.gameTableSearchResolver = gameTableSearchResolver;
        this.tableVisibilityService = tableVisibilityService;
        this.registrationService = registrationService;
    }

    /**
     * Tells every master of the table how its review ended.
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

    /** The creator becomes the table's Primary master; a Master row is the source of membership, not the role alone. */
    @Transactional
    public GameTableDetailResponse create(CreateGameTableRequest request, String creatorId) {
        requireRunnableDraft(request.systemIds(), request.tagIds(), request.platformIds(), request.schedule());
        User creator = userService.getById(creatorId);
        GameTable gameTable = buildTable(request, creator);

        gameTable = gameTableRepository.save(gameTable);
        masterService.createPrimary(gameTable, creator);
        applyCatalogs(gameTable.getId(), request.systemIds(), request.tagIds(), request.platformIds());
        // After the Master row exists: the master clash rule measures the agenda against what the actor is already
        // committed to, and creating the table is itself one of those commitments.
        tableScheduleService.replace(gameTable, orEmpty(request.schedule()), creatorId);

        return toDetail(gameTable);
    }

    /**
     * The master editing their own table: the wizard's second pass, and how a table sent back with
     * {@code ChangesRequested} is corrected.
     *
     * <p>Membership, not role: running <em>this</em> table is what authorizes the
     * edit, and it is checked here because no {@code @PreAuthorize} can see the resource. Only the
     * statuses in which a table is still being written are editable - once it is open, people have
     * applied on the strength of what it says, and changing it out from under them is a different
     * flow.
     *
     * <p>The agenda goes last on purpose: {@code duration} is what gives a slot its length, so the
     * clash check has to run against the duration the table is about to have and not the one it had.
     *
     * @param gameTableId the table to edit
     * @param request     the whole table as it should end up. Absent means empty, not unchanged
     * @param actorId     the actor, from the token; has to run the table
     * @return the table after the edit
     * @throws NotFoundException if the table does not exist, or the actor cannot see it - the same
     *         answer as a table that does not exist
     * @throws ForbiddenActionException if the actor sees the table but does not run it
     * @throws ConflictException if the table is past the point where its own master may rewrite it,
     *         or if the new agenda clashes with something the actor is committed to
     */
    @Transactional
    public GameTableDetailResponse update(String gameTableId, UpdateGameTableRequest request, String actorId) {
        GameTable gameTable = lockTable(gameTableId);
        if (!masterService.isMasterOf(gameTableId, actorId)) {
            // Said only about a table they may see: to anybody else it is the same 404 as an id nobody used.
            tableVisibilityService.requireVisible(gameTableId, actorId);
            throw new ForbiddenActionException("Only a master of this table can edit it");
        }
        if (!EDITABLE_STATUSES.contains(gameTable.getStatus())) {
            throw new ConflictException("A table in status " + gameTable.getStatus() + " can no longer be edited by its master");
        }
        // Authorized first, then the draft itself: somebody who does not run this table gets a 404 or a
        // 403, never a complaint about its agenda.
        // Nobody is told: a master only edits a table that has not gone public yet.
        rewrite(gameTable, request, actorId);
        return toDetail(gameTable);
    }

    /**
     * An admin rewriting somebody else's table, from /admin/tables/:id/edit.
     *
     * <p><b>A door of its own, not {@link #update} with a second rule inside</b>: the master's
     * edit and the admin's differ in who may, when, and what follows, and one path answering two ways
     * depending on who asks is a shape the project rejected. What they share is the rewrite itself.
     *
     * <ul>
     *   <li><b>When</b>: in any status but the three that close a table ({@link #ADMIN_EDITABLE_STATUSES}).
     *       While it is in review, not if another admin reserved it from the tray - the same rule as
     *       approving it.
     *   <li><b>The agenda's clash check</b> measures against the <em>Primary</em>'s other
     *       commitments, because the admin is committed to nothing here. A table with no master yet
     *       is checked only for its own coherence, as when an admin creates it.
     *   <li><b>The capacity</b> cannot drop below the players already in: nobody is removed to make a
     *       number fit. Lowered to exactly the players already in, on a table
     *       that takes applications, it fills the table the way accepting the last candidate does: the
     *       remaining candidates are turned down with {@code TABLE_FULL} and the search for players
     *       stops, in the same transaction and under the same lock.
     *   <li><b>The masters are told what changed</b> - one notification listing the fields - and the
     *       players only when the agenda moved. The admin is never told about their own edit, and an
     *       edit that changed nothing tells nobody.
     * </ul>
     *
     * @param gameTableId the table to rewrite
     * @param request     the whole table as it should end up. Absent means empty, not unchanged
     * @param actorId     the admin, from the token
     * @return the table after the edit
     * @throws ConflictException if the table is closed, reserved by another admin
     *                           ({@code ITEM_ALREADY_CLAIMED}), smaller than its roster
     *                           ({@code CAPACITY_BELOW_PLAYERS}), or its agenda clashes with what one
     *                           of its masters is committed to ({@code MASTER_SCHEDULE_CONFLICT})
     */
    @Transactional
    public GameTableDetailResponse adminUpdate(String gameTableId, UpdateGameTableRequest request, String actorId) {
        GameTable gameTable = lockTable(gameTableId);
        if (!ADMIN_EDITABLE_STATUSES.contains(gameTable.getStatus())) {
            throw new ConflictException("A table in status " + gameTable.getStatus() + " is closed and cannot be edited");
        }
        if (gameTable.getStatus() == GameTableStatus.Preparation) {
            QueueClaimRule.requireNotHeldByAnother(gameTable.getClaimedBy(), actorId, "table " + gameTableId);
        }
        boolean fillsTheSeats = false;
        if (request.maxPlayers() != null) {
            long players = tableRegistrationRepository.countByGameTable_IdAndStatus(gameTableId, TableRegistrationStatus.Player);
            if (request.maxPlayers() < players) {
                throw new ConflictException(
                        "Capacity " + request.maxPlayers() + " is below the " + players + " players the table already has",
                        ConflictException.CAPACITY_BELOW_PLAYERS,
                        Map.of(ConflictException.PARAM_PLAYER_COUNT, String.valueOf(players)));
            }
            fillsTheSeats = request.maxPlayers() == players && SEAT_FILLING_STATUSES.contains(gameTable.getStatus());
        }

        List<String> changes = rewrite(gameTable, request, actorId);
        if (fillsTheSeats) {
            registrationService.onSeatsFilled(gameTable);
        }
        tableChangeNotifier.announceEdit(gameTable, actorId, changes);
        return toDetail(gameTable);
    }

    /**
     * The rewrite the master's edit and the admin's share: every field replaced, the catalogs and the
     * agenda as whole sets.
     *
     * <p>The same three requirements as create - a rewrite that empties the agenda or the
     * catalogs would leave the table in a state creating it could never have reached. The agenda goes
     * last: {@code duration} is what gives a slot its length, so the clash check has to run against
     * the duration the table is about to have.
     *
     * @param actorId who is rewriting, from the token: the agenda is measured against every master of the
     *                table, and this only decides whose clash it reads as
     * @return the codes of the fields the rewrite actually changed, compared against the values that
     *         are stored (the sanitized text, not what was typed); empty when nothing changed
     */
    private List<String> rewrite(GameTable gameTable, UpdateGameTableRequest request, String actorId) {
        requireRunnableDraft(request.systemIds(), request.tagIds(), request.platformIds(), request.schedule());

        String description = richTextSanitizer.sanitize(request.description());
        String permitted = richTextSanitizer.sanitize(request.permitted());
        String requirements = richTextSanitizer.sanitize(request.requirements());
        TableSections sections = sanitize(request.sections());
        Integer maxPlayers = requireWithinPlayerCap(request.maxPlayers());
        TableType tableType = resolveTableType(request.tableTypeId());
        List<String> changes = new ArrayList<>(TableEditDiff.of(
                gameTable, request, description, permitted, requirements, maxPlayers,
                tableCatalogService.findLinks(gameTable.getId()), sections));

        gameTable.setName(request.name());
        gameTable.setDescription(description);
        gameTable.setPermitted(permitted);
        gameTable.setRequirements(requirements);
        applySections(gameTable, sections);
        gameTable.setStartDate(request.startDate());
        gameTable.setTotalSessions(request.totalSessions());
        gameTable.setMaxPlayers(maxPlayers);
        gameTable.setTableType(tableType);

        applyCatalogs(gameTable.getId(), request.systemIds(), request.tagIds(), request.platformIds());
        if (tableScheduleService.replace(gameTable, orEmpty(request.schedule()), actorId)) {
            changes.add(TableEditDiff.SCHEDULE);
        }
        return changes;
    }

    /**
     * A table an admin creates without running it: it is born directly in Unassigned, with no
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
        // No owner yet, so there is nobody whose commitments the agenda could clash with. The clash rule is
        // checked when the table gets its masters, in assignInitialMasters.
        tableScheduleService.replace(gameTable, orEmpty(request.schedule()), null);
        return toDetail(gameTable);
    }

    /**
     * A draft opened on somebody's behalf, when an admin approves their request to run a single table.
     *
     * <p>The requester becomes its author and its Primary, so they can write and send it to review like
     * any master's draft - and that is all they get: no platform role is granted, because running this
     * one table is what the Master row authorizes, while the role means creating tables of one's own.
     * Nothing beyond the name is required here: the systems, tags, platforms and agenda are demanded
     * when the master edits the draft, and checked again on the stored table when they send it to
     * review, so the bare draft cannot reach a reviewer.
     *
     * @param ownerId who asked for the table, the one who will run it
     * @param name    the name they proposed for it
     * @return the id of the new table, in Draft
     * @throws NotFoundException if the owner's account does not exist
     */
    @Transactional
    public String createDraftFor(String ownerId, String name) {
        User owner = userService.getById(ownerId);
        GameTable gameTable = gameTableRepository.save(new GameTable(name, owner));
        masterService.createPrimary(gameTable, owner);
        return gameTable.getId();
    }

    /** Assigning masters to an Unassigned table skips review entirely and opens it directly. */
    @Transactional
    public GameTableDetailResponse assignInitialMasters(String gameTableId, AssignMastersRequest request, String actorId) {
        GameTable gameTable = lockTable(gameTableId);
        if (gameTable.getStatus() != GameTableStatus.Unassigned) {
            throw new ConflictException("Cannot assign masters to a table in status " + gameTable.getStatus());
        }
        List<String> secondaries = request.secondaryUserIds() != null ? request.secondaryUserIds() : List.of();

        // The master clash rule, deferred from creation: an Unassigned table had no master to clash with, and this is
        // the moment it gets them - all of them, co-masters too. Refusing here is better than opening a
        // table its own masters cannot actually run.
        List<User> assigned = new ArrayList<>();
        assigned.add(userService.getById(request.primaryUserId()));
        secondaries.forEach(secondaryId -> assigned.add(userService.getById(secondaryId)));
        tableScheduleService.requireNoClash(assigned, gameTableId, scheduleConflictService.intervalsOf(gameTable), actorId);

        masterService.assignInitialMasters(gameTable, request.primaryUserId(), secondaries);
        // **They are told they now run it**. This is the one assignment nobody asked for: an
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
     * An admin approving a master's draft - the only way Preparation reaches Opened now.
     *
     * <p>Opening is what materializes the calendar: from here on the table has dates and
     * not only a weekly shape. A table missing a start date, an agenda or a session count opens with
     * no sessions rather than being refused - materializing is a consequence of opening, not a
     * precondition for it.
     *
     * <p><b>It refuses a table another admin has reserved</b> ({@link QueueClaimRule}). Not "reserved by the
     * actor": an unreserved table is nobody's and approving it is an implicit claim - the screen that offers
     * this moved to {@code /admin/queue}, but the endpoint stayed here because approving is an operation on
     * the table aggregate and nowhere else, and it has to keep working for whoever reaches it without passing
     * through the tray. What the check buys is that a stale link or a second tab cannot take a review out
     * from under the colleague who took it.
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
        QueueClaimRule.requireNotHeldByAnother(gameTable.getClaimedBy(), actorId, "table " + gameTableId);
        recordStatusChange(gameTable, GameTableStatus.Preparation, GameTableStatus.Opened, actorId, null);
        tableSessionService.materialize(gameTable);
        announceReviewOutcome(gameTable, NotificationType.TableApproved);
        return toDetail(gameTable);
    }

    /**
     * An admin sending a draft back to its master, with a reason. Preparation to ChangesRequested.
     *
     * <p>Same reservation check as {@link #approve} and for the same reason: the two answers to
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
        QueueClaimRule.requireNotHeldByAnother(gameTable.getClaimedBy(), actorId, "table " + gameTableId);
        recordStatusChange(gameTable, GameTableStatus.Preparation, GameTableStatus.ChangesRequested, actorId, request.justification());
        announceReviewOutcome(gameTable, NotificationType.TableChangesRequested);
        return toDetail(gameTable);
    }

    /**
     * The master sending their draft to review for the first time. Draft to Preparation.
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
     * @throws NotFoundException if the table does not exist, or the actor cannot see it - the same
     *         answer as a table that does not exist
     * @throws ForbiddenActionException if the actor sees the table but is not its Primary
     * @throws ConflictException if the table was not a draft
     * @throws InvalidRequestException {@code TABLE_NEEDS_SYSTEM}, {@code TABLE_NEEDS_PLATFORM},
     *         {@code TABLE_NEEDS_TAG} or {@code TABLE_NEEDS_SCHEDULE} when the draft lacks one of the
     *         four things a table cannot run without - the same requirement editing it enforces
     */
    @Transactional
    public GameTableDetailResponse submitForReview(String gameTableId, String actorId) {
        GameTable gameTable = lockTable(gameTableId);
        requirePrimaryOf(gameTableId, actorId, "submit for review");
        if (gameTable.getStatus() != GameTableStatus.Draft) {
            throw new ConflictException("Cannot send a table in status " + gameTable.getStatus() + " to review");
        }
        requireRunnableAsStored(gameTableId);
        recordStatusChange(gameTable, GameTableStatus.Draft, GameTableStatus.Preparation, actorId, null);
        return toDetail(gameTable);
    }

    /**
     * The master sending a corrected draft back for review. ChangesRequested to Preparation.
     *
     * @param gameTableId the table
     * @param actorId     the actor, from the token; has to be the table's Primary
     * @return the table after the change
     * @throws NotFoundException if the table does not exist, or the actor cannot see it - the same
     *         answer as a table that does not exist
     * @throws ForbiddenActionException if the actor sees the table but is not its Primary
     * @throws ConflictException if the table was not in ChangesRequested
     * @throws InvalidRequestException {@code TABLE_NEEDS_SYSTEM}, {@code TABLE_NEEDS_PLATFORM},
     *         {@code TABLE_NEEDS_TAG} or {@code TABLE_NEEDS_SCHEDULE} when the table lacks one of the
     *         four things a table cannot run without
     */
    @Transactional
    public GameTableDetailResponse resubmit(String gameTableId, String actorId) {
        GameTable gameTable = lockTable(gameTableId);
        requirePrimaryOf(gameTableId, actorId, "resubmit");
        if (gameTable.getStatus() != GameTableStatus.ChangesRequested) {
            throw new ConflictException("Cannot resubmit a table in status " + gameTable.getStatus());
        }
        requireRunnableAsStored(gameTableId);
        recordStatusChange(gameTable, GameTableStatus.ChangesRequested, GameTableStatus.Preparation, actorId, null);
        return toDetail(gameTable);
    }

    /**
     * The master declaring play has begun. Opened to InProgress.
     *
     * @param gameTableId the table
     * @param actorId     the actor, from the token; has to be the table's Primary
     * @return the table after the change
     * @throws NotFoundException if the table does not exist, or the actor cannot see it - the same
     *         answer as a table that does not exist
     * @throws ForbiddenActionException if the actor sees the table but is not its Primary
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
     * <p>This is where {@code closed_at} gets stamped. It is not bookkeeping: it starts the
     * two-week window in which the people who shared the table can still see each other's profiles,
     * and ending a table without recording when it ended would leave that window with no start.
     *
     * <p>Everybody else joined to the table is told it is over: its co-masters and its players.
     *
     * @param gameTableId the table
     * @param actorId     the actor, from the token; has to be the table's Primary
     * @return the table after the change
     * @throws NotFoundException if the table does not exist, or the actor cannot see it - the same
     *         answer as a table that does not exist
     * @throws ForbiddenActionException if the actor sees the table but is not its Primary
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
        tableChangeNotifier.announceTransition(gameTable, NotificationType.TableFinished, actorId, null);
        return toDetail(gameTable);
    }

    /**
     * Either the table's own Primary or a platform admin may cancel it - the only transition
     * either can trigger.
     *
     * <p>Everybody else joined to the table is told: its co-masters and its players. A master hears
     * {@code TableCanceledByAdmin} when the one who cancelled is an admin who does not run the table.
     *
     * @param gameTableId the table
     * @param actorId     the Primary or an admin, from the token
     * @param request     the justification, kept in the status history
     * @return the table, now Canceled
     * @throws NotFoundException if the table does not exist, or the actor cannot see it - the same
     *         answer as a table that does not exist
     * @throws ForbiddenActionException if the actor sees the table but is neither its Primary nor an admin
     * @throws ConflictException        if the table cannot be cancelled from its current status
     */
    @Transactional
    public GameTableDetailResponse cancel(String gameTableId, String actorId, ChangeTableStatusRequest request) {
        GameTable gameTable = lockTable(gameTableId);
        if (!masterService.isPrimaryOf(gameTableId, actorId) && !isAdmin(actorId)) {
            // Said only about a table they may see: to anybody else it is the same 404 as an id nobody used.
            tableVisibilityService.requireVisible(gameTableId, actorId);
            throw new ForbiddenActionException("Only the Primary master or an admin can cancel this table");
        }
        GameTableStatus from = gameTable.getStatus();
        if (!CANCELABLE_STATUSES.contains(from)) {
            throw new ConflictException("Cannot cancel a table in status " + from);
        }
        sealClosedAt(gameTable);
        recordStatusChange(gameTable, from, GameTableStatus.Canceled, actorId, request.justification());
        tableChangeNotifier.announceTransition(gameTable, NotificationType.TableCanceled, actorId, null);
        return toDetail(gameTable);
    }

    /**
     * Soft delete of a table that never went public. Same actors as cancel - the Primary
     * or an admin - and the same lock, but a different meaning: cancel closes a table that existed
     * for other people, this one removes a draft that did not.
     *
     * <p>Deleting is logical and the service carries the cascade explicitly, in one transaction: the master rows, the
     * registrations, the attached files, the blank forms of its requests and the catalog links of the
     * table fall with it, all stamped with the same instant. A link left live would keep reporting the
     * table as a use of the file or the catalog value, and keep the file out of the purge. The status
     * change is recorded too - the trail survives even when the table does not.
     *
     * <p>When an admin does it, every master the table had is told ({@code TableDeletedByAdmin}).
     *
     * @param gameTableId the table
     * @param actorId     the Primary or an admin, from the token
     * @throws NotFoundException if the table does not exist, or the actor cannot see it - the same
     *         answer as a table that does not exist
     * @throws ForbiddenActionException if the actor sees the table but is neither its Primary nor an admin
     * @throws ConflictException        if the table already went public, or has candidates or players
     */
    @Transactional
    public void delete(String gameTableId, String actorId) {
        GameTable gameTable = lockTable(gameTableId);
        if (!masterService.isPrimaryOf(gameTableId, actorId) && !isAdmin(actorId)) {
            // Said only about a table they may see: to anybody else it is the same 404 as an id nobody used.
            tableVisibilityService.requireVisible(gameTableId, actorId);
            throw new ForbiddenActionException("Only the Primary master or an admin can delete this table");
        }
        GameTableStatus from = gameTable.getStatus();
        // Only a table that was never public: nobody saw it, so there is no history worth
        // keeping. Everything past that is cancelled instead.
        if (!from.wasNeverPublic()) {
            throw new ConflictException("A table in status " + from + " cannot be deleted - cancel it instead");
        }
        if (tableRegistrationRepository.existsByGameTable_IdAndStatusIn(gameTableId, ACTIVE_REGISTRATION_STATUSES)) {
            throw new ConflictException("A table with candidates or players cannot be deleted - cancel it instead");
        }

        // Read before they are marked gone: after the removal the table has no live masters to tell.
        List<Master> masters = masterService.findByGameTable(gameTableId);
        LocalDateTime deletedAt = LocalDateTime.now();
        for (TableRegistration registration : tableRegistrationRepository.findByGameTable_Id(gameTableId)) {
            registration.setStatus(TableRegistrationStatus.Deleted);
            registration.setDeletedAt(deletedAt);
        }
        masterService.softDeleteAllOfTable(gameTableId, deletedAt);
        tableFileService.detachAllOfDeletedTable(gameTableId, deletedAt);
        tableCatalogService.removeAllLinks(gameTableId, deletedAt);
        gameTable.setDeletedAt(deletedAt);
        recordStatusChange(gameTable, from, GameTableStatus.Deleted, actorId, null);
        tableChangeNotifier.announceDeleted(gameTable, actorId, masters);
    }

    /**
     * An admin pausing a table directly. InProgress to Pause.
     *
     * <p>The other road to Pause - a master <em>asking</em> for one - goes through
     * {@link #requestPause} and an approval request. The agenda freezes by being filtered on read.
     *
     * <p>Everybody joined to the table is told: its masters ({@code TablePausedByAdmin} unless the admin
     * is one of them) and its players.
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
        tableChangeNotifier.announceTransition(gameTable, NotificationType.TablePaused, actorId, null);
        return toDetail(gameTable);
    }

    /**
     * The master <em>asking</em> for a pause. InProgress to PauseRequested.
     *
     * <p><b>This is the only producer of {@code PauseRequested}</b>, since the asking itself is carried
     * by an {@code approval_requests} row.
     *
     * <p>It only moves the table. The request itself is written by {@code ApprovalService}, in the
     * same transaction and as the caller of this method, because the mechanism has to stay single:
     * a second place that created approval rows would be a second set of rules about them.
     *
     * <p><b>The table keeps its slot while it waits.</b> {@code ScheduleConflictService} counts
     * {@code PauseRequested} among the committing statuses on purpose - nothing has been granted
     * yet, and releasing the hours before the answer arrives would let a master take on something
     * else and then be refused the resume.
     *
     * @param gameTableId the table
     * @param actorId     the actor, from the token; has to run the table
     * @return the table, now PauseRequested
     * @throws NotFoundException if the table does not exist, or the actor cannot see it - the same
     *         answer as a table that does not exist
     * @throws ForbiddenActionException if the actor sees the table but does not run it
     * @throws ConflictException 409 {@code PAUSE_ALREADY_REQUESTED} when it is already waiting on
     *                           one, or a plain conflict from any other status
     */
    @Transactional
    public GameTableDetailResponse markPauseRequested(String gameTableId, String actorId) {
        GameTable gameTable = lockTable(gameTableId);
        // isMasterOf and not isPrimaryOf: asking is not deciding, and a co-master who cannot run
        // next week's session is exactly the person with a reason to ask.
        if (!masterService.isMasterOf(gameTableId, actorId)) {
            // Said only about a table they may see: to anybody else it is the same 404 as an id nobody used.
            tableVisibilityService.requireVisible(gameTableId, actorId);
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
     * An admin saying yes to a requested pause. PauseRequested to Pause.
     *
     * <p>Called by {@code ApprovalService} when a {@code TablePause} request is approved. The
     * resolution note becomes the justification, which is not bookkeeping: a reason is
     * mandatory for {@code Pause}, and the reason an admin gave when they approved <em>is</em> that
     * reason. Inventing a second one would leave two answers to the same question.
     *
     * <p>Freezing the agenda needs nothing here. It is derived on read
     * ({@code TableSessionService.visibleSessionsOf}) and has to be, because the pause is reversible:
     * a pause that deleted the pending sessions could not put them back.
     *
     * <p>Everybody joined to the table is told except the admin and the master who asked: the
     * requester already hears the answer through {@code ApprovalRequestApproved}, while their
     * co-masters and the players learn of it here.
     *
     * @param gameTableId    the table
     * @param actorId        the admin, from the token
     * @param requesterId    the master who asked for the pause, who is not notified twice
     * @param resolutionNote why they approved it - recorded as the pause's justification
     * @return the table, now Pause
     * @throws ConflictException if the table was not waiting on a pause
     */
    @Transactional
    public GameTableDetailResponse applyApprovedPause(
            String gameTableId, String actorId, String requesterId, String resolutionNote) {
        GameTable gameTable = lockTable(gameTableId);
        if (gameTable.getStatus() != GameTableStatus.PauseRequested) {
            throw new ConflictException("Cannot grant a pause to a table in status " + gameTable.getStatus());
        }
        recordStatusChange(gameTable, GameTableStatus.PauseRequested, GameTableStatus.Pause, actorId, resolutionNote);
        tableChangeNotifier.announceTransition(gameTable, NotificationType.TablePaused, actorId, requesterId);
        return toDetail(gameTable);
    }

    /**
     * An admin saying no to a requested pause. PauseRequested back to InProgress.
     *
     * <p><b>A rejection with an effect.</b> Most types of request do nothing
     * when they are rejected. A pause is different because asking for one already moved the table: leaving it
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
     * <p><b>The clash is checked again</b>. A paused table does not reserve its slot, so
     * its master was free to take on something else while it was down; coming back is the moment
     * those slots are claimed again. If they are no longer free the resume is refused with a
     * {@code 409} that names the other table - the same answer the master clash rule gives everywhere else, because a
     * table its own master cannot attend is not a table that has resumed. The way out is to move one
     * of the two agendas.
     *
     * <p><b>The pending sessions are re-laid</b> from this instant. What was played and what
     * was called off keep their dates; the run's numbering does not move.
     *
     * <p>Everybody joined to the table is told once, and that one notice replaces the per-player
     * calendar notice the re-laying used to send: its masters ({@code TableResumedByAdmin} unless the
     * admin is one of them) and its players.
     *
     * @param gameTableId the table
     * @param actorId     the admin, from the token
     * @return the table, back in play, with its calendar re-laid
     * @throws ConflictException if the table was not paused, or if its agenda now clashes with
     *                           something one of its masters is committed to
     */
    @Transactional
    public GameTableDetailResponse resume(String gameTableId, String actorId) {
        GameTable gameTable = lockTable(gameTableId);
        if (gameTable.getStatus() != GameTableStatus.Pause) {
            throw new ConflictException("Cannot resume a table in status " + gameTable.getStatus());
        }

        // Every master may have committed elsewhere while the table was frozen, not only the Primary.
        List<User> masters = masterService.findByGameTable(gameTableId).stream().map(Master::getUser).toList();
        tableScheduleService.requireNoClash(masters, gameTableId, scheduleConflictService.intervalsOf(gameTable), actorId);

        recordStatusChange(gameTable, GameTableStatus.Pause, GameTableStatus.InProgress, actorId, null);
        tableSessionService.rescheduleAfterPause(gameTable, LocalDateTime.now());
        tableChangeNotifier.announceTransition(gameTable, NotificationType.TableResumed, actorId, null);
        return toDetail(gameTable);
    }

    /**
     * Turns the search for players on or off on a table that is already running, so it can find a
     * replacement. While it is on, the table shows in the explorer and takes applications.
     *
     * <p>Any master of the table may do it, not only its Primary. It is idempotent, and it sends no
     * notification. It switches itself off when the table fills up - by accepting a candidate, by
     * lifting a veto, or by an admin lowering the capacity to the players it already has - and
     * whenever the table leaves {@code InProgress}; resuming a paused table does not switch it back on.
     *
     * @param gameTableId the table
     * @param request     whether to look for players
     * @param actorId     the actor, from the token; has to run the table
     * @return the table, with the search in its new state
     * @throws NotFoundException        if the table does not exist, or the actor cannot see it - the
     *                                  same answer as a table that does not exist
     * @throws ForbiddenActionException if the actor sees the table but does not run it
     * @throws ConflictException        if the table is not {@code InProgress}, or 409
     *                                  {@code TABLE_FULL} when turning it on with every seat taken
     */
    @Transactional
    public GameTableDetailResponse setSeekingPlayers(String gameTableId, SetSeekingPlayersRequest request, String actorId) {
        GameTable gameTable = lockTable(gameTableId);
        if (!masterService.isMasterOf(gameTableId, actorId)) {
            // Said only about a table they may see: to anybody else it is the same 404 as an id nobody used.
            tableVisibilityService.requireVisible(gameTableId, actorId);
            throw new ForbiddenActionException("Only a master of this table can look for players");
        }
        if (gameTable.getStatus() != GameTableStatus.InProgress) {
            throw new ConflictException("Cannot look for players on a table in status " + gameTable.getStatus());
        }
        boolean seeking = request.seeking();
        Integer maxPlayers = gameTable.getMaxPlayers();
        if (seeking && maxPlayers != null && countPlayers(gameTableId) >= maxPlayers) {
            throw new ConflictException("Table " + gameTableId + " has no free seat", ConflictException.TABLE_FULL);
        }
        gameTable.setSeekingPlayers(seeking);
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
     * @throws NotFoundException if the table is not there or was deleted - for an admin too - and,
     *         for anybody who does not oversee it, whenever the table itself would answer 404 to them
     * @throws ForbiddenActionException if the actor sees the table but neither runs it nor is an
     *         admin
     */
    @Transactional(readOnly = true)
    public List<TableStatusChangeResponse> getStatusHistory(String gameTableId, String actorId) {
        if (!masterService.canOversee(gameTableId, actorId)) {
            // The same 404 the table's own detail gives them - deleted, vetoed or never public - so the
            // 403 below is only ever said about a table they already know exists.
            tableVisibilityService.requireVisible(gameTableId, actorId);
            throw new ForbiddenActionException("Only a master of this table or an admin can view its status history");
        }
        // A deleted or missing table has no history to read, for an admin either.
        tableVisibilityService.requireExisting(gameTableId);
        return tableStatusChangeRepository.findByGameTable_IdOrderByCreatedAtAsc(gameTableId).stream()
                .map(gameTableMapper::toStatusChangeResponse)
                .toList();
    }

    /**
     * Adds a co-master or promotes one to Primary, and answers with the table's masters afterwards -
     * which is what the section on screen re-renders.
     *
     * @param gameTableId the table
     * @param actorId     the actor, from the token; {@code MasterService} checks their membership
     * @param request     who to add or promote, and as what
     * @return every master of the table after the change
     * @throws NotFoundException if the table does not exist, or the actor cannot see it - the same
     *         answer as a table that does not exist
     * @throws ForbiddenActionException if the actor sees the table but is not its Primary
     */
    @Transactional
    public List<MasterSummaryResponse> addOrPromoteMaster(String gameTableId, String actorId, AddMasterRequest request) {
        GameTable gameTable = getEntityById(gameTableId);
        // Asked *before* the change, so it can tell being added apart from being promoted: the
        // first is news - a table this person did not run until now - and the second is not, because
        // they were already running it and are watching the screen that did it.
        boolean joins = !masterService.isMasterOf(gameTableId, request.userId());
        try {
            masterService.addOrPromote(gameTable, actorId, request.userId(), request.masterType());
        } catch (ForbiddenActionException refused) {
            throw refusedOnMasters(gameTableId, actorId, refused);
        }
        if (joins) {
            // After addOrPromote checked membership, so an outsider learns nothing about anybody's week.
            // A clash rolls the addition back with the rest of the transaction.
            tableScheduleService.requireNoClash(
                    List.of(userService.getById(request.userId())),
                    gameTableId,
                    scheduleConflictService.intervalsOf(gameTable),
                    actorId);
            notificationService.notifyMasterAssigned(request.userId(), gameTable);
        }
        return masterService.findByGameTable(gameTableId).stream().map(gameTableMapper::toMasterSummary).toList();
    }

    /**
     * Removes a co-master, and answers with the table's masters afterwards - the same shape
     * {@link #addOrPromoteMaster} returns, because the section on screen re-renders from it.
     *
     * @param gameTableId  the table
     * @param actorId      the actor, from the token; {@code MasterService} checks their membership
     * @param targetUserId who to remove
     * @return every master of the table after the change
     * @throws NotFoundException if the table does not exist, or the actor cannot see it - the same
     *         answer as a table that does not exist
     * @throws ForbiddenActionException if the actor sees the table but is not its Primary
     */
    @Transactional
    public List<MasterSummaryResponse> removeMaster(String gameTableId, String actorId, String targetUserId) {
        GameTable gameTable = getEntityById(gameTableId);
        try {
            masterService.removeMaster(gameTable, actorId, targetUserId);
        } catch (ForbiddenActionException refused) {
            throw refusedOnMasters(gameTableId, actorId, refused);
        }
        return masterService.findByGameTable(gameTableId).stream().map(gameTableMapper::toMasterSummary).toList();
    }

    /**
     * The people search behind adding a co-master, answered only for a table's own Primary.
     *
     * <p>The scope is the whole point: the admin directory stays closed, and what opens is a
     * search that only the person who may actually add a master can run. The check is membership and
     * not a role, because a Primary an admin assigned may never have been given {@code Master} -
     * and the actor comes from the token, never from the URL.
     *
     * @param gameTableId the table the search is for
     * @param actorId     the actor, from the token
     * @param query       what was typed. A missing one, or one shorter than the directory search's
     *                    minimum, finds nobody
     * @param pageable    only the size counts, and it is capped: the page number and the sort are the
     *                    server's own, so the directory cannot be walked
     * @return the first page of at most the directory search's cap of people who match, or an empty
     *         page for a query too short to search with
     * @throws NotFoundException        if the table does not exist, or the actor cannot see it - the
     *                                  same answer as a table that does not exist
     * @throws ForbiddenActionException if the actor sees the table but is not its Primary
     */
    @Transactional(readOnly = true)
    public PageResponse<UserSummaryResponse> searchMasterCandidates(
            String gameTableId, String actorId, @Nullable String query, Pageable pageable) {
        getEntityById(gameTableId);
        if (!masterService.isPrimaryOf(gameTableId, actorId)) {
            // Said only about a table they may see: to anybody else it is the same 404 as an id nobody used.
            tableVisibilityService.requireVisible(gameTableId, actorId);
            throw new ForbiddenActionException("Only the table's master can search for co-masters");
        }
        return userService.search(query, pageable);
    }

    /**
     * The public explorer, narrowed by what the reader typed.
     *
     * <p>Excludes tables the actor masters: a master cannot browse their own table to apply as
     * a Player at it. It is also the one listing that carries the schedule clash warning, computed for
     * the actor of the token: this is the screen where the question "can I actually take this on?" is
     * asked.
     *
     * <p><b>The catalog criteria are resolved before the query is built</b>, not inside it: a search
     * for {@code D&D} has to find the tables tagged {@code DANDD} too, and that expansion is a read of
     * its own. The search only ever narrows what the visibility rules already allowed -
     * see {@code GameTableSearchSpecification} for why the two are joined and never folded together.
     *
     * @param query    the raw search box, or null when it is empty
     * @param pageable page, size and sort
     * @param actorId  the actor, from the token - never from the URL
     * @return one page of the tables the actor could apply to
     */
    @Transactional(readOnly = true)
    public PageResponse<GameTableSummaryResponse> list(@Nullable String query, Pageable pageable, String actorId) {
        SearchQuery parsed = SearchQueryParser.parse(query, GameTableSearchField.wireNames());
        Map<SearchTerm, Set<String>> catalogIds = gameTableSearchResolver.resolveCatalogTerms(parsed);
        Page<GameTable> page = gameTableRepository.findAll(
                GameTableSearchSpecification.forExplorer(parsed, catalogIds, actorId), pageable);
        return toSummaryPage(page, actorId);
    }

    /**
     * The public detail of a table, for /tables/:id.
     *
     * <p>It carries the schedule clash flag computed for the actor: the apply button on that screen
     * has to be able to say <em>why</em> it is disabled, and the apply clash is the one reason the
     * reader can do something about.
     *
     * <p><b>The veto is applied here and nowhere below it.</b> The gate is
     * {@link TableVisibilityService#requireVisible}, which answers {@code 404} - never {@code 403},
     * because a 403 confirms exactly what the 404 denies. The public sessions and the shared files
     * ride along inside this answer precisely so that they inherit the one decision instead of
     * repeating it somewhere it could drift (see {@link #toDetail}).
     *
     * @param gameTableId the table
     * @param actorId     the actor, from the token - never from the URL
     * @return its detail
     * @throws com.centraldungeon.common.exception.NotFoundException if it does not exist, was
     *         deleted, or the actor is vetoed on it - the same 404 for all three
     */
    @Transactional(readOnly = true)
    public GameTableDetailResponse getDetail(String gameTableId, String actorId) {
        GameTable gameTable = tableVisibilityService.requireVisible(gameTableId, actorId);
        // Somebody outside the table sees only accepted catalog values; its masters and the admins read
        // them all, with their status, so that the next save keeps what is not accepted yet.
        Map<CatalogType, List<CatalogValueResponse>> catalogs = masterService.canOversee(gameTableId, actorId)
                ? tableCatalogService.findLinks(gameTableId)
                : tableCatalogService.findAcceptedLinks(gameTableId);
        return toDetail(gameTable, actorId, catalogs);
    }

    /**
     * /master/tables/:id - a different endpoint from getDetail on purpose:
     * getDetail is deliberately public (any player reads it to decide whether to apply), so
     * reusing it here would mean the full table body travels over the network before the
     * frontend ever gets to decide whether to render it. This one checks membership first and
     * never touches the mapper if the actor is neither a master of this table nor an admin - the
     * admin reads every table whole, from /admin/tables/:id.
     *
     * @param gameTableId the table
     * @param actorId     the actor, from the token
     * @return the table as the people running it see it
     * @throws NotFoundException        if the table does not exist, or the actor cannot see it - the
     *                                  same answer as a table that does not exist
     * @throws ForbiddenActionException if the actor sees the table but neither runs it nor is an admin
     */
    @Transactional(readOnly = true)
    public GameTableDetailResponse getManagedDetail(String gameTableId, String actorId) {
        GameTable gameTable = getEntityById(gameTableId);
        // A master of the table, or an admin: the admin reads every table whole.
        if (!masterService.canOversee(gameTableId, actorId)) {
            // Said only about a table they may see: to anybody else it is the same 404 as an id nobody used.
            tableVisibilityService.requireVisible(gameTableId, actorId);
            throw new ForbiddenActionException("Only a master of this table or an admin can view its management detail");
        }
        return toDetail(gameTable);
    }

    /**
     * /my/tables: only the tables where the actor currently holds an active Player registration,
     * <b>and only while the table itself is still live</b>.
     *
     * <p>The registration's own status never moves once somebody is accepted - it stays
     * {@code Player} whether the table is still running, was finished, or was cancelled. Filtering
     * by it alone, as this used to, left a table sitting among "mine" forever after it closed. What
     * decides "still mine to see here" is the table's own status; a closed run moves to
     * {@link #listMineHistory} instead. {@code Pause} counts as live - the table is frozen, not
     * over.
     *
     * @param actorId  the actor, from the token
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
     * {@code Canceled} - with when each one closed and the actor's own final attendance.
     *
     * <p><b>A different shape from {@link #listMine}, not the same read with another filter</b>: a
     * closed table's card has no seat count to show and cannot clash with anything the actor is
     * committed to, and it carries two things a live card never needs - {@code closed_at} and the
     * historical attendance aggregate. See {@link GameTableHistoryResponse} for the exact fields.
     *
     * <p>The attendance is resolved for the whole page in one grouped query
     * ({@link TableSessionService#summarizeByTables}), never one {@code summarize} call per row -
     * the same N+1 {@code CatalogUsageCount} and {@code FileService.usagesByFileId} already avoid.
     *
     * <p>Whether the actor left a comment on the table - is not here:
     * comments do not exist yet, and there is nowhere to read that from.
     *
     * @param actorId  the actor, from the token
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
     * /admin/tables: the management listing, unfiltered by membership - the caller is already an
     * admin.
     *
     * <p><b>Every table, not only the ones waiting on a review.</b> That default moved out:
     * reviewing is the shared tray's job now ({@code /admin/queue}), and what this screen is for is
     * finding a table - any table - and acting on it. {@code Deleted} is the one status that is never
     * listed even when it is asked for, because the filter belongs to the listing and not to the
     * caller.
     *
     * <p><b>It takes the same {@code ?q=} the explorer does</b>, with two
     * commands the explorer has no use for: {@code /table_status} over the closed list of states, and
     * {@code /table_master} over the masters' names. The catalog criteria are resolved to ids before
     * the query is built, exactly as {@link #list} does and for the same reason.
     *
     * <p><b>The page is built with three queries and not with three per row</b>:
     * one for the tables, one for their Primaries and one for their player counts. Asking per row was
     * survivable while the default was a handful of tables in review; listing every table there is
     * turned it into a query storm.
     *
     * @param rawQuery the search box, or null when it is empty. An unrecognized value matches nothing
     *                 and is never a 400
     * @param statuses which statuses to list, or null/empty for all of them but {@code Deleted}
     * @param pageable page, size and sort, with a tie-break by id
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
     * four lines. The claim is now true because the lookup lives in
     * {@link TableVisibilityService}, which is where the veto is written once; this
     * method is a name the callers inside this class already use, and nothing more.
     *
     * <p>Every caller left is a master's or an admin's operation on a table they run, where
     * membership is checked on the next line and being vetoed is not a question that can be asked.
     * The read a <em>player</em> performs is {@link #getDetail}, and it goes through
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
        applySections(gameTable, sanitize(request.sections()));
        gameTable.setStartDate(request.startDate());
        gameTable.setTotalSessions(request.totalSessions());
        gameTable.setMaxPlayers(requireWithinPlayerCap(request.maxPlayers()));
        gameTable.setTableType(resolveTableType(request.tableTypeId()));
        return gameTable;
    }

    /**
     * The platform-wide ceiling on {@code max_players}.
     *
     * <p>Written here and not as a {@code @Max} on the two request records, for the reason the whole
     * table is configurable: an annotation is a constant, and the number has to be changeable without a
     * deploy. Both ways in pass through this - {@link #buildTable} covers {@code create} and
     * {@code createUnassigned}, {@code update} calls it directly - so there is no third door.
     *
     * <p>A table with no cap at all stays legal: {@code null} means "no cap" and is the
     * absence of a number, not a number above the ceiling.
     *
     * @param maxPlayers what the request asked for, or null for no cap
     * @return the same value, once it is known to be allowed
     * @throws InvalidRequestException 400 {@code MAX_PLAYERS_ABOVE_CAP}, carrying the cap, because
     *                                 "too many" without the number tells nobody what to type
     */
    private @Nullable Integer requireWithinPlayerCap(@Nullable Integer maxPlayers) {
        if (maxPlayers == null) {
            return null;
        }
        int cap = settingsService.maxPlayersCap();
        if (maxPlayers > cap) {
            throw new InvalidRequestException(
                    "max_players of " + maxPlayers + " is above the platform cap of " + cap,
                    "MAX_PLAYERS_ABOVE_CAP",
                    Map.of("maxPlayersCap", String.valueOf(cap)));
        }
        return maxPlayers;
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

    /** Sets the three catalogs of a table in one step - each of them a full replacement. */
    private void applyCatalogs(
            String gameTableId, @Nullable List<String> systemIds, @Nullable List<String> tagIds, @Nullable List<String> platformIds) {
        tableCatalogService.replaceLinks(gameTableId, CatalogType.SYSTEMS, orEmpty(systemIds));
        tableCatalogService.replaceLinks(gameTableId, CatalogType.TAGS, orEmpty(tagIds));
        tableCatalogService.replaceLinks(gameTableId, CatalogType.PLATFORMS, orEmpty(platformIds));
    }

    /**
     * Stamps the closing instant, once. A table closes one time: if the column already
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

    /**
     * The refusal {@code MasterService} gave under its lock, passed on only to somebody who may see the
     * table. Asked after the refusal and not before the call: reading the masters ahead of the lock
     * would leave a stale row in the persistence context for the locked read to hand back.
     */
    private ForbiddenActionException refusedOnMasters(String gameTableId, String actorId, ForbiddenActionException refused) {
        tableVisibilityService.requireVisible(gameTableId, actorId);
        return refused;
    }

    /** The Primary's own transitions: 404 for whoever may not see the table, 403 for whoever sees it and is not its Primary. */
    private void requirePrimaryOf(String gameTableId, String actorId, String action) {
        if (!masterService.isPrimaryOf(gameTableId, actorId)) {
            tableVisibilityService.requireVisible(gameTableId, actorId);
            throw new ForbiddenActionException("Only the Primary master can " + action + " this table");
        }
    }

    private boolean isAdmin(String actorId) {
        Set<String> roles = userService.loadAuthSnapshot(actorId).roles();
        return roles.contains(PlatformRole.ADMIN.roleName()) || roles.contains(PlatformRole.OWNER.roleName());
    }

    /** Every real transition goes through here: it is the one place that flips status and leaves its trail. */
    private void recordStatusChange(
            GameTable gameTable, GameTableStatus from, GameTableStatus to, String actorId, @Nullable String justification) {
        User changedBy = userService.getById(actorId);
        gameTable.setStatus(to);
        // The search belongs to one stay in InProgress: leaving it for any reason ends it, and resuming does not bring it back.
        if (from == GameTableStatus.InProgress) {
            gameTable.setSeekingPlayers(false);
        }
        tableStatusChangeRepository.save(new TableStatusChange(gameTable, from, to, changedBy, justification));
    }

    /**
     * One page of cards, with the agendas read in a single query and - when the listing is one where
     * the question makes sense - the schedule clash warning and the "already applied" mark,
     * both computed for the actor of the token, each in one query for the whole page.
     *
     * <p>{@code /my/tables} and {@code /master/tables} pass {@code null}: a table you already run or
     * play at is the commitment, so warning that it clashes with itself would be noise.
     */
    private PageResponse<GameTableSummaryResponse> toSummaryPage(Page<GameTable> page, @Nullable String conflictActorId) {
        List<GameTable> tables = page.getContent();
        List<String> ids = tables.stream().map(GameTable::getId).toList();
        Map<String, List<TableScheduleEntry>> schedules = tableScheduleService.findByTables(ids);
        Set<String> clashing = conflictActorId == null ? Set.of() : scheduleConflictService.clashingAmong(conflictActorId, tables);
        Set<String> applied = conflictActorId == null || ids.isEmpty()
                ? Set.of()
                : Set.copyOf(tableRegistrationRepository.findTableIdsWithRegistration(conflictActorId, ids, ACTIVE_REGISTRATION_STATUSES));
        Set<String> joined = conflictActorId == null || ids.isEmpty()
                ? Set.of()
                : Set.copyOf(tableRegistrationRepository.findTableIdsWithRegistration(
                        conflictActorId, ids, List.of(TableRegistrationStatus.Player)));

        return PageResponse.from(page.map(gameTable -> gameTableMapper.toSummary(
                gameTable,
                countPlayers(gameTable.getId()),
                findPrimaryMaster(gameTable.getId()),
                schedules.getOrDefault(gameTable.getId(), List.of()),
                clashing.contains(gameTable.getId()),
                applied.contains(gameTable.getId()),
                joined.contains(gameTable.getId()))));
    }

    /**
     * The four things a table cannot be run without: what is played, how it is labelled, where, and when.
     *
     * <p>Requiring a calendar to open is a validation of the create request and not a silent effect of
     * approving, so it lands here.
     *
     * <p>Tags are required too: requiring them risks filler labels, but a table nobody can find by
     * subject is the worse of the two problems.
     *
     * <p>It does not apply to {@link #createUnassigned}: that one is an admin's stub, deliberately
     * created with a name and nothing else, for a master to fill in later.
     *
     * @throws InvalidRequestException naming which of the three is missing, so the frontend can say
     *                                 which field to go fix rather than "invalid"
     */
    private void requireRunnableDraft(
            @Nullable List<?> systemIds,
            @Nullable List<?> tagIds,
            @Nullable List<?> platformIds,
            @Nullable List<?> schedule) {
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
     * The same four requirements as {@link #requireRunnableDraft}, read from what the table has stored
     * rather than from a request: what sending it to review checks, since a draft can be born with a
     * name and nothing else when an admin approves a request to open a table.
     */
    private void requireRunnableAsStored(String gameTableId) {
        Map<CatalogType, List<CatalogValueResponse>> links = tableCatalogService.findLinks(gameTableId);
        requireRunnableDraft(
                links.get(CatalogType.SYSTEMS),
                links.get(CatalogType.TAGS),
                links.get(CatalogType.PLATFORMS),
                tableScheduleService.findByTable(gameTableId));
    }

    /**
     * One page of the admin listing, with the two expensive per-row reads done once for the whole page.
     *
     * <p>This is the N+1 the project forbids. The old {@code toAdminSummary} asked
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
     * @param primary     the table's live Primary, or null for an {@code Unassigned} one
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
                // A reserved table reads the same way a reserved request does, so an admin
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
        return toDetail(gameTable, null, tableCatalogService.findLinks(gameTable.getId()));
    }

    /**
     * @param conflictActorId whose commitments to measure the agenda against and whose application to look
     *                        for, or null when the read has no actor for whom the questions mean anything
     * @param catalogs        the catalog values to show, already narrowed to what this reader may see
     */
    private GameTableDetailResponse toDetail(
            GameTable gameTable, @Nullable String conflictActorId, Map<CatalogType, List<CatalogValueResponse>> catalogs) {
        int playerCount = countPlayers(gameTable.getId());
        List<MasterSummaryResponse> masters =
                masterService.findByGameTable(gameTable.getId()).stream().map(gameTableMapper::toMasterSummary).toList();

        // Sanitized on the way out as well as on the way in: a row written before this gate
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
                // once this read has already established the reader may see the table at all.
                tableFileService.sharedFilesOf(gameTable.getId()),
                richTextSanitizer.sanitize(gameTable.getDescription()),
                richTextSanitizer.sanitize(gameTable.getPermitted()),
                richTextSanitizer.sanitize(gameTable.getRequirements()),
                sanitize(new TableSections(
                        gameTable.getCharacterCreation(), gameTable.getWhatToExpect(),
                        gameTable.getEquipment(), gameTable.getSafety())),
                conflictActorId != null && scheduleConflictService.findClashWith(conflictActorId, gameTable) != null,
                conflictActorId != null && tableRegistrationRepository.existsByGameTable_IdAndUser_IdAndStatusIn(
                        gameTable.getId(), conflictActorId, ACTIVE_REGISTRATION_STATUSES),
                conflictActorId != null && tableRegistrationRepository.existsByGameTable_IdAndUser_IdAndStatusIn(
                        gameTable.getId(), conflictActorId, List.of(TableRegistrationStatus.Player)),
                conflictActorId == null ? null : registrationService.findMyRejection(gameTable.getId(), conflictActorId));
    }

    /**
     * The optional sections with each one sanitized, the way the description is. Absent means none
     * was written.
     */
    private TableSections sanitize(@Nullable TableSections sections) {
        if (sections == null) {
            return TableSections.EMPTY;
        }
        return new TableSections(
                richTextSanitizer.sanitize(sections.characterCreation()),
                richTextSanitizer.sanitize(sections.whatToExpect()),
                richTextSanitizer.sanitize(sections.equipment()),
                richTextSanitizer.sanitize(sections.safety()));
    }

    /** Writes the four sections, already sanitized, onto the table. */
    private static void applySections(GameTable gameTable, TableSections sections) {
        gameTable.setCharacterCreation(sections.characterCreation());
        gameTable.setWhatToExpect(sections.whatToExpect());
        gameTable.setEquipment(sections.equipment());
        gameTable.setSafety(sections.safety());
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

    /** The row every transition locks on; a deleted table is not there, so it is a 404 and never a "wrong status" 409. */
    private GameTable lockTable(String gameTableId) {
        return gameTableRepository.findByIdForUpdate(gameTableId)
                .filter(gameTable -> gameTable.getStatus() != GameTableStatus.Deleted)
                .orElseThrow(() -> new NotFoundException("Table not found: " + gameTableId));
    }
}
