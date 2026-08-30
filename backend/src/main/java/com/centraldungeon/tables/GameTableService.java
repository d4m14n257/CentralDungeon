package com.centraldungeon.tables;

import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.exception.ForbiddenActionException;
import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.registrations.TableRegistration;
import com.centraldungeon.registrations.TableRegistrationRepository;
import com.centraldungeon.registrations.TableRegistrationStatus;
import com.centraldungeon.tables.dto.AddMasterRequest;
import com.centraldungeon.tables.dto.CreateGameTableRequest;
import com.centraldungeon.tables.dto.GameTableDetailResponse;
import com.centraldungeon.tables.dto.GameTableSummaryResponse;
import com.centraldungeon.tables.dto.MasterSummaryResponse;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserService;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GameTableService {

    private static final List<GameTableStatus> VISIBLE_STATUSES = List.of(GameTableStatus.Opened, GameTableStatus.InProgress);

    private final GameTableRepository gameTableRepository;
    private final TableTypeRepository tableTypeRepository;
    private final TableRegistrationRepository tableRegistrationRepository;
    private final MasterService masterService;
    private final GameTableMapper gameTableMapper;
    private final UserService userService;

    public GameTableService(
            GameTableRepository gameTableRepository,
            TableTypeRepository tableTypeRepository,
            TableRegistrationRepository tableRegistrationRepository,
            MasterService masterService,
            GameTableMapper gameTableMapper,
            UserService userService) {
        this.gameTableRepository = gameTableRepository;
        this.tableTypeRepository = tableTypeRepository;
        this.tableRegistrationRepository = tableRegistrationRepository;
        this.masterService = masterService;
        this.gameTableMapper = gameTableMapper;
        this.userService = userService;
    }

    /** The creator becomes the table's Primary master (#73); a Master row is the source of pertenencia, not the role alone (#135). */
    @Transactional
    public GameTableDetailResponse create(CreateGameTableRequest request, String creatorId) {
        User creator = userService.getById(creatorId);

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

        gameTable = gameTableRepository.save(gameTable);
        masterService.createPrimary(gameTable, creator);

        return toDetail(gameTable);
    }

    /** Preparation to Opened is the only transition E1 exposes - the admin review path lands with approval_requests in a later etapa. */
    @Transactional
    public GameTableDetailResponse open(String gameTableId, String actorId) {
        GameTable gameTable = getEntityById(gameTableId);
        if (!masterService.isPrimaryOf(gameTableId, actorId)) {
            throw new ForbiddenActionException("Only the Primary master can open this table");
        }
        if (gameTable.getStatus() != GameTableStatus.Preparation) {
            throw new ConflictException("Cannot open a table in status " + gameTable.getStatus());
        }
        gameTable.setStatus(GameTableStatus.Opened);
        return toDetail(gameTable);
    }

    @Transactional
    public List<MasterSummaryResponse> addOrPromoteMaster(String gameTableId, String actorId, AddMasterRequest request) {
        GameTable gameTable = getEntityById(gameTableId);
        masterService.addOrPromote(gameTable, actorId, request.userId(), request.masterType());
        return masterService.findByGameTable(gameTableId).stream().map(gameTableMapper::toMasterSummary).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<GameTableSummaryResponse> list(Pageable pageable) {
        Page<GameTable> page = gameTableRepository.findByStatusIn(VISIBLE_STATUSES, pageable);
        return PageResponse.from(page.map(this::toSummary));
    }

    @Transactional(readOnly = true)
    public GameTableDetailResponse getDetail(String gameTableId) {
        return toDetail(getEntityById(gameTableId));
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

    /** Internal read used by other services (e.g. registrations) - never exposed raw over HTTP (arquitectura.md 2.2/2.3). */
    @Transactional(readOnly = true)
    public GameTable getEntityById(String gameTableId) {
        return gameTableRepository.findById(gameTableId).orElseThrow(() -> new NotFoundException("Table not found: " + gameTableId));
    }

    private GameTableSummaryResponse toSummary(GameTable gameTable) {
        int playerCount = countPlayers(gameTable.getId());
        MasterSummaryResponse primaryMaster = findPrimaryMaster(gameTable.getId());
        return gameTableMapper.toSummary(gameTable, playerCount, primaryMaster);
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
}
