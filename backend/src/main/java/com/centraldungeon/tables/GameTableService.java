package com.centraldungeon.tables;

import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.exception.ForbiddenActionException;
import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.common.model.PageResponse;
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
import com.centraldungeon.tables.dto.TableStatusChangeResponse;
import com.centraldungeon.users.PlatformRole;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserService;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GameTableService {

    private static final List<GameTableStatus> VISIBLE_STATUSES = List.of(GameTableStatus.Opened, GameTableStatus.InProgress);

    /** /admin/tables default view: mesas esperando alguna acción del admin (plan-desarrollo.md E2). */
    private static final List<GameTableStatus> DEFAULT_ADMIN_REVIEW_STATUSES =
            List.of(GameTableStatus.Unassigned, GameTableStatus.Preparation, GameTableStatus.ChangesRequested);

    /** cancel() is the one transition with more than one valid "from" (docs/decisiones.md, Ciclo de vida de la mesa). */
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

    public GameTableService(
            GameTableRepository gameTableRepository,
            TableTypeRepository tableTypeRepository,
            TableRegistrationRepository tableRegistrationRepository,
            TableStatusChangeRepository tableStatusChangeRepository,
            MasterService masterService,
            GameTableMapper gameTableMapper,
            UserService userService) {
        this.gameTableRepository = gameTableRepository;
        this.tableTypeRepository = tableTypeRepository;
        this.tableRegistrationRepository = tableRegistrationRepository;
        this.tableStatusChangeRepository = tableStatusChangeRepository;
        this.masterService = masterService;
        this.gameTableMapper = gameTableMapper;
        this.userService = userService;
    }

    /** The creator becomes the table's Primary master (#73); a Master row is the source of pertenencia, not the role alone (#135). */
    @Transactional
    public GameTableDetailResponse create(CreateGameTableRequest request, String creatorId) {
        User creator = userService.getById(creatorId);
        GameTable gameTable = buildTable(request, creator);

        gameTable = gameTableRepository.save(gameTable);
        masterService.createPrimary(gameTable, creator);

        return toDetail(gameTable);
    }

    /**
     * A mesa an admin creates without being its master (#72): it nests directly in Unassigned, no
     * Primary yet. assignInitialMasters is what moves it to Opened.
     */
    @Transactional
    public GameTableDetailResponse createUnassigned(CreateGameTableRequest request, String actorId) {
        User creator = userService.getById(actorId);
        GameTable gameTable = buildTable(request, creator);
        gameTable.setStatus(GameTableStatus.Unassigned);

        gameTable = gameTableRepository.save(gameTable);
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
        masterService.assignInitialMasters(gameTable, request.primaryUserId(), secondaries);
        recordStatusChange(gameTable, GameTableStatus.Unassigned, GameTableStatus.Opened, actorId, null);
        return toDetail(gameTable);
    }

    /** An admin approving a master's draft (#27) - the only way Preparation reaches Opened now. */
    @Transactional
    public GameTableDetailResponse approve(String gameTableId, String actorId) {
        GameTable gameTable = lockTable(gameTableId);
        if (gameTable.getStatus() != GameTableStatus.Preparation) {
            throw new ConflictException("Cannot approve a table in status " + gameTable.getStatus());
        }
        recordStatusChange(gameTable, GameTableStatus.Preparation, GameTableStatus.Opened, actorId, null);
        return toDetail(gameTable);
    }

    @Transactional
    public GameTableDetailResponse requestChanges(String gameTableId, String actorId, ChangeTableStatusRequest request) {
        GameTable gameTable = lockTable(gameTableId);
        if (gameTable.getStatus() != GameTableStatus.Preparation) {
            throw new ConflictException("Cannot request changes on a table in status " + gameTable.getStatus());
        }
        recordStatusChange(gameTable, GameTableStatus.Preparation, GameTableStatus.ChangesRequested, actorId, request.justification());
        return toDetail(gameTable);
    }

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

    @Transactional
    public GameTableDetailResponse finish(String gameTableId, String actorId) {
        GameTable gameTable = lockTable(gameTableId);
        requirePrimaryOf(gameTableId, actorId, "finish");
        if (gameTable.getStatus() != GameTableStatus.InProgress) {
            throw new ConflictException("Cannot finish a table in status " + gameTable.getStatus());
        }
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
        recordStatusChange(gameTable, from, GameTableStatus.Canceled, actorId, request.justification());
        return toDetail(gameTable);
    }

    /** Immediate pause by an admin (#32) - a master asking for one goes through approval_requests instead (E2 sub-rebanada 2). */
    @Transactional
    public GameTableDetailResponse pauseDirect(String gameTableId, String actorId, ChangeTableStatusRequest request) {
        GameTable gameTable = lockTable(gameTableId);
        if (gameTable.getStatus() != GameTableStatus.InProgress) {
            throw new ConflictException("Cannot pause a table in status " + gameTable.getStatus());
        }
        recordStatusChange(gameTable, GameTableStatus.InProgress, GameTableStatus.Pause, actorId, request.justification());
        return toDetail(gameTable);
    }

    @Transactional
    public GameTableDetailResponse resume(String gameTableId, String actorId) {
        GameTable gameTable = lockTable(gameTableId);
        if (gameTable.getStatus() != GameTableStatus.Pause) {
            throw new ConflictException("Cannot resume a table in status " + gameTable.getStatus());
        }
        recordStatusChange(gameTable, GameTableStatus.Pause, GameTableStatus.InProgress, actorId, null);
        return toDetail(gameTable);
    }

    @Transactional(readOnly = true)
    public List<TableStatusChangeResponse> getStatusHistory(String gameTableId, String actorId) {
        if (!masterService.isMasterOf(gameTableId, actorId) && !isAdmin(actorId)) {
            throw new ForbiddenActionException("Only a master of this table or an admin can view its status history");
        }
        return tableStatusChangeRepository.findByGameTable_IdOrderByCreatedAtAsc(gameTableId).stream()
                .map(gameTableMapper::toStatusChangeResponse)
                .toList();
    }

    @Transactional
    public List<MasterSummaryResponse> addOrPromoteMaster(String gameTableId, String actorId, AddMasterRequest request) {
        GameTable gameTable = getEntityById(gameTableId);
        masterService.addOrPromote(gameTable, actorId, request.userId(), request.masterType());
        return masterService.findByGameTable(gameTableId).stream().map(gameTableMapper::toMasterSummary).toList();
    }

    /** Excludes tables the actor masters (#154): a master cannot browse their own table to apply as a Player at it. */
    @Transactional(readOnly = true)
    public PageResponse<GameTableSummaryResponse> list(Pageable pageable, String actorId) {
        Page<GameTable> page = gameTableRepository.findByStatusInAndNotMasteredByActor(VISIBLE_STATUSES, actorId, pageable);
        return PageResponse.from(page.map(this::toSummary));
    }

    @Transactional(readOnly = true)
    public GameTableDetailResponse getDetail(String gameTableId) {
        return toDetail(getEntityById(gameTableId));
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
        return PageResponse.from(page.map(registration -> toSummary(registration.getGameTable())));
    }

    /** /master/tables: every status, including Preparation - a master needs to see and open their own drafts. */
    @Transactional(readOnly = true)
    public PageResponse<GameTableSummaryResponse> listManaged(String actorId, Pageable pageable) {
        Page<GameTable> page = gameTableRepository.findByMasterUserId(actorId, pageable);
        return PageResponse.from(page.map(this::toSummary));
    }

    /**
     * /admin/tables: unfiltered by pertenencia, defaults to the statuses waiting on an admin.
     * Unassigned tables have no Primary yet, so this uses toAdminSummary, never toSummary.
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminTableSummaryResponse> listForAdmin(@Nullable List<GameTableStatus> statuses, Pageable pageable) {
        List<GameTableStatus> effective = (statuses == null || statuses.isEmpty()) ? DEFAULT_ADMIN_REVIEW_STATUSES : statuses;
        Page<GameTable> page = gameTableRepository.findByStatusIn(effective, pageable);
        return PageResponse.from(page.map(this::toAdminSummary));
    }

    /** Internal read used by other services (e.g. registrations) - never exposed raw over HTTP (arquitectura.md 2.2/2.3). */
    @Transactional(readOnly = true)
    public GameTable getEntityById(String gameTableId) {
        return gameTableRepository.findById(gameTableId).orElseThrow(() -> new NotFoundException("Table not found: " + gameTableId));
    }

    private GameTable buildTable(CreateGameTableRequest request, User creator) {
        GameTable gameTable = new GameTable(request.name(), creator);
        gameTable.setDescription(request.description());
        gameTable.setRequirements(request.requirements());
        gameTable.setStartDate(request.startDate());
        gameTable.setDuration(request.duration());
        gameTable.setTotalSessions(request.totalSessions());
        gameTable.setMaxPlayers(request.maxPlayers());
        if (request.tableTypeId() != null) {
            gameTable.setTableType(tableTypeRepository
                    .findById(request.tableTypeId())
                    .orElseThrow(() -> new NotFoundException("Table type not found: " + request.tableTypeId())));
        }
        return gameTable;
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

    private GameTableSummaryResponse toSummary(GameTable gameTable) {
        int playerCount = countPlayers(gameTable.getId());
        MasterSummaryResponse primaryMaster = findPrimaryMaster(gameTable.getId());
        return gameTableMapper.toSummary(gameTable, playerCount, primaryMaster);
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

    private GameTableDetailResponse toDetail(GameTable gameTable) {
        int playerCount = countPlayers(gameTable.getId());
        List<MasterSummaryResponse> masters =
                masterService.findByGameTable(gameTable.getId()).stream().map(gameTableMapper::toMasterSummary).toList();
        return gameTableMapper.toDetail(gameTable, playerCount, masters);
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
