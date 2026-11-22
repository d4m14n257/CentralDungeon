package com.centraldungeon.registrations;

import com.centraldungeon.approvals.ApprovalRequest;
import com.centraldungeon.approvals.ApprovalRequestRepository;
import com.centraldungeon.approvals.ApprovalRequestType;
import com.centraldungeon.approvals.ApprovalStatus;
import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.exception.ForbiddenActionException;
import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.files.FileCategory;
import com.centraldungeon.files.FileService;
import com.centraldungeon.files.StoredFile;
import com.centraldungeon.notifications.NotificationService;
import com.centraldungeon.registrations.dto.BlockRegistrationRequest;
import com.centraldungeon.registrations.dto.CreateRegistrationRequest;
import com.centraldungeon.registrations.dto.MyRejectionResponse;
import com.centraldungeon.registrations.dto.RegistrationFileResponse;
import com.centraldungeon.registrations.dto.RegistrationResponse;
import com.centraldungeon.registrations.dto.RejectRegistrationRequest;
import com.centraldungeon.registrations.dto.TablePlayerResponse;
import com.centraldungeon.registrations.dto.UnblockRegistrationRequest;
import com.centraldungeon.tables.GameTable;
import com.centraldungeon.tables.GameTableRepository;
import com.centraldungeon.tables.GameTableStatus;
import com.centraldungeon.tables.CommittedTable;
import com.centraldungeon.tables.Master;
import com.centraldungeon.tables.MasterService;
import com.centraldungeon.tables.ScheduleConflictService;
import com.centraldungeon.tables.TableVisibilityService;
import com.centraldungeon.users.PlatformRole;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserAuthSnapshot;
import com.centraldungeon.users.UserService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applying to a table, and what a master does about it.
 *
 * <p>Two invariants MySQL cannot express live here, and both are held by taking a pessimistic lock
 * on the <b>table</b> row: only one active registration per (table, person) pair (ADR-09 §1), and the
 * player cap (ADR-09 §3). The table is the thing to lock because "no registration exists yet" has no row
 * of its own to lock, and because the cap is a property of the table rather than of any one
 * application.
 *
 * <p>Filling the last seat auto-rejects the candidates still queued, in FIFO order (ADR-09 §3). Nobody is
 * left waiting on a table that can no longer take them.
 *
 * <p><b>An application may carry files</b> (ADR-13 §1 uso 2): the character sheet a candidate applies
 * with, linked rather than copied (ADR-14 §1). There is no endpoint of its own for them - they travel
 * inside {@link RegistrationResponse}, the same criterion F1.3 used for a table's sessions and F1.4
 * for its shared files, because this class already decides who may see an application.
 *
 * <p><b>Withdrawing never touches {@code registration_files}</b> (ADR-09 §2, deliberate): the row is the
 * record that a sheet was sent, and withdrawing does not undo that it was. What stops counting a
 * withdrawn application's file as in use is the read side of {@link RegistrationFileRepository},
 * never a cascade here - see {@link #withdraw}.
 */
@Service
public class RegistrationService {

    /** The statuses that make a registration count as alive for the one-per-pair rule (ADR-09 §1). */
    private static final List<TableRegistrationStatus> ACTIVE_STATUSES =
            List.of(TableRegistrationStatus.Candidate, TableRegistrationStatus.Player);

    /**
     * The only status a veto can be applied to (ADR-09 §5): somebody who is <em>currently seated</em>.
     *
     * <p>A {@code Candidate} is not here on purpose: somebody who is not wanted yet is turned down, not
     * vetoed. Rejected and Deleted are not here either - there is nothing to veto, and «no ve esa
     * mesa» is already true of somebody who never got in. Vetoing them would put a row in the trail
     * that denies nothing.
     */
    private static final TableRegistrationStatus BLOCKABLE_STATUS = TableRegistrationStatus.Player;

    /**
     * What {@code /my/applications} never shows (ADR-03 §7, ADR-09 §4).
     *
     * <p>{@code Blocked} sits beside {@code Deleted} because the row carries {@code gameTableName}:
     * leaving it in would be the single crack through which a vetoed person keeps seeing the table
     * every other read answers them 404 about.
     */
    private static final List<TableRegistrationStatus> HIDDEN_FROM_MINE =
            List.of(TableRegistrationStatus.Deleted, TableRegistrationStatus.Blocked);

    /**
     * The reason the application itself writes when a table fills up (ADR-09 §3).
     *
     * <p>A <b>code</b> and not a sentence (ADR-23 §3): every other justification on this table is a
     * master writing to a person and is shown verbatim, but this one is the system speaking, so it
     * has to come out in the reader's language. {@code rejected_by IS NULL} is what already tells
     * the two apart, so no column was needed - only the value.
     */
    private static final String AUTO_REJECT_REASON_CODE = "TABLE_FULL";

    /** The {@code table_registrations} table. */
    private final TableRegistrationRepository registrationRepository;

    /** The reasons attached to turned-down applications. */
    private final RegistrationRejectionRepository rejectionRepository;

    /** The files attached to an application - linked, never copied (ADR-13 §2, §1, ADR-14 §1 uso 2). */
    private final RegistrationFileRepository registrationFileRepository;

    /** Resolves and authorizes each file being attached, and puts it in its cajón (ADR-14 §4). */
    private final FileService fileService;

    /** Used to lock the table row, which is what serializes the two invariants above. */
    private final GameTableRepository gameTableRepository;

    /** Answers pertenencia: only a master of the table may accept or reject (hard rule 17, ADR-05 §5). */
    private final MasterService masterService;

    /** Resolves the applicant and the acting master. */
    private final UserService userService;

    /** Emits what the applicant and the masters have to be told (ADR-15 §5). */
    private final NotificationService notificationService;

    /** Answers the three clash questions of ADR-08 §2 this service asks: R2, R3 and R4. */
    private final ScheduleConflictService scheduleConflictService;

    /** The veto's trail: every block and every lifting, with its reason (ADR-09 §5). */
    private final RegistrationStatusChangeRepository statusChangeRepository;

    /**
     * The one gate every read of a concrete table goes through (ADR-03 §7, ADR-09 §4).
     *
     * <p>Applying goes through it too, which is what makes «volver a postularse a la misma mesa»
     * impossible without a rule of its own: the vetoed person gets the same 404 the explorer and the
     * detail already give them. That is the proof the single point works - nobody had to remember
     * this endpoint.
     */
    private final TableVisibilityService tableVisibilityService;

    /** Entity to DTO. */
    private final RegistrationMapper registrationMapper;

    /**
     * The veto requests behind each veto, read for the roster only.
     *
     * <p>The repository and not {@code ApprovalService}: that service already depends on this one to
     * apply an approved veto, so depending back on it would be a cycle of beans.
     */
    private final ApprovalRequestRepository approvalRequestRepository;

    /**
     * @param registrationRepository     the {@code table_registrations} table
     * @param rejectionRepository        the reasons behind turned-down applications
     * @param registrationFileRepository the files attached to an application (ADR-13 §1 uso 2)
     * @param fileService                resolves and authorizes each file being attached (ADR-14 §1)
     * @param gameTableRepository         used to lock the table row the invariants serialize on
     * @param masterService               answers pertenencia
     * @param userService                 resolves the people involved
     * @param notificationService        tells the applicant and the masters what happened
     * @param scheduleConflictService answers whether the applicant is already busy at that hour (ADR-08 §2)
     * @param statusChangeRepository the veto's trail (ADR-09 §5)
     * @param tableVisibilityService the one gate of ADR-03 §7 and ADR-09 §4, so applying cannot walk around it
     * @param registrationMapper     entity to DTO
     * @param approvalRequestRepository the veto requests the roster names next to each veto
     */
    public RegistrationService(
            TableRegistrationRepository registrationRepository,
            RegistrationRejectionRepository rejectionRepository,
            RegistrationFileRepository registrationFileRepository,
            FileService fileService,
            GameTableRepository gameTableRepository,
            MasterService masterService,
            UserService userService,
            NotificationService notificationService,
            ScheduleConflictService scheduleConflictService,
            RegistrationStatusChangeRepository statusChangeRepository,
            TableVisibilityService tableVisibilityService,
            RegistrationMapper registrationMapper,
            ApprovalRequestRepository approvalRequestRepository) {
        this.registrationRepository = registrationRepository;
        this.rejectionRepository = rejectionRepository;
        this.registrationFileRepository = registrationFileRepository;
        this.fileService = fileService;
        this.gameTableRepository = gameTableRepository;
        this.masterService = masterService;
        this.userService = userService;
        this.notificationService = notificationService;
        this.scheduleConflictService = scheduleConflictService;
        this.statusChangeRepository = statusChangeRepository;
        this.tableVisibilityService = tableVisibilityService;
        this.registrationMapper = registrationMapper;
        this.approvalRequestRepository = approvalRequestRepository;
    }

    /**
     * Locks the table row before checking: it is the only way to serialize concurrent applications
     * for the same table and make "at most one active registration per pair" actually hold.
     *
     * <p><b>The lock is the first statement of the transaction, and that ordering is load-bearing.</b>
     * Putting the veto check in front of it once broke the invariant above - ten concurrent
     * applications all succeeded. Under {@code REPEATABLE READ} the first read opens the snapshot, so
     * a thread that read <em>before</em> queueing for the lock wakes up and answers the duplicate
     * check from a snapshot taken before the winner committed. Anything that reads a row this method
     * will re-read goes after the lock.
     *
     * @param gameTableId the table to apply to
     * @param actorId     the applicant, from the token
     * @param request     the description and the files to attach
     * @return the application, now a pending candidate
     * @throws NotFoundException        if the table does not exist, or the actor cannot see it (for
     *                                  instance, they are vetoed on it) - the same answer as a table
     *                                  that does not exist
     * @throws ForbiddenActionException if the actor sees the table but lacks the {@code Player} role, or
     *                                  runs the table
     * @throws ConflictException        if the table is not taking applications, the actor already has an
     *                                  active one, no seat is left, or the schedule clashes
     */
    @Transactional
    public RegistrationResponse apply(String gameTableId, String actorId, CreateRegistrationRequest request) {
        GameTable table = lockTable(gameTableId);
        // After the lock, for the reason above - but still through the single point, so that the
        // answer is the same 404 every other read of a vetoed table gives. Applying again needs no
        // rule of its own: this endpoint was never on anybody's list of read paths and is closed anyway.
        tableVisibilityService.requireVisible(gameTableId, actorId);

        UserAuthSnapshot actorSnapshot = userService.loadAuthSnapshot(actorId);
        if (!actorSnapshot.roles().contains(PlatformRole.PLAYER.roleName())) {
            throw new ForbiddenActionException("The Player role is required to apply");
        }
        if (masterService.isMasterOf(gameTableId, actorId)) {
            throw new ForbiddenActionException("A master of this table cannot apply to it as a candidate");
        }
        // Open, or in progress with its masters looking for players: one definition, on the table.
        if (!table.acceptsApplications()) {
            throw new ConflictException("Table is not open for applications");
        }
        if (registrationRepository.existsByGameTable_IdAndUser_IdAndStatusIn(gameTableId, actorId, ACTIVE_STATUSES)) {
            throw new ConflictException("An active application for this table already exists");
        }
        requireSeatLeft(table);

        // R2 (ADR-08 §2): a table where they already play is a real commitment, so this blocks rather
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
        List<RegistrationFileResponse> files = attachFiles(registration, request.fileIds(), actorId);

        String applicantName = actor.getName() != null ? actor.getName() : actor.getDiscordUsername();
        for (Master master : masterService.findByGameTable(gameTableId)) {
            notificationService.notifyNewCandidate(master.getUser().getId(), table, applicantName);
        }

        return registrationMapper.toResponse(registration, files);
    }

    /**
     * Links whatever the applicant attached to their application (ADR-13 §1 uso 2), and puts each file in
     * the {@code PlayerApplication} cajón (ADR-14 §4).
     *
     * <p>The same gate attaching a file to a table or an answer goes through: the applicant's own, or
     * one the platform published (ADR-14 §1). Somebody else's private upload never gets here.
     *
     * @param registration the application just saved, so the links have a row to point at
     * @param fileIds      the files to attach, by id. Never null in practice - the request field is
     *                     {@code @NotNull} - but treated as empty defensively, the same as
     *                     {@code TaskSubmissionService} treats its own
     * @param actorId      the applicant, from the token
     * @return the attached files, in the shape the response carries
     * @throws ForbiddenActionException if a file belongs to somebody else and is not published
     * @throws NotFoundException        if a file is not there
     */
    private List<RegistrationFileResponse> attachFiles(
            TableRegistration registration, @Nullable List<String> fileIds, String actorId) {
        List<String> ids = fileIds == null ? List.of() : fileIds;
        List<RegistrationFileResponse> files = new ArrayList<>();
        for (String fileId : ids) {
            StoredFile file = fileService.requireAttachable(fileId, actorId);
            // The cajón is a consequence of the link, not of the upload (ADR-14 §4): add-only and
            // idempotent, so attaching the same sheet to a third table costs nothing extra here.
            fileService.classify(fileId, FileCategory.PlayerApplication);
            registrationFileRepository.save(new RegistrationFile(registration.getId(), file.getId()));
            files.add(new RegistrationFileResponse(file.getId(), file.getName(), file.getMimeType(), file.getSizeBytes()));
        }
        return files;
    }

    /**
     * The attached files of one application, resolved through the bulk query so a single caller
     * pays the same one round trip a whole page would.
     */
    private List<RegistrationFileResponse> filesOf(String registrationId) {
        return filesByRegistrationIds(List.of(registrationId)).getOrDefault(registrationId, List.of());
    }

    /**
     * The attached files of a whole page of applications, in one query (ADR-14 §3).
     *
     * <p>A file its owner has since deleted is left out rather than shown as a broken row - the same
     * thing {@code TaskSubmissionService} does for a task's answers, and for the same reason: an
     * owner removing a file removes it from what shows it (ADR-03 §7).
     *
     * @param registrationIds the applications to resolve files for
     * @return the files per application id; an application with nothing attached is absent
     */
    private Map<String, List<RegistrationFileResponse>> filesByRegistrationIds(List<String> registrationIds) {
        if (registrationIds.isEmpty()) {
            return Map.of();
        }
        return registrationFileRepository.findAttachmentsByRegistrationIds(registrationIds).stream()
                .collect(Collectors.groupingBy(
                        RegistrationFileRow::registrationId,
                        Collectors.mapping(
                                row -> new RegistrationFileResponse(row.fileId(), row.name(), row.mimeType(), row.sizeBytes()),
                                Collectors.toList())));
    }

    /**
     * Accepting a candidate. A table with no seat left refuses with {@code TABLE_FULL}, and accepting
     * the candidate that completes max_players rejects the rest with that same code.
     *
     * <p><b>The table lock serializes this against every other write to the table's applications</b>,
     * and two things make that lock mean something here. The application is read only after it: an
     * entity loaded before the wait would still say {@code Candidate} after a concurrent automatic
     * rejection, and its full-row update would quietly undo it. And the transaction runs at
     * {@code READ COMMITTED}: the path names only the application, so its table has to be looked up
     * before the lock, and under {@code REPEATABLE READ} that first read would freeze the snapshot -
     * the player count and the candidates the automatic rejection walks would then be the ones from
     * before the transaction that held the lock committed, and the last seat could be given twice, or
     * somebody just accepted rejected.
     *
     * @param registrationId the application
     * @param actorId        the actor, from the token; has to run the table
     * @return the application, now {@code Player}
     * @throws NotFoundException if the application or its table does not exist, or the actor cannot see
     *         the application (they must see its table and run it, oversee it or have made the
     *         application) - the same answer as an application that does not exist
     * @throws ForbiddenActionException if the actor sees the application but does not run the table
     * @throws ConflictException {@code TABLE_FULL} when no seat is left, a schedule clash, or a plain
     *         conflict when the application is not a pending candidate
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RegistrationResponse accept(String registrationId, String actorId) {
        GameTable table = lockTableOf(registrationId);
        TableRegistration registration = getRegistrationById(registrationId);

        requireMasterOf(registration, actorId, "accept candidates");
        if (registration.getStatus() != TableRegistrationStatus.Candidate) {
            throw new ConflictException("Registration is not a pending candidate");
        }
        requireSeatLeft(table);

        // R3 (ADR-08 §2): asked again here and not only at apply time, because the candidate may have
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

        if (isFull(table)) {
            onSeatsFilled(table);
        }

        return registrationMapper.toResponse(registration, filesOf(registration.getId()));
    }

    /**
     * A master turning down an application, with a reason.
     *
     * <p>The reason is stored and sent to the applicant: a rejection they can learn nothing from is
     * the worst outcome the flow can produce.
     *
     * @param registrationId the application
     * @param actorId        the actor, from the token; has to run the table
     * @param request        the justification
     * @return the application afterwards
     * @throws NotFoundException if the application does not exist, or the actor cannot see it (they
     *         must see its table and run it, oversee it or have made the application) - the same
     *         answer as an application that does not exist
     * @throws ForbiddenActionException if the actor sees the application but does not run the table
     * @throws ConflictException if the application was not a pending candidate
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RegistrationResponse reject(String registrationId, String actorId, RejectRegistrationRequest request) {
        // Under the table's lock and at READ COMMITTED for the reason accept() gives: otherwise an accept
        // and a reject of the same candidate both apply, or deadlock on the row and the table.
        lockTableOf(registrationId);
        TableRegistration registration = getRegistrationById(registrationId);
        requireMasterOf(registration, actorId, "reject candidates");
        if (registration.getStatus() != TableRegistrationStatus.Candidate) {
            throw new ConflictException("Registration is not a pending candidate");
        }

        User rejectedBy = userService.getById(actorId);
        registration.setStatus(TableRegistrationStatus.Rejected);
        rejectionRepository.save(new RegistrationRejection(registration, request.justification(), rejectedBy));
        notificationService.notifyRegistrationRejected(registration.getUser().getId(), registration.getGameTable());

        return registrationMapper.toResponse(registration, filesOf(registration.getId()));
    }

    /**
     * The applicant taking their own application back, while it is still pending.
     *
     * <p>It exists because R4 needs it to (ADR-08 §2). When accepting somebody makes their other pending
     * applications clash, they get told - and a notification that asks for an action nobody can take
     * is the dead end E1 already documented with /my/tables. This is the action.
     *
     * <p>Only a {@code Candidate}, and only their own: once accepted there is a table full of people
     * counting on them, and leaving it is a conversation with a master rather than a button. The
     * registration is marked, never dropped (ADR-03 §7, ADR-07 §6) - that somebody applied and thought better
     * of it is part of the record.
     *
     * <p><b>Deliberately does not touch {@code registration_files}</b>: whatever the
     * candidate attached stays attached, marked or not. The row is the record that a sheet was sent,
     * and withdrawing does not undo that it was - only {@link RegistrationFileRepository}'s read
     * methods stop counting it as in use, by requiring the application to still be
     * {@code Candidate} or {@code Player}.
     *
     * @param registrationId the application to withdraw
     * @param actorId        the applicant, from the token. Never an id from the URL: the check is
     *                       that the registration is theirs
     * @throws NotFoundException if the application does not exist, or the actor cannot see it (they
     *         must see its table and run it, oversee it or have made the application) - the same
     *         answer as an application that does not exist
     * @throws ForbiddenActionException if the actor sees the application but it is somebody else's
     * @throws ConflictException if it is no longer pending
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void withdraw(String registrationId, String actorId) {
        // Serialized with the automatic rejection the way accept() explains: withdrawn and then
        // rejected «because the table is full» is a message about an application the person took back.
        lockTableOf(registrationId);
        TableRegistration registration = getRegistrationById(registrationId);
        if (!registration.getUser().getId().equals(actorId)) {
            requireSeen(registration, actorId);
            throw new ForbiddenActionException("Cannot withdraw another user's application");
        }
        if (registration.getStatus() != TableRegistrationStatus.Candidate) {
            throw new ConflictException("Only a pending application can be withdrawn");
        }
        registration.setStatus(TableRegistrationStatus.Deleted);
        registration.setDeletedAt(LocalDateTime.now());
    }

    /**
     * Candidates only, FIFO by arrival - never re-sorted, whatever the caller's sort param says.
     *
     * @param gameTableId the table
     * @param actorId     the actor, from the token
     * @param pageable    page and size; its sort is ignored
     * @return one page of the table's pending candidates, oldest first
     * @throws NotFoundException        if the table does not exist, or the actor cannot see it (for
     *                                  instance, they are vetoed on it) - the same answer as a table
     *                                  that does not exist
     * @throws ForbiddenActionException if the actor sees the table but neither runs it nor is an admin
     */
    @Transactional(readOnly = true)
    public PageResponse<RegistrationResponse> listCandidatesForTable(String gameTableId, String actorId, Pageable pageable) {
        // Read by the table's masters and by any admin (ADR-06 §8); its writes stay the masters' own.
        tableVisibilityService.requireExisting(gameTableId);
        if (!masterService.canOversee(gameTableId, actorId)) {
            // Said only about a table they may see: to anybody else it is the same 404 as an id nobody used.
            tableVisibilityService.requireVisible(gameTableId, actorId);
            throw new ForbiddenActionException("Only a master of this table or an admin can view its candidates");
        }
        Pageable fifo = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("createdAt").ascending());
        Page<TableRegistration> page = registrationRepository.findByGameTable_IdAndStatus(gameTableId, TableRegistrationStatus.Candidate, fifo);
        Map<String, List<RegistrationFileResponse>> filesByRegistration =
                filesByRegistrationIds(page.getContent().stream().map(TableRegistration::getId).toList());
        return PageResponse.from(page.map(registration ->
                registrationMapper.toResponse(registration, filesByRegistration.getOrDefault(registration.getId(), List.of()))));
    }

    /**
     * The rejection the actor is still living under at a table, for the notice on that table's detail
     * (ADR-09 §7): present only when their <b>latest</b> application there was rejected, so applying
     * again - or withdrawing the new one - clears it. Two small reads for one table, nothing per row.
     *
     * <p>The lookup is keyed by the actor, who comes from the token (ADR-05 §5): it can only ever
     * return their own rejection, never another applicant's.
     *
     * @param gameTableId the table whose detail is being read
     * @param actorId     the reader, from the token
     * @return their rejection, or null when they never applied, their latest application is not a
     *         rejection, or it carries no rejection row
     */
    @Transactional(readOnly = true)
    public @Nullable MyRejectionResponse findMyRejection(String gameTableId, String actorId) {
        return registrationRepository.findFirstByGameTable_IdAndUser_IdOrderByCreatedAtDescIdDesc(gameTableId, actorId)
                .filter(registration -> registration.getStatus() == TableRegistrationStatus.Rejected)
                .flatMap(registration -> rejectionRepository.findByRegistration_Id(registration.getId()))
                .map(rejection -> {
                    // Same split as listMine: rejected_by null marks the system's own rejection, whose
                    // stored text is a code and not words anybody wrote.
                    boolean automatic = rejection.getRejectedBy() == null;
                    return new MyRejectionResponse(
                            automatic ? null : rejection.getDescription(),
                            automatic ? rejection.getDescription() : null,
                            rejection.getRejectedAt());
                })
                .orElse(null);
    }

    /**
     * Everything the actor applied to, whatever came of it. Backs /my/applications.
     *
     * <p>It is the one place a rejection's justification is loaded, because it is the only screen
     * where the applicant themselves reads it (ADR-09 §7) - the master's queue has no use for it.
     *
     * @param actorId  the actor, from the token (ADR-05 §5)
     * @param pageable page, size and sort
     * @return one page of their applications, each with its rejection reason when there is one
     */
    @Transactional(readOnly = true)
    public PageResponse<RegistrationResponse> listMine(String actorId, Pageable pageable) {
        Page<TableRegistration> page =
                registrationRepository.findByUser_IdAndStatusNotIn(actorId, HIDDEN_FROM_MINE, pageable);
        Map<String, RegistrationRejection> rejectionByRegistrationId = loadRejections(page.getContent());
        Map<String, List<RegistrationFileResponse>> filesByRegistration =
                filesByRegistrationIds(page.getContent().stream().map(TableRegistration::getId).toList());
        return PageResponse.from(page.map(registration -> {
            RegistrationResponse response = registrationMapper.toResponse(
                    registration, filesByRegistration.getOrDefault(registration.getId(), List.of()));
            RegistrationRejection rejection = rejectionByRegistrationId.get(registration.getId());
            if (rejection == null) {
                return response;
            }
            // A rejection the application wrote itself is a code to translate; one a master wrote is
            // their own words and is shown exactly as typed. `rejected_by IS NULL` is what tells the
            // two apart, and it always has (ADR-09 §3, ADR-23 §3).
            boolean automatic = rejection.getRejectedBy() == null;
            return new RegistrationResponse(
                    response.id(), response.gameTableId(), response.gameTableName(), response.userId(), response.userName(),
                    response.status(), response.description(), response.createdAt(),
                    automatic ? null : rejection.getDescription(),
                    automatic ? rejection.getDescription() : null,
                    response.attachedFiles(),
                    // No veto ever travels here: this is the applicant's own list, and it never
                    // contains a Blocked row - `HIDDEN_FROM_MINE` takes those out (ADR-09 §4).
                    null, null, null);
        }));
    }

    /**
     * The table's roster, as the people running it need it - its players, <b>and the people vetoed
     * from it</b>.
     *
     * <p>A different question from {@link #listCandidatesForTable}, which answers with the queue
     * waiting to get in (ADR-09 §1). Nothing could ask this one until F1.5 needed it: addressing a task to
     * one player (ADR-15 §2) means being able to choose among them, and offering the platform's whole user
     * directory there would be offering people who cannot be chosen.
     *
     * <p><b>A vetoed row stays here</b> (ADR-09 §5, F3.4). It is the one screen where the veto can be
     * lifted, and a veto that vanishes from the interface is not reversible in practice however
     * reversible the backend makes it. Each such row carries who decided it, when, and why - because
     * the master reading it six months later is deciding whether that still stands.
     *
     * <p><b>A veto has two reasons, and the row carries both</b>: the admin's note that decided it,
     * and the justification of the master who asked for it, with who asked. Both come in a fixed
     * number of queries for the whole roster. A veto with no approved request behind it - older than
     * the request flow - carries only the admin's side. The vetoed person reads none of this: the
     * table answers them 404.
     *
     * <p>It does not widen anything else: {@code TableTaskService} asks for a live {@code Player}
     * registration before it will address a task to somebody, so a vetoed person cannot be chosen in
     * the picker that reads this same list.
     *
     * <p>A list and not a page: it is bounded by {@code max_players} and read as one roster.
     *
     * @param gameTableId the table
     * @param actorId     the actor, from the token
     * @return its players and its vetoed, oldest first
     * @throws NotFoundException        if the table does not exist, or the actor cannot see it - the
     *                                  same answer as a table that does not exist
     * @throws ForbiddenActionException if the actor sees the table but neither runs it nor is an admin
     */
    @Transactional(readOnly = true)
    public List<TablePlayerResponse> listPlayersForTable(String gameTableId, String actorId) {
        // Read by the table's masters and by any admin (ADR-06 §8); its writes stay the masters' own.
        tableVisibilityService.requireExisting(gameTableId);
        if (!masterService.canOversee(gameTableId, actorId)) {
            // Said only about a table they may see: to anybody else it is the same 404 as an id nobody used.
            tableVisibilityService.requireVisible(gameTableId, actorId);
            throw new ForbiddenActionException("Only a master of this table or an admin can view its players");
        }
        List<TableRegistration> roster = registrationRepository.findByGameTable_IdAndStatusInOrderByCreatedAtAsc(
                gameTableId, List.of(TableRegistrationStatus.Player, TableRegistrationStatus.Blocked));
        Map<String, RegistrationStatusChange> vetoes = latestVetoesOf(roster);
        Map<String, ApprovalRequest> vetoRequests = latestApprovedVetoRequestsOf(roster);

        return roster.stream()
                .map(registration -> {
                    boolean blocked = registration.getStatus() == TableRegistrationStatus.Blocked;
                    RegistrationStatusChange veto = blocked ? vetoes.get(registration.getId()) : null;
                    ApprovalRequest request = blocked ? vetoRequests.get(registration.getId()) : null;
                    return new TablePlayerResponse(
                            registration.getId(),
                            registration.getUser().getId(),
                            registration.getUser().getDiscordUsername(),
                            registration.getStatus().name(),
                            veto == null ? null : displayNameOf(veto.getChangedBy()),
                            veto == null ? null : veto.getCreatedAt(),
                            veto == null ? null : veto.getJustification(),
                            request == null ? null : displayNameOf(request.getRequestedBy()),
                            request == null ? null : request.getJustification());
                })
                .toList();
    }

    /**
     * Vetoing somebody from a table: {@code Player} to {@code Blocked}.
     *
     * <p>Private, and reached only through {@link #applyApprovedBlock}: nobody vetoes directly. A
     * master asks for a veto and an admin decides it, so by the time this runs the decision was already
     * authorized by whoever resolved the request.
     *
     * <p>Under the table's lock, like every other transition here: two admins approving vetoes on the
     * same person at the same instant must produce one veto and one 409, not two rows in the trail.
     *
     * @param gameTableId    the table the application must belong to
     * @param registrationId the application to veto
     * @param actorId        the admin who approved the veto, from the token
     * @param justification  why, never blank - it is what lets the veto be undone knowingly
     * @return the application, now {@code Blocked}, with who vetoed it and when
     * @throws NotFoundException 404 when the application does not belong to that table
     * @throws ConflictException 409 {@code REGISTRATION_ALREADY_BLOCKED} when it already is, or a plain
     *                           conflict for an application that is not a {@code Player} - a candidate,
     *                           or one that is over, including a player who stopped being one while the
     *                           request waited
     */
    private RegistrationResponse block(
            String gameTableId, String registrationId, String actorId, String justification) {
        // The lock first, and the application read after it: read before the wait, it would still say
        // Player once a concurrent veto had committed, and the person would be vetoed twice.
        lockTable(gameTableId);
        TableRegistration registration = requireRegistrationOf(gameTableId, registrationId);

        TableRegistrationStatus from = registration.getStatus();
        if (from == TableRegistrationStatus.Blocked) {
            throw new ConflictException(
                    "Registration " + registrationId + " is already blocked",
                    ConflictException.REGISTRATION_ALREADY_BLOCKED);
        }
        if (from != BLOCKABLE_STATUS) {
            throw new ConflictException("A registration in status " + from + " cannot be blocked");
        }

        return moveTo(registration, from, TableRegistrationStatus.Blocked, actorId, justification);
    }

    /**
     * Lifting a veto: {@code Blocked} back to wherever the person was.
     *
     * <p><b>Back to where they were, read from the trail</b>: {@code from_status} of the last move into
     * {@code Blocked}. Today that is always {@code Player}, since only players are vetoed; the trail is
     * still what is read so a veto an older version wrote against a candidate lifts back to
     * {@code Candidate} as it always did.
     *
     * <p><b>The table's {@code Primary} or an admin.</b> The veto itself is decided by an admin, so an
     * admin can undo it; the {@code Primary} runs the table and answers for who sits at it. A
     * co-master gets {@code 403 NOT_PRIMARY_MASTER}.
     *
     * <p><b>Except for the seat.</b> A vetoed player frees their seat, and the table may have filled it
     * since. Lifting the veto never fails on that account: a person coming back to a full table lands
     * as {@code Rejected}, with the same system reason a full table gives every remaining candidate,
     * so they see the table again and can apply once a seat frees. Coming back as {@code Player} and
     * completing the seats closes the table to the remaining candidates, exactly as accepting the last
     * one does.
     *
     * @param gameTableId    the table the application must belong to
     * @param registrationId the application
     * @param actorId        the actor, from the token; has to be the table's {@code Primary} or an admin
     * @param justification  why the veto is being lifted, never blank
     * @return the application, back to {@code Player} - or {@code Rejected} when the table has no seat left
     * @throws NotFoundException        404 when the table does not exist or the actor cannot see it - the
     *                                  same answer as a table that does not exist - or when the
     *                                  application does not belong to that table
     * @throws ForbiddenActionException 403 {@code NOT_PRIMARY_MASTER} when the actor sees the table but is
     *                                  neither the Primary nor an admin
     * @throws ConflictException        409 {@code REGISTRATION_NOT_BLOCKED} when it is not vetoed
     */
    @Transactional
    public RegistrationResponse unblock(
            String gameTableId, String registrationId, String actorId, String justification) {
        GameTable table = lockTable(gameTableId);
        // Who first, then which row: somebody who does not run the table gets the same 403 whatever id
        // they send, and cannot learn from a 404 which applications belong to it.
        requirePrimaryOrAdmin(gameTableId, actorId);
        TableRegistration registration = requireRegistrationOf(gameTableId, registrationId);

        if (registration.getStatus() != TableRegistrationStatus.Blocked) {
            throw new ConflictException(
                    "Registration " + registrationId + " is " + registration.getStatus() + " and is not blocked",
                    ConflictException.REGISTRATION_NOT_BLOCKED);
        }

        // Where they were before the veto. The fallback is Player and it is only reachable for a row
        // blocked by something that left no trail, which nothing in the application does.
        TableRegistrationStatus to = statusChangeRepository
                .findFirstByRegistration_IdAndToStatusOrderByCreatedAtDesc(registrationId, TableRegistrationStatus.Blocked)
                .map(RegistrationStatusChange::getFromStatus)
                .orElse(TableRegistrationStatus.Player);
        if (isFull(table)) {
            to = TableRegistrationStatus.Rejected;
            rejectionRepository.save(new RegistrationRejection(registration, AUTO_REJECT_REASON_CODE, null));
        }

        RegistrationResponse response = moveTo(registration, TableRegistrationStatus.Blocked, to, actorId, justification);
        if (to == TableRegistrationStatus.Player && isFull(table)) {
            onSeatsFilled(table);
        }
        return response;
    }

    /**
     * The veto a master asked for, applied once an admin approved it.
     *
     * <p>Called by {@code ApprovalService} when a {@code PlayerBan} request is approved, and it is the
     * only way {@code Blocked} is ever written: a second path would be a second copy of the rules, or -
     * worse - a version without them. Who may approve is the admin endpoint's annotation; this resolves
     * the table from the application and applies the veto under that table's lock.
     *
     * @param registrationId the application the request pointed at
     * @param adminId        the admin who approved it, from the token
     * @param justification  the resolution note, which becomes the veto's reason
     * @return the application, now {@code Blocked}
     */
    @Transactional
    public RegistrationResponse applyApprovedBlock(String registrationId, String adminId, String justification) {
        return block(tableIdOf(registrationId), registrationId, adminId, justification);
    }

    /**
     * The table an application belongs to, for the callers that have a registration id and need its
     * table - to lock it, or to link it.
     *
     * <p>{@code ApprovalService} is the caller: a {@code PlayerBan} points at a
     * {@code table_registration}, and resolving it means taking that table's lock first. Exposed here
     * rather than letting another service reach into {@code table_registrations}.
     *
     * <p>Read as a projection and never as the entity: the writes that need it look it up before
     * taking the table's lock, and an entity loaded then would sit in the persistence context with its
     * pre-lock state, to be handed back - stale - by every later read of the same row.
     *
     * @param registrationId the application
     * @return the id of the table it belongs to
     * @throws NotFoundException when no application has that id
     */
    @Transactional(readOnly = true)
    public String tableIdOf(String registrationId) {
        return registrationRepository.findTablesByRegistrationIds(List.of(registrationId)).stream()
                .findFirst()
                .map(RegistrationTable::gameTableId)
                .orElseThrow(() -> registrationNotFound(registrationId));
    }

    /**
     * Who each of several applications is from, and its table, in one query.
     *
     * <p>{@code ApprovalService} and the admin tray are the callers: a veto request is about a third
     * person, and an admin deciding it has to read whom it is about and reach the table, on every line
     * of a page, without one lookup per line.
     *
     * @param registrationIds the applications
     * @return the subject by application id; an application that does not exist is absent
     */
    @Transactional(readOnly = true)
    public Map<String, RegistrationSubject> subjectsOf(java.util.Collection<String> registrationIds) {
        if (registrationIds.isEmpty()) {
            return Map.of();
        }
        return registrationRepository.findSubjectsByRegistrationIds(registrationIds).stream()
                .collect(Collectors.toMap(RegistrationSubject::registrationId, subject -> subject));
    }

    /**
     * Who every application of one table is from, by application id, in one query.
     *
     * <p>Read by {@code ApprovalService} for two things at once, and both are the price of the
     * polymorphic reference: {@code entity_id} on {@code approval_requests} is a plain column with no
     * foreign key, so there is nothing to join through - the set has to be resolved first to scope the
     * query, <b>and</b> the subjects are what turn a request into a sentence about a person. A veto
     * request that cannot name who it is about is one a reader with two of them open can only tell
     * apart by the wording of the reason.
     *
     * <p>Withdrawn applications are included, like in {@link #subjectsOf}: a veto request about
     * somebody who took their application back is still waiting on an admin, and it still has to say
     * whom it is about.
     *
     * @param gameTableId the table
     * @return the subject of each of its applications, withdrawn ones included; empty when it has none
     */
    @Transactional(readOnly = true)
    public Map<String, RegistrationSubject> subjectsOfTable(String gameTableId) {
        return registrationRepository.findSubjectsByGameTableId(gameTableId).stream()
                .collect(Collectors.toMap(RegistrationSubject::registrationId, subject -> subject));
    }

    /**
     * Confirms an application of this table is one a veto could still be asked for.
     *
     * <p>Asked by {@code ApprovalService} <em>before</em> it writes a {@code PlayerBan} request, under
     * the table's lock the caller already holds, so a master is refused at the moment they ask rather
     * than after an admin has read it. It is the same question {@link #block} asks.
     *
     * @param gameTableId    the table the application must belong to
     * @param registrationId the application
     * @throws NotFoundException 404 when the application does not belong to that table
     * @throws ConflictException 409 {@code REGISTRATION_ALREADY_BLOCKED} when it already is {@code Blocked},
     *                           or a plain conflict when it is not a {@code Player}
     */
    @Transactional(readOnly = true)
    public void requireBlockableOn(String gameTableId, String registrationId) {
        TableRegistrationStatus status = requireRegistrationOf(gameTableId, registrationId).getStatus();
        if (status == TableRegistrationStatus.Blocked) {
            throw new ConflictException(
                    "Registration " + registrationId + " is already blocked",
                    ConflictException.REGISTRATION_ALREADY_BLOCKED);
        }
        if (status != BLOCKABLE_STATUS) {
            throw new ConflictException("A registration in status " + status + " cannot be blocked");
        }
    }

    /**
     * The application, confirmed to belong to the table the caller named.
     *
     * <p>The veto endpoints hang off the table, because the authorization is about <b>this</b> table
     * and the table has to be readable for that to mean anything. Resolving the table from the
     * application instead would make the path segment decorative - a URL could name one table and act
     * on a row belonging to another, and the check would still pass because it was never asked about
     * what the URL said.
     *
     * <p>404 and not 400 for a mismatch: a row that is not this table's is not this caller's business
     * to learn the existence of.
     */
    private TableRegistration requireRegistrationOf(String gameTableId, String registrationId) {
        TableRegistration registration = getRegistrationById(registrationId);
        if (!gameTableId.equals(registration.getGameTable().getId())) {
            throw registrationNotFound(registrationId);
        }
        return registration;
    }

    /**
     * The one write both directions of the veto share: the status moves and the trail gets its row.
     *
     * <p>Written once rather than twice for the same reason {@code ApprovalService.resolve} is: the
     * difference between vetoing and lifting is the direction, not the bookkeeping, and two copies of
     * the bookkeeping is how one of them ends up not writing the trail.
     */
    private RegistrationResponse moveTo(
            TableRegistration registration,
            TableRegistrationStatus from,
            TableRegistrationStatus to,
            String actorId,
            String justification) {
        User changedBy = userService.getById(actorId);
        registration.setStatus(to);
        statusChangeRepository.save(new RegistrationStatusChange(registration, from, to, changedBy, justification));

        RegistrationResponse response =
                registrationMapper.toResponse(registration, filesOf(registration.getId()));
        if (to != TableRegistrationStatus.Blocked) {
            return response;
        }
        return withVeto(response, displayNameOf(changedBy), LocalDateTime.now(), justification);
    }

    /**
     * The latest veto of each application on a page, in one query.
     *
     * <p>Oldest first from the repository and overwritten as it goes, so what survives per id is the
     * most recent - the same trick the rejection map uses, and cheaper than a per-row lookup on a
     * screen that lists a whole roster.
     */
    private Map<String, RegistrationStatusChange> latestVetoesOf(List<TableRegistration> registrations) {
        List<String> blockedIds = registrations.stream()
                .filter(registration -> registration.getStatus() == TableRegistrationStatus.Blocked)
                .map(TableRegistration::getId)
                .toList();
        if (blockedIds.isEmpty()) {
            return Map.of();
        }
        Map<String, RegistrationStatusChange> latest = new HashMap<>();
        for (RegistrationStatusChange change :
                statusChangeRepository.findByRegistration_IdInAndToStatusOrderByCreatedAtAsc(
                        blockedIds, TableRegistrationStatus.Blocked)) {
            latest.put(change.getRegistration().getId(), change);
        }
        return latest;
    }

    /**
     * The latest approved veto request of each vetoed application on a roster, in one query.
     *
     * <p>The same overwrite-as-it-goes trick as {@link #latestVetoesOf}, with each requester already
     * loaded by the repository. A veto older than the request flow has nothing behind it and is simply
     * absent from the map.
     */
    private Map<String, ApprovalRequest> latestApprovedVetoRequestsOf(List<TableRegistration> registrations) {
        List<String> blockedIds = registrations.stream()
                .filter(registration -> registration.getStatus() == TableRegistrationStatus.Blocked)
                .map(TableRegistration::getId)
                .toList();
        if (blockedIds.isEmpty()) {
            return Map.of();
        }
        Map<String, ApprovalRequest> latest = new HashMap<>();
        for (ApprovalRequest request : approvalRequestRepository.findByRequestTypeAndStatusAndEntityIdInOrderByCreatedAtAsc(
                ApprovalRequestType.PlayerBan, ApprovalStatus.Approved, blockedIds)) {
            latest.put(request.getEntityId(), request);
        }
        return latest;
    }

    /** The three veto fields, put onto a response the mapper left empty. */
    private static RegistrationResponse withVeto(
            RegistrationResponse response, String blockedByName, LocalDateTime blockedAt, String justification) {
        return new RegistrationResponse(
                response.id(), response.gameTableId(), response.gameTableName(), response.userId(),
                response.userName(), response.status(), response.description(),
                response.createdAt(), response.rejectionJustification(), response.rejectionReasonCode(),
                response.attachedFiles(), blockedByName, blockedAt, justification);
    }

    /** The screen shows a person, not an id - the same fallback every mapper of the project uses. */
    private static String displayNameOf(User user) {
        return user.getName() != null ? user.getName() : user.getDiscordUsername();
    }

    /**
     * Who may lift a veto: the table's {@code Primary}, who answers for who sits at it, or an admin,
     * who decided the veto in the first place.
     *
     * <p>Every other master operation here settles for {@code isMasterOf}; lifting a veto is narrower,
     * because it undoes a decision about somebody not being welcome at the table.
     *
     * @throws NotFoundException        404 when the actor cannot see the table, checked before the
     *                                  refusal so that it never confirms a table they cannot see
     * @throws ForbiddenActionException 403 {@code NOT_PRIMARY_MASTER}, never a 400 - it is who you
     *                                  are, not what you sent
     */
    private void requirePrimaryOrAdmin(String gameTableId, String actorId) {
        if (masterService.isPrimaryOf(gameTableId, actorId)) {
            return;
        }
        Set<String> roles = userService.loadAuthSnapshot(actorId).roles();
        if (roles.contains(PlatformRole.ADMIN.roleName()) || roles.contains(PlatformRole.OWNER.roleName())) {
            return;
        }
        // Said only about a table they may see: to anybody else it is the same 404 as an id nobody used.
        tableVisibilityService.requireVisible(gameTableId, actorId);
        throw new ForbiddenActionException(
                "Only the Primary master or an admin can lift a veto on table " + gameTableId,
                ForbiddenActionException.NOT_PRIMARY_MASTER);
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
     * R4 (ADR-08 §2): now that this person plays here, tell them which of their other pending
     * applications fall at the same hour.
     *
     * <p><b>Told, not rejected.</b> Somebody sends three applications to see which one comes
     * through, and until one does there is no commitment to defend; deciding for them which to drop
     * would be the system making a choice that is theirs, the same reasoning as ADR-15 §2. A table where
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

    /**
     * What happens the moment the last seat is taken: every remaining candidate is turned down with
     * the system's reason, and a table that was looking for players stops looking.
     *
     * <p>The one definition of that effect, for the three ways a table fills up: accepting the
     * candidate who completes it, lifting a veto that brings a player back into the last seat, and an
     * admin lowering the capacity to the players already in ({@code GameTableService.adminUpdate}).
     * Public for that last caller; the caller must already hold the table's lock, taken before it read
     * the player count that told it the seats are all taken.
     *
     * @param table the table whose seats are all taken, locked by the caller
     */
    public void onSeatsFilled(GameTable table) {
        List<TableRegistration> remaining =
                registrationRepository.findByGameTable_IdAndStatusOrderByCreatedAtAsc(table.getId(), TableRegistrationStatus.Candidate);
        for (TableRegistration candidate : remaining) {
            candidate.setStatus(TableRegistrationStatus.Rejected);
            rejectionRepository.save(new RegistrationRejection(candidate, AUTO_REJECT_REASON_CODE, null));
            notificationService.notifyRegistrationRejected(candidate.getUser().getId(), table);
        }
        table.setSeekingPlayers(false);
    }

    private void requireMasterOf(TableRegistration registration, String actorId, String action) {
        if (!masterService.isMasterOf(registration.getGameTable().getId(), actorId)) {
            requireSeen(registration, actorId);
            throw new ForbiddenActionException("Only a master of this table can " + action);
        }
    }

    /**
     * What has to hold before somebody is told they may not act on an application: that they see it at
     * all. They see it when they see its table and either run or oversee the table or made the
     * application; anybody else gets the same 404 as an id nobody ever used.
     */
    private void requireSeen(TableRegistration registration, String actorId) {
        String gameTableId = registration.getGameTable().getId();
        boolean seesIt = tableVisibilityService.canSee(gameTableId, actorId)
                && (registration.getUser().getId().equals(actorId) || masterService.canOversee(gameTableId, actorId));
        if (!seesIt) {
            throw registrationNotFound(registration.getId());
        }
    }

    /**
     * {@link #lockTable} for the writes that name an application rather than its table: a deleted table
     * takes its applications with it, so it answers as an application that is not there, never with the
     * id of a table the caller did not name.
     */
    private GameTable lockTableOf(String registrationId) {
        return gameTableRepository.findByIdForUpdate(tableIdOf(registrationId))
                .filter(table -> table.getStatus() != GameTableStatus.Deleted)
                .orElseThrow(() -> registrationNotFound(registrationId));
    }

    /** One sentence for an application that is not there and one the actor may not see. */
    private static NotFoundException registrationNotFound(String registrationId) {
        return new NotFoundException("Registration not found: " + registrationId);
    }

    /**
     * Takes the row lock that every write to a table's applications serializes on, and returns the
     * table read under it.
     *
     * <p>Public for the one other writer of that state: asking for, approving and refusing a veto
     * request queue on the same lock, and take it <em>before</em> the request's - the one order every
     * veto path follows, so each reads under it what the others committed. A deleted table does not
     * exist for any read, so it is a 404 here too and never a conflict that would confirm it is there.
     *
     * @param gameTableId the table
     * @return the table, locked until the transaction ends
     * @throws NotFoundException when there is no such table, or it was deleted
     */
    @Transactional
    public GameTable lockTable(String gameTableId) {
        return gameTableRepository.findByIdForUpdate(gameTableId)
                .filter(table -> table.getStatus() != GameTableStatus.Deleted)
                .orElseThrow(() -> new NotFoundException("Table not found: " + gameTableId));
    }

    /**
     * Takes the same row lock as {@link #lockTable}, but does not refuse a deleted table.
     *
     * <p>For the one caller that has to keep the lock order on a table that may be gone: resolving a
     * veto request about an application that went away with its table. Approving it is still refused,
     * because what it points at no longer exists; refusing it is what takes it out of the tray.
     *
     * @param gameTableId the table
     */
    @Transactional
    public void lockTableRowEvenIfDeleted(String gameTableId) {
        gameTableRepository.findByIdForUpdate(gameTableId);
    }

    /**
     * Applying and accepting refuse a full table outright; lifting a veto, the third way a player can
     * come back, is not refused but lands the person as a rejected application instead (see
     * {@link #unblock}).
     */
    private void requireSeatLeft(GameTable table) {
        if (isFull(table)) {
            throw new ConflictException("Table " + table.getId() + " has no seat left", ConflictException.TABLE_FULL);
        }
    }

    /**
     * Only players count against max_players - a candidate holds no seat - so this is the count of
     * players, asked under the table's lock by every path that could add one.
     */
    private boolean isFull(GameTable table) {
        Integer maxPlayers = table.getMaxPlayers();
        return maxPlayers != null
                && registrationRepository.countByGameTable_IdAndStatus(table.getId(), TableRegistrationStatus.Player) >= maxPlayers;
    }

    private TableRegistration getRegistrationById(String registrationId) {
        return registrationRepository.findById(registrationId)
                .orElseThrow(() -> registrationNotFound(registrationId));
    }
}
