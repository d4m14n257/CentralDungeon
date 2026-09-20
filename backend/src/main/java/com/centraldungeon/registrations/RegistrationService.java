package com.centraldungeon.registrations;

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
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
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
 *
 * <p><b>An application may carry files</b> (#60 uso 2): the character sheet a candidate applies
 * with, linked rather than copied (#79). There is no endpoint of its own for them - they travel
 * inside {@link RegistrationResponse}, the same criterion F1.3 used for a table's sessions and F1.4
 * for its shared files, because this class already decides who may see an application.
 *
 * <p><b>Withdrawing never touches {@code registration_files}</b> (#247, deliberate): the row is the
 * record that a sheet was sent, and withdrawing does not undo that it was. What stops counting a
 * withdrawn application's file as in use is the read side of {@link RegistrationFileRepository},
 * never a cascade here - see {@link #withdraw}.
 */
@Service
public class RegistrationService {

    /** The statuses that make a registration count as alive for the one-per-pair rule (#28). */
    private static final List<TableRegistrationStatus> ACTIVE_STATUSES =
            List.of(TableRegistrationStatus.Candidate, TableRegistrationStatus.Player);

    /**
     * The statuses a veto can be applied to (#39): somebody who is <em>currently</em> involved.
     *
     * <p>Rejected and Deleted are not here and it is not an oversight - there is nothing to veto,
     * and «no ve esa mesa» is already true of somebody who never got in. Vetoing them would put a
     * row in the trail that denies nothing.
     */
    private static final List<TableRegistrationStatus> BLOCKABLE_STATUSES =
            List.of(TableRegistrationStatus.Candidate, TableRegistrationStatus.Player);

    /**
     * What {@code /my/applications} never shows (#25, #29).
     *
     * <p>{@code Blocked} sits beside {@code Deleted} by decision of the F3.4 contract: the row
     * carries {@code gameTableName}, so leaving it in would be the single crack through which a
     * vetoed person keeps seeing the table the rest of the slice answers them 404 about.
     */
    private static final List<TableRegistrationStatus> HIDDEN_FROM_MINE =
            List.of(TableRegistrationStatus.Deleted, TableRegistrationStatus.Blocked);

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

    /** The files attached to an application - linked, never copied (#65, #79, #60 uso 2). */
    private final RegistrationFileRepository registrationFileRepository;

    /** Resolves and authorizes each file being attached, and puts it in its cajón (#233). */
    private final FileService fileService;

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

    /** The veto's trail: every block and every lifting, with its reason (#39). */
    private final RegistrationStatusChangeRepository statusChangeRepository;

    /**
     * The one gate every read of a concrete table goes through (#25, #29).
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
     * @param registrationRepository     the {@code table_registrations} table
     * @param rejectionRepository        the reasons behind turned-down applications
     * @param registrationFileRepository the files attached to an application (#60 uso 2)
     * @param fileService                resolves and authorizes each file being attached (#79)
     * @param gameTableRepository         used to lock the table row the invariants serialize on
     * @param masterService               answers pertenencia
     * @param userService                 resolves the people involved
     * @param notificationService        tells the applicant and the masters what happened
     * @param scheduleConflictService answers whether the applicant is already busy at that hour (#178)
     * @param statusChangeRepository the veto's trail (#39)
     * @param tableVisibilityService the one gate of #25 and #29, so applying cannot walk around it
     * @param registrationMapper     entity to DTO
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
            RegistrationMapper registrationMapper) {
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
    }

    /**
     * Locks the table row before checking: it is the only way to serialize concurrent applications
     * for the same table and make "at most one active registration per pair" (#28) actually hold.
     *
     * <p><b>The lock is the first statement of the transaction, and that ordering is load-bearing</b>
     * (#252). F3.4 briefly put the veto check in front of it and the invariant above stopped holding
     * - ten concurrent applications all succeeded. The mechanism is the one #252 wrote down: under
     * {@code REPEATABLE READ} the first read opens the snapshot, so a thread that read <em>before</em>
     * queueing for the lock wakes up and answers the duplicate check from a snapshot taken before the
     * winner committed. Anything that reads a row this method will re-read goes after the lock.
     */
    @Transactional
    public RegistrationResponse apply(String gameTableId, String actorId, CreateRegistrationRequest request) {
        GameTable table = lockTable(gameTableId);
        // After the lock, for the reason above - but still through the single point, so that the
        // answer is the same 404 every other read of a vetoed table gives (#29). «Volver a
        // postularse» needs no rule of its own: this endpoint was never on anybody's list of read
        // paths and is closed anyway.
        tableVisibilityService.requireVisible(gameTableId, actorId);

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
        List<RegistrationFileResponse> files = attachFiles(registration, request.fileIds(), actorId);

        String applicantName = actor.getName() != null ? actor.getName() : actor.getDiscordUsername();
        for (Master master : masterService.findByGameTable(gameTableId)) {
            notificationService.notifyNewCandidate(master.getUser().getId(), table, applicantName);
        }

        return registrationMapper.toResponse(registration, files);
    }

    /**
     * Links whatever the applicant attached to their application (#60 uso 2), and puts each file in
     * the {@code PlayerApplication} cajón (#233).
     *
     * <p>The same gate attaching a file to a table or an answer goes through: the applicant's own, or
     * one the platform published (#79). Somebody else's private upload never gets here.
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
            // The cajón is a consequence of the link, not of the upload (#233): add-only and
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
     * The attached files of a whole page of applications, in one query (#232).
     *
     * <p>A file its owner has since deleted is left out rather than shown as a broken row - the same
     * thing {@code TaskSubmissionService} does for a task's answers, and for the same reason: an
     * owner removing a file removes it from what shows it (#25).
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

        return registrationMapper.toResponse(registration, filesOf(registration.getId()));
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

        return registrationMapper.toResponse(registration, filesOf(registration.getId()));
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
     * <p><b>Deliberately does not touch {@code registration_files}</b> (#247): whatever the
     * candidate attached stays attached, marked or not. The row is the record that a sheet was sent,
     * and withdrawing does not undo that it was - only {@link RegistrationFileRepository}'s read
     * methods stop counting it as in use, by requiring the application to still be
     * {@code Candidate} or {@code Player}.
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
        Map<String, List<RegistrationFileResponse>> filesByRegistration =
                filesByRegistrationIds(page.getContent().stream().map(TableRegistration::getId).toList());
        return PageResponse.from(page.map(registration ->
                registrationMapper.toResponse(registration, filesByRegistration.getOrDefault(registration.getId(), List.of()))));
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
            // two apart, and it always has (#34, #197).
            boolean automatic = rejection.getRejectedBy() == null;
            return new RegistrationResponse(
                    response.id(), response.gameTableId(), response.gameTableName(), response.userId(), response.userName(),
                    response.userKarma(), response.status(), response.description(), response.createdAt(),
                    automatic ? null : rejection.getDescription(),
                    automatic ? rejection.getDescription() : null,
                    response.attachedFiles(),
                    // No veto ever travels here: this is the applicant's own list, and it never
                    // contains a Blocked row - `HIDDEN_FROM_MINE` takes those out (#29).
                    null, null, null);
        }));
    }

    /**
     * The table's roster, as the people running it need it - its players, <b>and the people vetoed
     * from it</b>.
     *
     * <p>A different question from {@link #listCandidatesForTable}, which answers with the queue
     * waiting to get in (#28). Nothing could ask this one until F1.5 needed it: addressing a task to
     * one player (#76) means being able to choose among them, and offering the platform's whole user
     * directory there would be offering people who cannot be chosen.
     *
     * <p><b>A vetoed row stays here</b> (#39, F3.4). It is the one screen where the veto can be
     * lifted, and a veto that vanishes from the interface is not reversible in practice however
     * reversible the backend makes it. Each such row carries who decided it, when, and why - because
     * the master reading it six months later is deciding whether that still stands.
     *
     * <p>It does not widen anything else: {@code TableTaskService} asks for a live {@code Player}
     * registration before it will address a task to somebody, so a vetoed person cannot be chosen in
     * the picker that reads this same list.
     *
     * <p>A list and not a page: it is bounded by {@code max_players} and read as one roster.
     *
     * @param gameTableId the table
     * @param actorId     the actor, from the token (#121)
     * @return its players and its vetoed, oldest first
     * @throws ForbiddenActionException if the actor does not run the table (#17, #135)
     */
    @Transactional(readOnly = true)
    public List<TablePlayerResponse> listPlayersForTable(String gameTableId, String actorId) {
        requireMasterOf(gameTableId, actorId, "view its players");
        List<TableRegistration> roster = registrationRepository.findByGameTable_IdAndStatusInOrderByCreatedAtAsc(
                gameTableId, List.of(TableRegistrationStatus.Player, TableRegistrationStatus.Blocked));
        Map<String, RegistrationStatusChange> vetoes = latestVetoesOf(roster);

        return roster.stream()
                .map(registration -> {
                    RegistrationStatusChange veto = registration.getStatus() == TableRegistrationStatus.Blocked
                            ? vetoes.get(registration.getId())
                            : null;
                    return new TablePlayerResponse(
                            registration.getId(),
                            registration.getUser().getId(),
                            registration.getUser().getDiscordUsername(),
                            registration.getUser().getKarma(),
                            registration.getStatus().name(),
                            veto == null ? null : displayNameOf(veto.getChangedBy()),
                            veto == null ? null : veto.getCreatedAt(),
                            veto == null ? null : veto.getJustification());
                })
                .toList();
    }

    /**
     * Vetoing somebody from a table. {@code Candidate} or {@code Player} to {@code Blocked} (#39).
     *
     * <p><b>Only the {@code Primary}</b>, and the check is {@code isPrimaryOf} and not
     * {@code isMasterOf} - which is the difference between this and every other master operation on
     * an application. #39 is explicit: the veto is the {@code Primary}'s, and a co-master asks for
     * one, through {@code approval_requests}. A {@code Secondary}
     * calling this gets a {@code 403 NOT_PRIMARY_MASTER}: it is who they are, not what they sent.
     *
     * <p><b>The authorization lives here and not in a {@code @PreAuthorize}</b>, for the reason the
     * whole project keeps repeating: being <em>this</em> table's Primary is a row in {@code masters}
     * and no annotation can see it (#17, #121, #135). Same principle as F3.1 - «el otorgamiento es la
     * regla, no la puerta».
     *
     * <p>Under the table's lock, like every other transition here: two masters vetoing the same
     * person at the same instant must produce one veto and one 409, not two rows in the trail.
     *
     * @param gameTableId    the table the application must belong to - the one the authorization is
     *                        about, and therefore the one the path names
     * @param registrationId the application to veto
     * @param actorId        the actor, from the token; has to be the table's {@code Primary}
     * @param justification  why, never blank - #39 makes it the thing that lets the veto be undone
     * @return the application, now {@code Blocked}, with who vetoed it and when
     * @throws NotFoundException        404 when the application does not belong to that table
     * @throws ForbiddenActionException 403 {@code NOT_PRIMARY_MASTER} when the actor is not the
     *                                  table's Primary
     * @throws ConflictException        409 {@code REGISTRATION_ALREADY_BLOCKED} when it already is,
     *                                  or a plain conflict for an application that is over
     */
    @Transactional
    public RegistrationResponse block(
            String gameTableId, String registrationId, String actorId, String justification) {
        TableRegistration registration = requireRegistrationOf(gameTableId, registrationId);
        lockTable(gameTableId);
        requirePrimaryOf(gameTableId, actorId, "veto somebody from this table");

        TableRegistrationStatus from = registration.getStatus();
        if (from == TableRegistrationStatus.Blocked) {
            throw new ConflictException(
                    "Registration " + registrationId + " is already blocked",
                    ConflictException.REGISTRATION_ALREADY_BLOCKED);
        }
        if (!BLOCKABLE_STATUSES.contains(from)) {
            throw new ConflictException("A registration in status " + from + " cannot be blocked");
        }

        return moveTo(registration, from, TableRegistrationStatus.Blocked, actorId, justification);
    }

    /**
     * Lifting a veto. {@code Blocked} back to wherever the person was (#39).
     *
     * <p><b>Back to where they were, read from the trail</b> - not "back to {@code Player}". A
     * candidate who was vetoed while still queued goes back to {@code Candidate}, and the only place
     * that knows which of the two it was is {@code from_status} of the last move into
     * {@code Blocked}. This is the concrete thing a {@code blocked_reason} column could not have
     * done, and the reason F3.4 added a table.
     *
     * <p>Same actor as {@link #block}: reversibility that a different person has to perform is not
     * reversibility of the act, it is an appeal.
     *
     * @param gameTableId    the table the application must belong to
     * @param registrationId the application
     * @param actorId        the actor, from the token; has to be the table's {@code Primary}
     * @param justification  why the veto is being lifted, never blank
     * @return the application, back where it was
     * @throws NotFoundException        404 when the application does not belong to that table
     * @throws ForbiddenActionException 403 {@code NOT_PRIMARY_MASTER} when the actor is not Primary
     * @throws ConflictException        409 {@code REGISTRATION_NOT_BLOCKED} when it is not vetoed
     */
    @Transactional
    public RegistrationResponse unblock(
            String gameTableId, String registrationId, String actorId, String justification) {
        TableRegistration registration = requireRegistrationOf(gameTableId, registrationId);
        lockTable(gameTableId);
        requirePrimaryOf(gameTableId, actorId, "lift a veto on this table");

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

        return moveTo(registration, TableRegistrationStatus.Blocked, to, actorId, justification);
    }

    /**
     * The veto a co-master asked for, applied once the {@code Primary} said yes (#39).
     *
     * <p>Called by {@code ApprovalService} when a {@code PlayerBan} request is approved, and it is
     * the <b>same</b> {@link #block} the direct endpoint uses - deliberately, and for the reason
     * F3.2 wrote down about roles: «el rol queda otorgado por el {@code UserRoleService}, no por una
     * segunda ruta que haga lo mismo». A second path that wrote {@code Blocked} would be a second
     * copy of the rules, or - worse - a version without them.
     *
     * <p>It adds nothing of its own beyond resolving the table from the application - not even the
     * authorization, which {@link #block} checks again on its own. {@code ApprovalService} has
     * already established that the resolver is this table's {@code Primary}, so the second check
     * can only agree; it is left in because a gate that trusts its caller is a gate that stops
     * being one the day a second caller appears.
     *
     * @param registrationId the application the request pointed at
     * @param primaryId      the {@code Primary} who approved it, from the token
     * @param justification  the resolution note, which becomes the veto's reason
     * @return the application, now {@code Blocked}
     */
    @Transactional
    public RegistrationResponse applyApprovedBlock(String registrationId, String primaryId, String justification) {
        return block(tableIdOf(registrationId), registrationId, primaryId, justification);
    }

    /**
     * The table an application belongs to, for the callers that have a registration id and need to
     * know which table's {@code Primary} is entitled to answer for it.
     *
     * <p>{@code ApprovalService} is the caller: a {@code PlayerBan} points at a
     * {@code table_registration}, and deciding whether the actor may resolve it means going from
     * that row to its table. Exposed here rather than letting another service reach into
     * {@code table_registrations}, which is the rule this package exists to keep (regla dura 1).
     *
     * @param registrationId the application
     * @return the id of the table it belongs to
     * @throws NotFoundException when no application has that id
     */
    @Transactional(readOnly = true)
    public String tableIdOf(String registrationId) {
        return getRegistrationById(registrationId).getGameTable().getId();
    }

    /**
     * One application as its readers see it, with its veto described when it has one.
     *
     * <p>Read by {@code ApprovalService} after a {@code Primary} grants a veto request: the screen
     * that has that button is the roster, so what it needs back is the row, not the request.
     *
     * @param registrationId the application
     * @return its response shape
     * @throws NotFoundException when no application has that id
     */
    @Transactional(readOnly = true)
    public RegistrationResponse findResponse(String registrationId) {
        TableRegistration registration = getRegistrationById(registrationId);
        RegistrationResponse response = registrationMapper.toResponse(registration, filesOf(registrationId));
        if (registration.getStatus() != TableRegistrationStatus.Blocked) {
            return response;
        }
        return statusChangeRepository
                .findFirstByRegistration_IdAndToStatusOrderByCreatedAtDesc(registrationId, TableRegistrationStatus.Blocked)
                .map(veto -> withVeto(
                        response, displayNameOf(veto.getChangedBy()), veto.getCreatedAt(), veto.getJustification()))
                .orElse(response);
    }

    /**
     * Every application of one table, by id.
     *
     * <p>Read by {@code ApprovalService} for two things at once, and both are the price of the
     * polymorphic reference (#78): {@code entity_id} on {@code approval_requests} is a plain column
     * with no foreign key, so there is nothing to join through - the set has to be resolved first to
     * scope the query, <b>and</b> the rows are what turn a request into a sentence about a person.
     * A veto request that cannot name who it is about is one a {@code Primary} with two of them open
     * can only tell apart by the wording of the reason.
     *
     * <p>Asked here rather than by reaching into {@code table_registrations} from another package
     * (regla dura 1).
     *
     * @param gameTableId the table
     * @return its applications keyed by id; empty when it has none
     */
    @Transactional(readOnly = true)
    public Map<String, TableRegistration> registrationsOf(String gameTableId) {
        Map<String, TableRegistration> byId = new HashMap<>();
        for (TableRegistration registration : registrationRepository.findByGameTable_Id(gameTableId)) {
            byId.put(registration.getId(), registration);
        }
        return byId;
    }

    /** The display name of whoever an application belongs to - a screen shows a person, not an id. */
    public static String applicantNameOf(TableRegistration registration) {
        return displayNameOf(registration.getUser());
    }

    /**
     * Whether an application is one a veto could still be asked for.
     *
     * <p>Asked by {@code ApprovalService} <em>before</em> it writes a {@code PlayerBan} request, so
     * a co-master is refused at the moment they ask rather than after the {@code Primary} has read
     * it. It is the same question {@link #block} asks, in the shape a caller with no intention of
     * writing anything can use.
     *
     * @param registrationId the application
     * @return true when it is a {@code Candidate} or a {@code Player}
     */
    @Transactional(readOnly = true)
    public boolean isBlockable(String registrationId) {
        return BLOCKABLE_STATUSES.contains(getRegistrationById(registrationId).getStatus());
    }

    /**
     * The application, confirmed to belong to the table the caller named.
     *
     * <p>Both veto endpoints hang off the table, because the authorization is «are you the
     * {@code Primary} of <b>this</b> table» and the table has to be readable for that to mean
     * anything (§2.6, #121). Resolving the table from the application instead would make the path
     * segment decorative - a URL could name one table and act on a row belonging to another, and the
     * check would still pass because it was never asked about what the URL said. No escalation
     * follows from it, but «un path que no puede expresar lo incorrecto no es lo mismo que una regla
     * aplicada» is this controller's own sentence, and this is the rule that makes it true.
     *
     * <p>404 and not 400 for a mismatch, the same answer {@code ApprovalService} gives for a veto
     * request of another table: a row that is not this table's is not this caller's business to
     * learn the existence of.
     */
    private TableRegistration requireRegistrationOf(String gameTableId, String registrationId) {
        TableRegistration registration = getRegistrationById(registrationId);
        if (!gameTableId.equals(registration.getGameTable().getId())) {
            throw new NotFoundException("No registration " + registrationId + " on table " + gameTableId);
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

    /** The three veto fields, put onto a response the mapper left empty. */
    private static RegistrationResponse withVeto(
            RegistrationResponse response, String blockedByName, LocalDateTime blockedAt, String justification) {
        return new RegistrationResponse(
                response.id(), response.gameTableId(), response.gameTableName(), response.userId(),
                response.userName(), response.userKarma(), response.status(), response.description(),
                response.createdAt(), response.rejectionJustification(), response.rejectionReasonCode(),
                response.attachedFiles(), blockedByName, blockedAt, justification);
    }

    /** The screen shows a person, not an id - the same fallback every mapper of the project uses. */
    private static String displayNameOf(User user) {
        return user.getName() != null ? user.getName() : user.getDiscordUsername();
    }

    /**
     * The narrower pertenencia check, and the only place this service asks for it (#39, #71).
     *
     * <p>Every other operation here settles for {@code isMasterOf}, because accepting and rejecting
     * are things a table's co-masters do. The veto is not one of those: it decides that somebody is
     * not welcome at a table, and #39 puts that with whoever runs it.
     *
     * @throws ForbiddenActionException 403 {@code NOT_PRIMARY_MASTER}, never a 400 - it is who you
     *                                  are, not what you sent
     */
    private void requirePrimaryOf(String gameTableId, String actorId, String action) {
        if (!masterService.isPrimaryOf(gameTableId, actorId)) {
            throw new ForbiddenActionException(
                    "Only the Primary master can " + action, ForbiddenActionException.NOT_PRIMARY_MASTER);
        }
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
