package com.centraldungeon.tables;

import com.centraldungeon.catalogs.CatalogType;
import com.centraldungeon.catalogs.TableCatalogService;
import com.centraldungeon.catalogs.dto.CatalogValueResponse;
import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.exception.ForbiddenActionException;
import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.common.text.RichTextSanitizer;
import com.centraldungeon.files.TableFileService;
import com.centraldungeon.registrations.TableRegistration;
import com.centraldungeon.registrations.TableRegistrationRepository;
import com.centraldungeon.registrations.TableRegistrationStatus;
import com.centraldungeon.tables.dto.AddMasterRequest;
import com.centraldungeon.tables.dto.AdminTableSummaryResponse;
import com.centraldungeon.tables.dto.AssignMastersRequest;
import com.centraldungeon.tables.dto.ChangeTableStatusRequest;
import com.centraldungeon.tables.dto.CreateGameTableRequest;
import com.centraldungeon.tables.dto.GameTableDetailResponse;
import com.centraldungeon.tables.dto.GameTableSummaryResponse;
import com.centraldungeon.tables.dto.MasterSummaryResponse;
import com.centraldungeon.tables.dto.TableScheduleEntry;
import com.centraldungeon.tables.dto.TableStatusChangeResponse;
import com.centraldungeon.tables.dto.UpdateGameTableRequest;
import com.centraldungeon.users.PlatformRole;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /** The /admin/tables default until the queue screen exists: the tables waiting on an admin (#176, F3). */
    private static final List<GameTableStatus> DEFAULT_ADMIN_REVIEW_STATUSES =
            List.of(GameTableStatus.Unassigned, GameTableStatus.Preparation, GameTableStatus.ChangesRequested);

    /**
     * A table can be deleted only while it was never public (decisiones.md #175): nobody saw it, so
     * there is no history worth keeping. Everything past this point is cancelled instead, because a
     * table other people looked at, applied to or played is a record of something that happened.
     */
    private static final Set<GameTableStatus> DELETABLE_STATUSES =
            Set.of(GameTableStatus.Unassigned, GameTableStatus.Preparation, GameTableStatus.ChangesRequested);

    /**
     * The statuses in which a table is still being written, and therefore still its master's to
     * rewrite. Past this point people have applied on the strength of what it says, and changing it
     * under them is a different conversation than editing a draft.
     */
    private static final Set<GameTableStatus> EDITABLE_STATUSES =
            Set.of(GameTableStatus.Preparation, GameTableStatus.ChangesRequested);

    private static final List<TableRegistrationStatus> ACTIVE_REGISTRATION_STATUSES =
            List.of(TableRegistrationStatus.Candidate, TableRegistrationStatus.Player);

    /** cancel() is the one transition with more than one valid "from" (docs/decisiones.md, the table's life cycle). */
    private static final Set<GameTableStatus> CANCELABLE_STATUSES = Set.of(
            GameTableStatus.Unassigned, GameTableStatus.Preparation, GameTableStatus.ChangesRequested,
            GameTableStatus.Opened, GameTableStatus.InProgress, GameTableStatus.Pause);

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
            RichTextSanitizer richTextSanitizer) {
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
    }

    /** The creator becomes the table's Primary master (#73); a Master row is the source of pertenencia, not the role alone (#135). */
    @Transactional
    public GameTableDetailResponse create(CreateGameTableRequest request, String creatorId) {
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

        gameTable.setName(request.name());
        gameTable.setDescription(richTextSanitizer.sanitize(request.description()));
        gameTable.setPermitted(richTextSanitizer.sanitize(request.permitted()));
        gameTable.setRequirements(richTextSanitizer.sanitize(request.requirements()));
        gameTable.setStartDate(request.startDate());
        gameTable.setDuration(request.duration());
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
     * @param gameTableId the table to approve
     * @param actorId     the admin, from the token; recorded in the status history
     * @return the table, now Opened, with its sessions materialized
     * @throws ConflictException if the table was not awaiting review
     */
    @Transactional
    public GameTableDetailResponse approve(String gameTableId, String actorId) {
        GameTable gameTable = lockTable(gameTableId);
        if (gameTable.getStatus() != GameTableStatus.Preparation) {
            throw new ConflictException("Cannot approve a table in status " + gameTable.getStatus());
        }
        recordStatusChange(gameTable, GameTableStatus.Preparation, GameTableStatus.Opened, actorId, null);
        tableSessionService.materialize(gameTable);
        return toDetail(gameTable);
    }

    /**
     * An admin sending a draft back to its master, with a reason. Preparation to ChangesRequested.
     *
     * @param gameTableId the table
     * @param actorId     the admin, from the token
     * @param request     the justification, which the master reads on the status tab
     * @return the table after the change
     * @throws ConflictException if the table was not awaiting review
     */
    @Transactional
    public GameTableDetailResponse requestChanges(String gameTableId, String actorId, ChangeTableStatusRequest request) {
        GameTable gameTable = lockTable(gameTableId);
        if (gameTable.getStatus() != GameTableStatus.Preparation) {
            throw new ConflictException("Cannot request changes on a table in status " + gameTable.getStatus());
        }
        recordStatusChange(gameTable, GameTableStatus.Preparation, GameTableStatus.ChangesRequested, actorId, request.justification());
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
        masterService.addOrPromote(gameTable, actorId, request.userId(), request.masterType());
        return masterService.findByGameTable(gameTableId).stream().map(gameTableMapper::toMasterSummary).toList();
    }

    /**
     * Excludes tables the actor masters (#154): a master cannot browse their own table to apply as a
     * Player at it.
     *
     * <p>It is also the one listing that carries the clash warning of #178, computed for the actor
     * of the token: this is the screen where the question "can I actually take this on?" is asked.
     */
    @Transactional(readOnly = true)
    public PageResponse<GameTableSummaryResponse> list(Pageable pageable, String actorId) {
        Page<GameTable> page = gameTableRepository.findByStatusInAndNotMasteredByActor(VISIBLE_STATUSES, actorId, pageable);
        return toSummaryPage(page, actorId);
    }

    /**
     * The public detail of a table, for /tables/:id.
     *
     * <p>It carries the clash flag of #178 computed for the actor: the apply button on that screen
     * has to be able to say <em>why</em> it is disabled, and R2 is the one reason the reader can do
     * something about (principio 2 de frontend-diseno.md 1).
     *
     * @param gameTableId the table
     * @param actorId     the actor, from the token - never from the URL (#121)
     * @return its detail
     * @throws com.centraldungeon.common.exception.NotFoundException if it does not exist
     */
    @Transactional(readOnly = true)
    public GameTableDetailResponse getDetail(String gameTableId, String actorId) {
        return toDetail(getEntityById(gameTableId), actorId);
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

    /** /my/tables: only the tables where the actor currently holds an active Player registration. */
    @Transactional(readOnly = true)
    public PageResponse<GameTableSummaryResponse> listMine(String actorId, Pageable pageable) {
        Page<TableRegistration> page = tableRegistrationRepository.findByUser_IdAndStatus(actorId, TableRegistrationStatus.Player, pageable);
        return toSummaryPage(page.map(TableRegistration::getGameTable), null);
    }

    /** /master/tables: every status, including Preparation - a master needs to see and open their own drafts. */
    @Transactional(readOnly = true)
    public PageResponse<GameTableSummaryResponse> listManaged(String actorId, Pageable pageable) {
        Page<GameTable> page = gameTableRepository.findByMasterUserId(actorId, pageable);
        return toSummaryPage(page, null);
    }

    /**
     * /admin/tables: unfiltered by pertenencia, defaults to the statuses waiting on an admin.
     * Unassigned tables have no Primary yet, so this uses toAdminSummary, never toSummary.
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminTableSummaryResponse> listForAdmin(@Nullable List<GameTableStatus> statuses, Pageable pageable) {
        // Deleted is never listed even when asked for: the filter belongs to the listing, not to the
        // caller (#25).
        List<GameTableStatus> effective = (statuses == null || statuses.isEmpty())
                ? DEFAULT_ADMIN_REVIEW_STATUSES
                : statuses.stream().filter(status -> status != GameTableStatus.Deleted).toList();
        Page<GameTable> page = gameTableRepository.findByStatusIn(effective, pageable);
        return PageResponse.from(page.map(this::toAdminSummary));
    }

    /** Internal read used by other services (e.g. registrations) - never exposed raw over HTTP (arquitectura.md 2.2/2.3). */
    @Transactional(readOnly = true)
    /**
     * The single lookup every read goes through, so it is the single place where a soft-deleted
     * table stops existing (#25, #175): 404 and not 403, because "it was deleted" is not something
     * a caller is entitled to learn.
     */
    public GameTable getEntityById(String gameTableId) {
        GameTable gameTable =
                gameTableRepository.findById(gameTableId).orElseThrow(() -> new NotFoundException("Table not found: " + gameTableId));
        if (gameTable.getStatus() == GameTableStatus.Deleted) {
            throw new NotFoundException("Table not found: " + gameTableId);
        }
        return gameTable;
    }

    private GameTable buildTable(CreateGameTableRequest request, User creator) {
        GameTable gameTable = new GameTable(request.name(), creator);
        gameTable.setDescription(richTextSanitizer.sanitize(request.description()));
        gameTable.setPermitted(richTextSanitizer.sanitize(request.permitted()));
        gameTable.setRequirements(richTextSanitizer.sanitize(request.requirements()));
        gameTable.setStartDate(request.startDate());
        gameTable.setDuration(request.duration());
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

    private AdminTableSummaryResponse toAdminSummary(GameTable gameTable) {
        MasterSummaryResponse primaryMaster = findPrimaryMasterOrNull(gameTable.getId());
        return new AdminTableSummaryResponse(
                gameTable.getId(),
                gameTable.getName(),
                gameTable.getStatus().name(),
                gameTable.getTableType() != null ? gameTable.getTableType().getName() : null,
                gameTable.getMaxPlayers(),
                countPlayers(gameTable.getId()),
                primaryMaster != null ? primaryMaster.name() : null,
                gameTable.getCreatedAt());
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
