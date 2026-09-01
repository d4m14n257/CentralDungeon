package com.centraldungeon.registrations;

import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.exception.ForbiddenActionException;
import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.notifications.NotificationService;
import com.centraldungeon.registrations.dto.CreateRegistrationRequest;
import com.centraldungeon.registrations.dto.RegistrationResponse;
import com.centraldungeon.registrations.dto.RejectRegistrationRequest;
import com.centraldungeon.tables.GameTable;
import com.centraldungeon.tables.GameTableRepository;
import com.centraldungeon.tables.GameTableStatus;
import com.centraldungeon.tables.Master;
import com.centraldungeon.tables.MasterService;
import com.centraldungeon.users.PlatformRole;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserAuthSnapshot;
import com.centraldungeon.users.UserService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

    private static final List<TableRegistrationStatus> ACTIVE_STATUSES =
            List.of(TableRegistrationStatus.Candidate, TableRegistrationStatus.Player);

    /** decisiones.md #34 fixes this literal in Spanish - it is content a player reads, not code (#102 does not apply to it). */
    private static final String AUTO_REJECT_JUSTIFICATION = "Mesa llena";

    private final TableRegistrationRepository registrationRepository;
    private final RegistrationRejectionRepository rejectionRepository;
    private final GameTableRepository gameTableRepository;
    private final MasterService masterService;
    private final UserService userService;
    private final NotificationService notificationService;
    private final RegistrationMapper registrationMapper;

    public RegistrationService(
            TableRegistrationRepository registrationRepository,
            RegistrationRejectionRepository rejectionRepository,
            GameTableRepository gameTableRepository,
            MasterService masterService,
            UserService userService,
            NotificationService notificationService,
            RegistrationMapper registrationMapper) {
        this.registrationRepository = registrationRepository;
        this.rejectionRepository = rejectionRepository;
        this.gameTableRepository = gameTableRepository;
        this.masterService = masterService;
        this.userService = userService;
        this.notificationService = notificationService;
        this.registrationMapper = registrationMapper;
    }

    /**
     * Locks the table row before checking: it is the only way to serialize concurrent applications
     * for the same table and make "at most one active registration per pair" (#28) actually hold.
     */
    @Transactional
    public RegistrationResponse apply(String gameTableId, String actorId, CreateRegistrationRequest request) {
        GameTable table = lockTable(gameTableId);

        UserAuthSnapshot actorSnapshot = userService.loadAuthSnapshot(actorId);
        if (!actorSnapshot.roles().contains(PlatformRole.PLAYER.roleName())) {
            throw new ForbiddenActionException("The Player role is required to apply");
        }
        if (masterService.isMasterOf(gameTableId, actorId)) {
            throw new ForbiddenActionException("A master of this table cannot apply to it as a candidate");
        }
        if (table.getStatus() != GameTableStatus.Opened) {
            throw new ConflictException("Table is not open for applications");
        }
        if (registrationRepository.existsByGameTable_IdAndUser_IdAndStatusIn(gameTableId, actorId, ACTIVE_STATUSES)) {
            throw new ConflictException("An active application for this table already exists");
        }

        User actor = userService.getById(actorId);
        TableRegistration registration = registrationRepository.save(new TableRegistration(table, actor, request.description()));

        String applicantName = actor.getName() != null ? actor.getName() : actor.getDiscordUsername();
        for (Master master : masterService.findByGameTable(gameTableId)) {
            notificationService.notifyNewCandidate(master.getUser().getId(), table, applicantName);
        }

        return registrationMapper.toResponse(registration);
    }

    /**
     * Accepting the candidate that completes max_players auto-rejects the rest with "Mesa llena"
     * (#34). The table lock also protects this: two concurrent accepts cannot both think there is
     * room left.
     */
    @Transactional
    public RegistrationResponse accept(String registrationId, String actorId) {
        TableRegistration registration = getRegistrationById(registrationId);
        GameTable table = lockTable(registration.getGameTable().getId());

        requireMasterOf(table.getId(), actorId, "accept candidates");
        if (registration.getStatus() != TableRegistrationStatus.Candidate) {
            throw new ConflictException("Registration is not a pending candidate");
        }

        registration.setStatus(TableRegistrationStatus.Player);
        notificationService.notifyRegistrationAccepted(registration.getUser().getId(), table);

        Integer maxPlayers = table.getMaxPlayers();
        if (maxPlayers != null) {
            long playerCount = registrationRepository.countByGameTable_IdAndStatus(table.getId(), TableRegistrationStatus.Player);
            if (playerCount >= maxPlayers) {
                autoRejectRemainingCandidates(table);
            }
        }

        return registrationMapper.toResponse(registration);
    }

    @Transactional
    public RegistrationResponse reject(String registrationId, String actorId, RejectRegistrationRequest request) {
        TableRegistration registration = getRegistrationById(registrationId);
        requireMasterOf(registration.getGameTable().getId(), actorId, "reject candidates");
        if (registration.getStatus() != TableRegistrationStatus.Candidate) {
            throw new ConflictException("Registration is not a pending candidate");
        }

        User rejectedBy = userService.getById(actorId);
        registration.setStatus(TableRegistrationStatus.Rejected);
        rejectionRepository.save(new RegistrationRejection(registration, request.justification(), rejectedBy));
        notificationService.notifyRegistrationRejected(registration.getUser().getId(), registration.getGameTable(), request.justification());

        return registrationMapper.toResponse(registration);
    }

    /** Candidates only, FIFO by arrival - never re-sorted, whatever the caller's sort param says (#28). */
    @Transactional(readOnly = true)
    public PageResponse<RegistrationResponse> listCandidatesForTable(String gameTableId, String actorId, Pageable pageable) {
        requireMasterOf(gameTableId, actorId, "view its candidates");
        Pageable fifo = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("createdAt").ascending());
        Page<TableRegistration> page = registrationRepository.findByGameTable_IdAndStatus(gameTableId, TableRegistrationStatus.Candidate, fifo);
        return PageResponse.from(page.map(registrationMapper::toResponse));
    }

    /** La justificación del rechazo importa acá - es la única pantalla donde el propio postulante la lee (#34). */
    @Transactional(readOnly = true)
    public PageResponse<RegistrationResponse> listMine(String actorId, Pageable pageable) {
        Page<TableRegistration> page = registrationRepository.findByUser_Id(actorId, pageable);
        Map<String, String> justificationByRegistrationId = loadRejectionJustifications(page.getContent());
        return PageResponse.from(page.map(registration -> {
            RegistrationResponse response = registrationMapper.toResponse(registration);
            String justification = justificationByRegistrationId.get(registration.getId());
            return justification == null
                    ? response
                    : new RegistrationResponse(
                            response.id(), response.gameTableId(), response.gameTableName(), response.userId(), response.userName(),
                            response.userKarma(), response.status(), response.description(), response.createdAt(), justification);
        }));
    }

    private Map<String, String> loadRejectionJustifications(List<TableRegistration> registrations) {
        List<String> rejectedIds = registrations.stream()
                .filter(registration -> registration.getStatus() == TableRegistrationStatus.Rejected)
                .map(TableRegistration::getId)
                .toList();
        if (rejectedIds.isEmpty()) {
            return Map.of();
        }
        return rejectionRepository.findByRegistration_IdIn(rejectedIds).stream()
                .collect(Collectors.toMap(
                        rejection -> rejection.getRegistration().getId(), RegistrationRejection::getDescription));
    }

    private void autoRejectRemainingCandidates(GameTable table) {
        List<TableRegistration> remaining =
                registrationRepository.findByGameTable_IdAndStatusOrderByCreatedAtAsc(table.getId(), TableRegistrationStatus.Candidate);
        for (TableRegistration candidate : remaining) {
            candidate.setStatus(TableRegistrationStatus.Rejected);
            rejectionRepository.save(new RegistrationRejection(candidate, AUTO_REJECT_JUSTIFICATION, null));
            notificationService.notifyRegistrationRejected(candidate.getUser().getId(), table, AUTO_REJECT_JUSTIFICATION);
        }
    }

    private void requireMasterOf(String gameTableId, String actorId, String action) {
        if (!masterService.isMasterOf(gameTableId, actorId)) {
            throw new ForbiddenActionException("Only a master of this table can " + action);
        }
    }

    private GameTable lockTable(String gameTableId) {
        return gameTableRepository.findByIdForUpdate(gameTableId).orElseThrow(() -> new NotFoundException("Table not found: " + gameTableId));
    }

    private TableRegistration getRegistrationById(String registrationId) {
        return registrationRepository.findById(registrationId)
                .orElseThrow(() -> new NotFoundException("Registration not found: " + registrationId));
    }
}
