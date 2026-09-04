package com.centraldungeon.registrations;

import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.exception.ForbiddenActionException;
import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.notifications.NotificationService;
import com.centraldungeon.registrations.dto.CreateRegistrationRequest;
import com.centraldungeon.registrations.dto.RegistrationResponse;
import com.centraldungeon.registrations.dto.RejectRegistrationRequest;
import com.centraldungeon.registrations.dto.TablePlayerResponse;
import com.centraldungeon.tables.GameTable;
import com.centraldungeon.tables.GameTableRepository;
import com.centraldungeon.tables.GameTableStatus;
import com.centraldungeon.tables.CommittedTable;
import com.centraldungeon.tables.Master;
import com.centraldungeon.tables.MasterService;
import com.centraldungeon.tables.ScheduleConflictService;
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

/**
 * Applying to a table, and what a master does about it.
 *
 * <p>Two invariants MySQL cannot express live here, and both are held by taking a pessimistic lock
 * on the <b>table</b> row: only one active registration per (table, person) pair (#28), and the
 * player cap (#34). The table is the thing to lock because "no registration exists yet" has no row
 * of its own to lock, and because the cap is a property of the table rather than of any one
 * application.
 *
 * <p>Filling the last seat auto-rejects the candidates still queued, in FIFO order (#34). Nobody is
 * left waiting on a table that can no longer take them.
 */
@Service
public class RegistrationService {

    /** The statuses that make a registration count as alive for the one-per-pair rule (#28). */
    private static final List<TableRegistrationStatus> ACTIVE_STATUSES =
            List.of(TableRegistrationStatus.Candidate, TableRegistrationStatus.Player);

    /** decisiones.md #34 fixes this literal in Spanish - it is content a player reads, not code (#102 does not apply to it). */
    /**
     * The reason the application itself writes when a table fills up (#34).
     *
     * <p>A <b>code</b> and not a sentence (#197): every other justification on this table is a
     * master writing to a person and is shown verbatim, but this one is the system speaking, so it
     * has to come out in the reader's language. {@code rejected_by IS NULL} is what already tells
     * the two apart, so no column was needed - only the value.
     */
    private static final String AUTO_REJECT_REASON_CODE = "TABLE_FULL";

    /** The {@code table_registrations} table. */
    private final TableRegistrationRepository registrationRepository;

    /** The reasons attached to turned-down applications. */
    private final RegistrationRejectionRepository rejectionRepository;

    /** Used to lock the table row, which is what serializes the two invariants above. */
    private final GameTableRepository gameTableRepository;

    /** Answers pertenencia: only a master of the table may accept or reject (#17, #121). */
    private final MasterService masterService;

    /** Resolves the applicant and the acting master. */
    private final UserService userService;

    /** Emits what the applicant and the masters have to be told (#77). */
    private final NotificationService notificationService;

    /** Answers the three clash questions of #178 this service asks: R2, R3 and R4. */
    private final ScheduleConflictService scheduleConflictService;

    /** Entity to DTO. */
    private final RegistrationMapper registrationMapper;

    /**
     * @param registrationRepository the {@code table_registrations} table
     * @param rejectionRepository    the reasons behind turned-down applications
     * @param gameTableRepository    used to lock the table row the invariants serialize on
     * @param masterService          answers pertenencia
     * @param userService            resolves the people involved
     * @param notificationService    tells the applicant and the masters what happened
     * @param scheduleConflictService answers whether the applicant is already busy at that hour (#178)
     * @param registrationMapper     entity to DTO
     */
    public RegistrationService(
            TableRegistrationRepository registrationRepository,
            RegistrationRejectionRepository rejectionRepository,
            GameTableRepository gameTableRepository,
            MasterService masterService,
            UserService userService,
            NotificationService notificationService,
            ScheduleConflictService scheduleConflictService,
            RegistrationMapper registrationMapper) {
        this.registrationRepository = registrationRepository;
        this.rejectionRepository = rejectionRepository;
        this.gameTableRepository = gameTableRepository;
        this.masterService = masterService;
        this.userService = userService;
        this.notificationService = notificationService;
        this.scheduleConflictService = scheduleConflictService;
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

        // R2 (#178): a table where they already play is a real commitment, so this blocks rather
        // than warns. Running and playing weigh the same - it is one person and one Tuesday night.
        CommittedTable clash = scheduleConflictService.findClashWith(actorId, table);
        if (clash != null) {
            throw new ConflictException(
                    "Table schedule overlaps " + clash.name() + ", where the applicant already plays",
                    ConflictException.SCHEDULE_CONFLICT,
                    Map.of(ConflictException.PARAM_OTHER_TABLE_NAME, clash.name()));
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
     * Accepting the candidate that completes max_players auto-rejects the rest with TABLE_FULL
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

        // R3 (#178): asked again here and not only at apply time, because the candidate may have
        // been accepted somewhere else in between - the answer is a different one now than it was.
        String candidateId = registration.getUser().getId();
        CommittedTable clash = scheduleConflictService.findClashWith(candidateId, table);
        if (clash != null) {
            throw new ConflictException(
                    "The candidate already plays at a table clashing with this one's agenda",
                    ConflictException.CANDIDATE_SCHEDULE_CONFLICT,
                    Map.of(ConflictException.PARAM_OTHER_TABLE_NAME, clash.name()));
        }

        registration.setStatus(TableRegistrationStatus.Player);
        notificationService.notifyRegistrationAccepted(candidateId, table);
        warnAboutNowClashingApplications(candidateId, table);

        Integer maxPlayers = table.getMaxPlayers();
        if (maxPlayers != null) {
            long playerCount = registrationRepository.countByGameTable_IdAndStatus(table.getId(), TableRegistrationStatus.Player);
            if (playerCount >= maxPlayers) {
                autoRejectRemainingCandidates(table);
            }
        }

        return registrationMapper.toResponse(registration);
    }

    /**
     * A master turning down an application, with a reason.
     *
     * <p>The reason is stored and sent to the applicant: a rejection they can learn nothing from is
     * the worst outcome the flow can produce.
     *
     * @param registrationId the application
     * @param actorId        the actor, from the token; has to run the table (#17, #121)
     * @param request        the justification
     * @return the application afterwards
     * @throws com.centraldungeon.common.exception.ForbiddenActionException if the actor does not run
     *         the table
     * @throws ConflictException if the application was not a pending candidate
     */
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
        notificationService.notifyRegistrationRejected(registration.getUser().getId(), registration.getGameTable());

        return registrationMapper.toResponse(registration);
    }

    /**
     * The applicant taking their own application back, while it is still pending.
     *
     * <p>It exists because R4 needs it to (#178). When accepting somebody makes their other pending
     * applications clash, they get told - and a notification that asks for an action nobody can take
     * is the dead end E1 already documented with /my/tables. This is the action.
     *
     * <p>Only a {@code Candidate}, and only their own: once accepted there is a table full of people
     * counting on them, and leaving it is a conversation with a master rather than a button. The
     * registration is marked, never dropped (#25, #175) - that somebody applied and thought better
     * of it is part of the record.
     *
     * @param registrationId the application to withdraw
     * @param actorId        the applicant, from the token. Never an id from the URL: the check is
     *                       that the registration is theirs (#121)
     * @throws com.centraldungeon.common.exception.ForbiddenActionException if the application is
     *         somebody else's
     * @throws ConflictException if it is no longer pending
     */
    @Transactional
    public void withdraw(String registrationId, String actorId) {
        TableRegistration registration = getRegistrationById(registrationId);
        if (!registration.getUser().getId().equals(actorId)) {
            throw new ForbiddenActionException("Cannot withdraw another user's application");
        }
        if (registration.getStatus() != TableRegistrationStatus.Candidate) {
            throw new ConflictException("Only a pending application can be withdrawn");
        }
        registration.setStatus(TableRegistrationStatus.Deleted);
    }

    /** Candidates only, FIFO by arrival - never re-sorted, whatever the caller's sort param says (#28). */
    @Transactional(readOnly = true)
    public PageResponse<RegistrationResponse> listCandidatesForTable(String gameTableId, String actorId, Pageable pageable) {
        requireMasterOf(gameTableId, actorId, "view its candidates");
        Pageable fifo = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("createdAt").ascending());
        Page<TableRegistration> page = registrationRepository.findByGameTable_IdAndStatus(gameTableId, TableRegistrationStatus.Candidate, fifo);
        return PageResponse.from(page.map(registrationMapper::toResponse));
    }

    /**
     * Everything the actor applied to, whatever came of it. Backs /my/applications.
     *
     * <p>It is the one place a rejection's justification is loaded, because it is the only screen
     * where the applicant themselves reads it (#34) - the master's queue has no use for it.
     *
     * @param actorId  the actor, from the token (#121)
     * @param pageable page, size and sort
     * @return one page of their applications, each with its rejection reason when there is one
     */
    @Transactional(readOnly = true)
    public PageResponse<RegistrationResponse> listMine(String actorId, Pageable pageable) {
        Page<TableRegistration> page =
                registrationRepository.findByUser_IdAndStatusNot(actorId, TableRegistrationStatus.Deleted, pageable);
        Map<String, RegistrationRejection> rejectionByRegistrationId = loadRejections(page.getContent());
        return PageResponse.from(page.map(registration -> {
            RegistrationResponse response = registrationMapper.toResponse(registration);
            RegistrationRejection rejection = rejectionByRegistrationId.get(registration.getId());
            if (rejection == null) {
                return response;
            }
            // A rejection the application wrote itself is a code to translate; one a master wrote is
            // their own words and is shown exactly as typed. `rejected_by IS NULL` is what tells the
            // two apart, and it always has (#34, #197).
            boolean automatic = rejection.getRejectedBy() == null;
            return new RegistrationResponse(
                    response.id(), response.gameTableId(), response.gameTableName(), response.userId(), response.userName(),
                    response.userKarma(), response.status(), response.description(), response.createdAt(),
                    automatic ? null : rejection.getDescription(),
                    automatic ? rejection.getDescription() : null);
        }));
    }

    /**
     * The table's current players - its roster, as the people running it need it.
     *
     * <p>A different question from {@link #listCandidatesForTable}, which answers with the queue
     * waiting to get in (#28). Nothing could ask this one until F1.5 needed it: addressing a task to
     * one player (#76) means being able to choose among them, and offering the platform's whole user
     * directory there would be offering people who cannot be chosen.
     *
     * <p>A list and not a page: it is bounded by {@code max_players} and read as one roster.
     *
     * @param gameTableId the table
     * @param actorId     the actor, from the token (#121)
     * @return its players, in the order they were accepted
     * @throws ForbiddenActionException if the actor does not run the table (#17, #135)
     */
    @Transactional(readOnly = true)
    public List<TablePlayerResponse> listPlayersForTable(String gameTableId, String actorId) {
        requireMasterOf(gameTableId, actorId, "view its players");
        return registrationRepository
                .findByGameTable_IdAndStatusOrderByCreatedAtAsc(gameTableId, TableRegistrationStatus.Player).stream()
                .map(registration -> new TablePlayerResponse(
                        registration.getUser().getId(),
                        registration.getUser().getDiscordUsername(),
                        registration.getUser().getKarma()))
                .toList();
    }

    private Map<String, RegistrationRejection> loadRejections(List<TableRegistration> registrations) {
        List<String> rejectedIds = registrations.stream()
                .filter(registration -> registration.getStatus() == TableRegistrationStatus.Rejected)
                .map(TableRegistration::getId)
                .toList();
        if (rejectedIds.isEmpty()) {
            return Map.of();
        }
        return rejectionRepository.findByRegistration_IdIn(rejectedIds).stream()
                .collect(Collectors.toMap(rejection -> rejection.getRegistration().getId(), rejection -> rejection));
    }

    /**
     * R4 (#178): now that this person plays here, tell them which of their other pending
     * applications fall at the same hour.
     *
     * <p><b>Told, not rejected.</b> Somebody sends three applications to see which one comes
     * through, and until one does there is no commitment to defend; deciding for them which to drop
     * would be the system making a choice that is theirs, the same reasoning as #70. A table where
     * they already play is different, and that is why R2 and R3 block for real.
     */
    private void warnAboutNowClashingApplications(String userId, GameTable acceptedTable) {
        for (TableRegistration other : registrationRepository.findByUser_IdAndStatus(userId, TableRegistrationStatus.Candidate)) {
            GameTable otherTable = other.getGameTable();
            if (otherTable.getId().equals(acceptedTable.getId())) {
                continue;
            }
            if (scheduleConflictService.overlap(acceptedTable, otherTable)) {
                notificationService.notifyScheduleConflict(userId, otherTable, acceptedTable.getName());
            }
        }
    }

    private void autoRejectRemainingCandidates(GameTable table) {
        List<TableRegistration> remaining =
                registrationRepository.findByGameTable_IdAndStatusOrderByCreatedAtAsc(table.getId(), TableRegistrationStatus.Candidate);
        for (TableRegistration candidate : remaining) {
            candidate.setStatus(TableRegistrationStatus.Rejected);
            rejectionRepository.save(new RegistrationRejection(candidate, AUTO_REJECT_REASON_CODE, null));
            notificationService.notifyRegistrationRejected(candidate.getUser().getId(), table);
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
