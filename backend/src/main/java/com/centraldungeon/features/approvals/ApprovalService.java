package com.centraldungeon.features.approvals;

import com.centraldungeon.features.queue.QueueClaimRule;
import com.centraldungeon.features.approvals.dto.ApprovalRequestDetailResponse;
import com.centraldungeon.features.approvals.dto.ApprovalRequestSummaryResponse;
import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.exception.ForbiddenActionException;
import com.centraldungeon.common.exception.InvalidRequestException;
import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.common.search.SearchQuery;
import com.centraldungeon.common.search.SearchQueryParser;
import com.centraldungeon.common.security.CurrentUser;
import com.centraldungeon.features.notifications.NotificationService;
import com.centraldungeon.features.notifications.NotificationType;
import com.centraldungeon.features.registrations.RegistrationService;
import com.centraldungeon.features.registrations.RegistrationSubject;
import com.centraldungeon.features.registrations.dto.BanRequestResponse;
import com.centraldungeon.features.tables.GameTableService;
import com.centraldungeon.features.tables.MasterService;
import com.centraldungeon.features.tables.TableVisibilityService;
import com.centraldungeon.features.tables.dto.GameTableDetailResponse;
import com.centraldungeon.features.users.PlatformRole;
import com.centraldungeon.features.users.User;
import com.centraldungeon.features.users.UserRepository;
import com.centraldungeon.features.users.UserRoleRepository;
import com.centraldungeon.features.users.UserRoleService;
import com.centraldungeon.features.users.UserService;
import com.centraldungeon.features.users.UserStatus;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one mechanism every request that needs an approval goes through (ADR-16 §1).
 *
 * <p>Four rules carry this class, and none of them is visible in the table:
 *
 * <ul>
 *   <li><b>The referenced entity is validated before anything is inserted</b> (ADR-16 §2). There is
 *       no foreign key behind {@code entity_type}/{@code entity_id} and there cannot be one, so the
 *       check the database would normally do lives here - and it is only two thirds of the price.
 *       The rest is that nothing maps the reference as a {@code @ManyToOne}, and
 *       {@link ApprovalOrphanCheckService}, which sweeps for what stopped resolving. Miss the sweep
 *       and the problem surfaces months later with no way to reconstruct what pointed at what, which
 *       is what this project already lived through with {@code table_files}.</li>
 *   <li><b>One Pending request per type and per person.</b> Without it, a button pressed twice fills
 *       the shared queue with duplicates an admin has to resolve by hand, one at a time.</li>
 *   <li><b>A resolution is never re-resolved.</b> The same shape the table's state machine has, and
 *       the reason two admins answering at once produce one answer and one 409 rather than two
 *       answers.</li>
 *   <li><b>A request another admin reserved is not yours to answer</b> ({@link QueueClaimRule},
 *       ADR-16 §5). Arrived with F3.3. Note what it is <em>not</em>: it does not require having reserved
 *       it, because this screen resolves without ever passing through the shared tray. Correctness
 *       does not rest on it either - the pessimistic lock above is what makes two answers impossible.
 *       This is about not taking work off a colleague's desk.</li>
 *   <li><b>Approving a {@code MasterGrant} grants the role through {@link UserRoleService}</b>
 *       (roles-y-alcance.md §4). Not a second path that writes the same row: who may move which
 *       rank, the exclusion of ADR-06 §1 and the owner invariant all live over there, and a copy here
 *       would be a copy of the rules or - worse - a version without them.</li>
 *   <li><b>Approving a {@code TableOpen} creates a draft owned by the requester</b>, through
 *       {@link GameTableService#createDraftFor}: they become its Primary master and are granted no
 *       role, because running one table is a row in {@code masters} and not the Master role.</li>
 * </ul>
 *
 * <p><b>Submitting notifies nobody</b> (ADR-16 §5). Admin work items are not duplicated as notifications;
 * the shared queue is a view over these rows. The <em>resolution</em> notifies, and only the person
 * who asked.
 */
@Service
public class ApprovalService {

    /** The {@code approval_requests} table, queried by specification for the admin search box. */
    private final ApprovalRequestRepository approvalRequestRepository;

    /** The half of the polymorphic reference the database cannot do (ADR-16 §2). */
    private final ApprovalEntityResolver entityResolver;

    /** Loads people - the requester, and the admin answering. */
    private final UserService userService;

    /**
     * Where the requester's row is locked, which is what serializes two submits by one person.
     *
     * <p>Injected alongside {@link UserService} rather than through it, because what this needs is a
     * locking read and a service that hands back a cached or already-loaded instance cannot give one.
     */
    private final UserRepository userRepository;

    /** Reads live grants, for the "you already are a master" check. */
    private final UserRoleRepository userRoleRepository;

    /** The <b>only</b> way a role is ever granted (roles-y-alcance.md §4). */
    private final UserRoleService userRoleService;

    /** Tells the requester their request was answered. Nothing is sent when it is made (ADR-16 §5). */
    private final NotificationService notificationService;

    /**
     * Where the two pause transitions live (ADR-07 §2). Approving a {@code TablePause} moves the table and
     * <b>rejecting one moves it back</b>; neither is written here, for the same reason
     * {@code MasterGrant} does not write a role: the lifecycle has one owner and a copy of it here
     * would be a copy without the locking and the trail.
     */
    private final GameTableService gameTableService;

    /** Where {@code Blocked} is written. Approving a {@code PlayerBan} goes through it, never around it. */
    private final RegistrationService registrationService;

    /**
     * Answers who runs a table: who may ask for a veto on it, and who may read the pending ones. Being a
     * master of <em>this</em> table is a row in {@code masters} that no {@code @PreAuthorize} can see.
     */
    private final MasterService masterService;

    /** Entity to DTO. */
    private final ApprovalMapper approvalMapper;

    /** Whether a table is there for the actor, asked before a refusal could confirm it. */
    private final TableVisibilityService tableVisibilityService;

    /**
     * @param approvalRequestRepository the {@code approval_requests} table
     * @param entityResolver            resolves the polymorphic reference (ADR-16 §2)
     * @param userService               loads people
     * @param userRepository            locks the requester's row
     * @param userRoleRepository        reads live grants
     * @param userRoleService           the one place a role is written
     * @param notificationService       tells the requester the outcome
     * @param gameTableService          the table's lifecycle - the effect of a {@code TablePause}
     * @param registrationService       the veto - the effect of a {@code PlayerBan}
     * @param masterService             answers membership of a table, for who may ask for a veto
     * @param approvalMapper            entity to DTO
     * @param tableVisibilityService    whether the actor may see a table, before being refused on it
     */
    public ApprovalService(
            ApprovalRequestRepository approvalRequestRepository,
            ApprovalEntityResolver entityResolver,
            UserService userService,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            UserRoleService userRoleService,
            NotificationService notificationService,
            GameTableService gameTableService,
            RegistrationService registrationService,
            MasterService masterService,
            ApprovalMapper approvalMapper,
            TableVisibilityService tableVisibilityService) {
        this.approvalRequestRepository = approvalRequestRepository;
        this.entityResolver = entityResolver;
        this.userService = userService;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.userRoleService = userRoleService;
        this.notificationService = notificationService;
        this.gameTableService = gameTableService;
        this.registrationService = registrationService;
        this.masterService = masterService;
        this.approvalMapper = approvalMapper;
        this.tableVisibilityService = tableVisibilityService;
    }

    /**
     * Opens a request.
     *
     * <p>The order of the checks is deliberate: the specific refusal comes before the general one, so
     * somebody who already holds Master is told <em>that</em> rather than being told they have a
     * request open. And every check happens before the first write, so a refused request leaves no
     * half of itself behind.
     *
     * @param type          what is being asked for
     * @param justification why, never blank - the request validates it
     * @param tableName     the name of the table a {@code TableOpen} asks to open, required for that
     *                      type and ignored for the others
     * @param actor         the actor, from the token (ADR-05 §5). The request is always about them: the
     *                      body carries no id and could not be trusted with one (arquitectura §2.6)
     * @return the request as it was opened
     * @throws NotFoundException        404 when the actor's account is gone - the ADR-16 §2 validation,
     *                                  run before the insert
     * @throws ForbiddenActionException 403 when the account is not Allowed
     * @throws InvalidRequestException  400 when the type is one whose entity is not the requester -
     *                                  those are opened from the resource they are about - or
     *                                  {@code TABLE_NAME_REQUIRED} when a {@code TableOpen} names no table
     * @throws ConflictException        409 {@code MASTER_ROLE_ALREADY_HELD} when asking for the role,
     *                                  or for a single table, while already holding the role, or
     *                                  {@code REQUEST_ALREADY_PENDING} when one of this type is
     *                                  already open
     */
    @Transactional
    public ApprovalRequestDetailResponse submit(
            ApprovalRequestType type, String justification, @Nullable String tableName, CurrentUser actor) {
        // The locking read is the FIRST thing this transaction does with the requester's row, and it
        // has to be (ADR-06 §2). Two submits of the same type by one person each read "no pending request
        // of this type" from their own snapshot, both pass the guard below, and both insert - the
        // duplicate the rule exists to stop, with neither of them refused. A plain load here followed
        // by a lock would be wrong in the way that is hardest to see: under REPEATABLE READ the
        // waiting transaction wakes up and answers from the snapshot it opened before it began
        // waiting, and a stale instance already in the persistence context is what the lock would
        // refresh. So the lock comes first, and everything below reads through it.
        User requester = requireAllowedRequester(actor);

        // Somebody who holds the role creates their own tables; asking an admin for one is asking
        // for nothing.
        boolean asksToRun = type == ApprovalRequestType.MasterGrant || type == ApprovalRequestType.TableOpen;
        if (asksToRun && holdsMaster(requester.getId())) {
            throw new ConflictException(
                    "User " + requester.getId() + " already holds the Master role",
                    ConflictException.MASTER_ROLE_ALREADY_HELD);
        }

        // This endpoint only ever opens a request about the person asking, which is why it takes no
        // entity id and could not be trusted with one (F3.2 §0d, arquitectura §2.6). The two types
        // whose entity is something else have doors of their own, on the aggregate they are about:
        // submitTablePause and submitPlayerBan.
        //
        // 400 and not 403, by the rule the project keeps repeating: 403 is who you are, 400 is what
        // you sent. Anybody at all may open a request here - what cannot be opened here is a request
        // of this type, and no change of actor would make it work.
        if (!type.entityType().equals(ApprovalEntityResolver.USER)) {
            throw new InvalidRequestException(
                    "A " + type.wireName() + " request is opened from the resource it is about, not here",
                    "REQUEST_TYPE_NOT_ACCEPTED_HERE");
        }

        String proposedTableName = null;
        if (type == ApprovalRequestType.TableOpen) {
            proposedTableName = tableName == null ? "" : tableName.strip();
            if (proposedTableName.isEmpty()) {
                throw new InvalidRequestException(
                        "A TableOpen request needs the name of the table", "TABLE_NAME_REQUIRED");
            }
        }

        return detailOf(openRequest(type, requester.getId(), requester, justification, proposedTableName));
    }

    /**
     * A master asking for their table to be paused (ADR-07 §2).
     *
     * <p><b>Two things in one transaction</b>: the table moves to {@code PauseRequested} and the
     * request is written. Not two endpoints and not two calls from a controller - a table sitting in
     * {@code PauseRequested} with nobody asked is the orphan this slice came to close, and the
     * reverse - a request about a table still reading {@code InProgress} - is an admin approving
     * something that never happened.
     *
     * <p><b>Why the door is on the table and not on {@code POST /api/v1/requests}.</b> That endpoint
     * deliberately takes no {@code entityId}, so nobody can file a request in somebody
     * else's name; here the entity is not the requester, and putting the table in the path is what
     * lets the authorization - running <em>this</em> table - be checked where it can be seen at all.
     *
     * <p>The table moves first, which is also the lock: {@code markPauseRequested} takes the table
     * row, so two masters of the same table asking at once produce one request and one
     * {@code PAUSE_ALREADY_REQUESTED} rather than two rows in the tray.
     *
     * @param gameTableId   the table to pause
     * @param justification why, never blank - it is what the admin reads in the tray
     * @param actor         the actor, from the token; has to run the table
     * @return the table, now PauseRequested
     * @throws NotFoundException 404 when the table does not exist or the actor cannot see it - the
     *         same answer as a table that does not exist
     * @throws ForbiddenActionException 403 when the actor sees the table but does not run it, or
     *         their account is not Allowed
     * @throws ConflictException 409 {@code PAUSE_ALREADY_REQUESTED} when it is already waiting
     */
    @Transactional
    public GameTableDetailResponse submitTablePause(String gameTableId, String justification, CurrentUser actor) {
        User requester = requireAllowedRequester(actor);
        GameTableDetailResponse table = gameTableService.markPauseRequested(gameTableId, actor.userId());
        openRequest(ApprovalRequestType.TablePause, gameTableId, requester, justification, null);
        return table;
    }

    /**
     * A master of the table asking the admins to veto somebody.
     *
     * <p>Any master who runs the table may ask - the {@code Primary} or a co-master - and nobody vetoes
     * directly: an admin decides, through {@link #approve} or {@link #reject}. Until then nothing about
     * the person changes.
     *
     * <p><b>The table's lock is the first statement</b>, which is what serializes two masters asking
     * about the same person at once: without it both read "nothing pending" and both insert, and the
     * tray gets the same decision twice. The requester's own row is deliberately <em>not</em> locked
     * here - asking for a pause locks the user and then the table, and taking them in the other order
     * on this path could deadlock the two.
     *
     * <p>Only a {@code Player} can be vetoed: a candidate is turned down, not vetoed. Anybody else is
     * refused <em>before</em> the row is written: a request an admin can only answer with an error is
     * worse than a 409 now.
     *
     * @param gameTableId    the table, from the path
     * @param registrationId the application to veto
     * @param justification  why, never blank - the admin deciding reads exactly this
     * @param actor          the actor, from the token; has to be a master of that table
     * @return the request as it was opened
     * @throws NotFoundException        404 when the table does not exist or the actor cannot see it -
     *                                  the same answer as a table that does not exist - or when the
     *                                  application is not this table's
     * @throws ForbiddenActionException 403 when the actor sees the table but does not run it, or
     *                                  their account is not Allowed
     * @throws ConflictException        409 {@code REGISTRATION_ALREADY_BLOCKED} when it is already
     *                                  vetoed, a plain conflict when it is not a {@code Player}, or
     *                                  {@code REQUEST_ALREADY_PENDING}
     */
    @Transactional
    public ApprovalRequestDetailResponse submitPlayerBan(
            String gameTableId, String registrationId, String justification, CurrentUser actor) {
        registrationService.lockTable(gameTableId);
        // Asking is a thing the people running the table do, whatever role they hold.
        if (!masterService.isMasterOf(gameTableId, actor.userId())) {
            // Said only about a table they may see: to anybody else it is the same 404 as an id nobody used.
            tableVisibilityService.requireVisible(gameTableId, actor.userId());
            throw new ForbiddenActionException("Only a master of this table can ask for a veto on it");
        }
        registrationService.requireBlockableOn(gameTableId, registrationId);
        User requester = requireAllowed(userService.getById(actor.userId()));
        return detailOf(openRequest(ApprovalRequestType.PlayerBan, registrationId, requester, justification, null));
    }

    /**
     * The veto requests waiting on one table, for the masters who asked and the admins who decide.
     *
     * <p>Readable by <b>any</b> master of the table: the one who asked has to be able to see that they
     * did, which is the same reason {@code /requests/mine} exists. Resolving them is an admin's, from
     * the shared approval endpoints.
     *
     * <p>Pending only. A resolved veto request is not work waiting on anybody, and what became of a
     * veto is on the roster row itself, with who decided it and when.
     *
     * <p><b>It answers with {@link BanRequestResponse} and not with the shared summary</b>, because the
     * request is about a third person: a reader with two open requests on one table would otherwise be
     * telling them apart by the wording of the reason.
     *
     * <p>A request about an application that was withdrawn is still listed, with the person's name:
     * it is still waiting on an admin, who can only refuse it, and the masters who asked have to see
     * that it is there. Two queries whatever the number of requests - the table's applications with
     * their applicants, and the requests about them with their requesters.
     *
     * @param gameTableId the table
     * @param actorId     the actor, from the token
     * @return the pending veto requests about this table's applications, withdrawn ones included,
     *         oldest first, each naming the person it is about
     * @throws NotFoundException 404 when the table does not exist or the actor cannot see it - the
     *         same answer as a table that does not exist
     * @throws ForbiddenActionException 403 when the actor sees the table but neither runs it nor is
     *         an admin
     */
    @Transactional(readOnly = true)
    public List<BanRequestResponse> listBanRequests(String gameTableId, String actorId) {
        tableVisibilityService.requireExisting(gameTableId);
        if (!masterService.canOversee(gameTableId, actorId)) {
            // Said only about a table they may see: to anybody else it is the same 404 as an id nobody used.
            tableVisibilityService.requireVisible(gameTableId, actorId);
            throw new ForbiddenActionException("Only a master of this table or an admin can read its veto requests");
        }
        Map<String, RegistrationSubject> subjects = registrationService.subjectsOfTable(gameTableId);
        if (subjects.isEmpty()) {
            return List.of();
        }
        return approvalRequestRepository
                .findByRequestTypeAndStatusAndEntityIdInOrderByCreatedAtAsc(
                        ApprovalRequestType.PlayerBan, ApprovalStatus.Pending, subjects.keySet())
                .stream()
                .map(request -> toBanRequest(request, subjects.get(request.getEntityId())))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * One veto request as the table's roster reads it.
     *
     * <p>Null when the application it points at is not among the table's - which the polymorphic
     * reference makes possible in principle (ADR-16 §2) and the caller filters out. The row outliving
     * what it points at is the accepted cost of that decision; showing a line that names nobody is
     * not.
     */
    private static @Nullable BanRequestResponse toBanRequest(
            ApprovalRequest request, @Nullable RegistrationSubject target) {
        if (target == null) {
            return null;
        }
        return new BanRequestResponse(
                request.getId(),
                target.registrationId(),
                target.applicantUserId(),
                target.applicantName(),
                displayNameOf(request.getRequestedBy()),
                request.getJustification(),
                request.getCreatedAt());
    }

    /** The screen shows a person, not an id - the same fallback every mapper of the project uses. */
    private static String displayNameOf(User user) {
        return user.getName() != null ? user.getName() : user.getDiscordUsername();
    }

    /**
     * One person's own requests, newest first.
     *
     * <p>It exists so a request does not disappear once sent. The screen that provoked it needs to be
     * able to say "you already asked, it is pending, here is when" instead of offering the button
     * again - which is what stops the duplicate at the interface rather than only at the 409.
     *
     * <p><b>It takes the same {@code ?q=} the admin listing does</b>, and it has to. Without a filter
     * the screen reads page one of everything this person ever asked, newest first, and decides
     * whether to offer the button from that: a {@code MasterGrant} still pending from months ago,
     * with twenty resolved {@code General} requests on top of it, falls off page one and the button
     * comes back - offering an action whose only possible answer is a 409. {@code /status Pending}
     * answers the question the screen is actually asking. It goes through {@code ?q=} rather than a
     * parameter of its own because one search box per endpoint is the rule (arquitectura §2.5), and
     * because {@code /request_type} then works here for free.
     *
     * <p>The actor's own filter is <b>not</b> part of that language: it is forced inside
     * {@link ApprovalSearchSpecification#mine}, where no string from the request can reach it (ADR-05 §5).
     *
     * @param actorId  the requester, always the actor from the token. There is no id parameter that
     *                 could point at somebody else (ADR-05 §5)
     * @param rawQuery the search box: {@code /status} and {@code /request_type} narrow it, bare text
     *                 matches the justification. Null or blank lists all of their requests
     * @param pageable page and size
     * @return one page of their requests
     */
    @Transactional(readOnly = true)
    public PageResponse<ApprovalRequestSummaryResponse> listMine(
            String actorId, @Nullable String rawQuery, Pageable pageable) {
        SearchQuery query = SearchQueryParser.parse(rawQuery, ApprovalSearchField.wireNames());
        Page<ApprovalRequest> page =
                approvalRequestRepository.findAll(ApprovalSearchSpecification.mine(query, actorId), pageable);
        return summariesOf(page);
    }

    /**
     * The admin listing behind {@code /admin/requests}.
     *
     * <p><b>No implicit filter.</b> An empty query lists every request in every state, like every
     * other listing of the application. The screen opens on {@code Pending} because the frontend puts
     * it in its initial {@code ?q=} - a queue that opens showing resolved items is useless to work
     * with, but an endpoint whose answer does not follow from its URL is worse.
     *
     * @param rawQuery the search box: bare text matches the justification or the requester's name,
     *                 and {@code /request_type}, {@code /status} and {@code /requested_by} narrow it.
     *                 An unrecognized value matches nothing and is never a 400 (arquitectura §2.5)
     * @param pageable page, size and sort, with a tie-break by id (ADR-07 §9, ADR-04 §6)
     * @return one page of requests
     */
    @Transactional(readOnly = true)
    public PageResponse<ApprovalRequestSummaryResponse> search(@Nullable String rawQuery, Pageable pageable) {
        SearchQuery query = SearchQueryParser.parse(rawQuery, ApprovalSearchField.wireNames());
        Page<ApprovalRequest> page =
                approvalRequestRepository.findAll(ApprovalSearchSpecification.forAdmin(query), pageable);
        return summariesOf(page);
    }

    /**
     * A page of requests as rows, each with the table it is about and, for a veto, whom it is about.
     *
     * <p>A pause points at its table; a veto points at an application, whose table and applicant are
     * read for the whole page in one query ({@code RegistrationService.subjectsOf}) instead of one per
     * row.
     *
     * @param page the requests of one page
     * @return the same page as summaries
     */
    private PageResponse<ApprovalRequestSummaryResponse> summariesOf(Page<ApprovalRequest> page) {
        Map<String, RegistrationSubject> subjects = subjectsOf(page.getContent());
        return PageResponse.from(page.map(request -> approvalMapper.toSummaryResponse(
                request, tableIdOf(request, subjects), subjectNameOf(request, subjects))));
    }

    /**
     * One request in full.
     *
     * @param requestId the request
     * @return its detail
     * @throws NotFoundException 404 when no request has that id
     */
    @Transactional(readOnly = true)
    public ApprovalRequestDetailResponse getDetail(String requestId) {
        return detailOf(getById(requestId));
    }

    /** The detail of one request, with its table and - for a veto - its subject resolved. */
    private ApprovalRequestDetailResponse detailOf(ApprovalRequest request) {
        Map<String, RegistrationSubject> subjects = subjectsOf(List.of(request));
        return approvalMapper.toDetailResponse(
                request, tableIdOf(request, subjects), subjectNameOf(request, subjects));
    }

    /**
     * Who the veto requests among these are about, in one query.
     *
     * @param requests any requests; only the ones pointing at an application are looked up
     * @return the subject of each application they point at, by application id
     */
    private Map<String, RegistrationSubject> subjectsOf(List<ApprovalRequest> requests) {
        return registrationService.subjectsOf(requests.stream()
                .filter(request -> ApprovalEntityResolver.TABLE_REGISTRATION.equals(request.getEntityType()))
                .map(ApprovalRequest::getEntityId)
                .toList());
    }

    /** The table a request is about: a pause names it, a veto reaches it through its application. */
    private static @Nullable String tableIdOf(ApprovalRequest request, Map<String, RegistrationSubject> subjects) {
        return switch (request.getEntityType()) {
            case ApprovalEntityResolver.GAME_TABLE -> request.getEntityId();
            case ApprovalEntityResolver.TABLE_REGISTRATION -> {
                RegistrationSubject subject = subjects.get(request.getEntityId());
                yield subject == null ? null : subject.gameTableId();
            }
            default -> null;
        };
    }

    /** Whom a veto request is about; null for every request whose subject is the requester. */
    private static @Nullable String subjectNameOf(ApprovalRequest request, Map<String, RegistrationSubject> subjects) {
        RegistrationSubject subject = subjects.get(request.getEntityId());
        return ApprovalEntityResolver.TABLE_REGISTRATION.equals(request.getEntityType()) && subject != null
                ? subject.applicantName()
                : null;
    }

    /**
     * Says yes, and applies whatever saying yes means for this type.
     *
     * <p>The effect per type:
     *
     * <ul>
     *   <li>{@code MasterGrant} grants the role through {@link UserRoleService#grantRole} - the one
     *       place that writes a role, with the reason it was granted being the resolution note.</li>
     *   <li>{@code TableOpen} creates a {@code Draft} with the proposed name, authored and run by the
     *       requester as its Primary master, through {@link GameTableService#createDraftFor}. No role is
     *       granted: they run that one table and nothing more. The rest of the table - systems, agenda,
     *       seats - is theirs to write, and is demanded when they send it to review. The requester has
     *       to be in good standing at the moment of approving: a draft handed to a blocked account is a
     *       table nobody can run.</li>
     *   <li>{@code General} has no effect beyond being resolved. That is its nature, and saying so
     *       here is the point - an admin reading this class should not have to wonder what it
     *       silently did.</li>
     *   <li>{@code PlayerBan} is the veto itself: a master asked, and an admin approving it bars the
     *       person from the table, through {@code RegistrationService}.</li>
     * </ul>
     *
     * @param requestId      the request
     * @param resolutionNote why, never blank - mandatory in both directions (ADR-16 §1)
     * @param actor          the admin, from the token (ADR-05 §5)
     * @return the request afterwards, so the screen does not have to re-fetch
     * @throws NotFoundException 404 when no request has that id
     * @throws ConflictException 409 {@code REQUEST_ALREADY_RESOLVED} when it is not Pending,
     *                           {@code ITEM_ALREADY_CLAIMED} when <em>another</em> admin reserved it
     *                           from the shared queue (ADR-16 §5), {@code OWN_REQUEST_NOT_APPROVABLE}
     *                           when it is a veto the actor asked for themselves,
     *                           {@code REQUEST_ENTITY_GONE} when what it points at no longer exists, or
     *                           {@code REQUESTER_NOT_ACTIVE} when it asks to open a table and the
     *                           requester's account is no longer Allowed
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ApprovalRequestDetailResponse approve(String requestId, String resolutionNote, CurrentUser actor) {
        lockTableFirstIfVeto(requestId);
        ApprovalRequest request = resolve(requestId, ApprovalStatus.Approved, resolutionNote, actor);

        String createdTableId = null;
        switch (request.getRequestType()) {
            case MasterGrant -> userRoleService.grantRole(
                    request.getRequestedBy().getId(), PlatformRole.MASTER, resolutionNote, actor);
            // The resolution note IS the pause's justification (ADR-07 §2, arquitectura §4.5). Not a
            // copy of it and not a second reason invented here: the admin already wrote why, and
            // asking them twice would produce two answers to one question.
            case TablePause -> gameTableService.applyApprovedPause(
                    request.getEntityId(), actor.userId(), request.getRequestedBy().getId(), resolutionNote);
            // Through RegistrationService and never a second write of `Blocked`: the same reasoning
            // MasterGrant records above, and the reason the veto lands in `registration_status_changes`
            // with the deciding admin's name on it.
            case PlayerBan -> registrationService.applyApprovedBlock(
                    request.getEntityId(), actor.userId(), resolutionNote);
            case TableOpen -> createdTableId = createRequestedDraft(request);
            // Nothing to do: resolving it is all a general request is.
            case General -> { }
        }

        notifyRequester(request, NotificationType.ApprovalRequestApproved, createdTableId);
        return detailOf(request);
    }

    /**
     * Says no, with a reason.
     *
     * <p>A rejection has no effect of its own - that is what makes it a rejection - but the reason is
     * as mandatory as it is on an approval (ADR-16 §1). Being told no and learning nothing is the half of
     * the mechanism that would make the whole thing not worth having.
     *
     * @param requestId      the request
     * @param resolutionNote why, never blank
     * @param actor          the admin, from the token (ADR-05 §5)
     * @return the request afterwards
     * @throws NotFoundException 404 when no request has that id
     * @throws ConflictException 409 {@code REQUEST_ALREADY_RESOLVED} when it is not Pending,
     *                           {@code ITEM_ALREADY_CLAIMED} when <em>another</em> admin reserved it
     *                           from the shared queue (ADR-16 §5), or {@code REQUEST_ENTITY_GONE} when
     *                           what it points at no longer exists - except for a veto, which can be
     *                           refused after its application was withdrawn, since refusing it acts on
     *                           nothing and is the only way it leaves the tray. Refusing a veto one
     *                           asked for oneself is allowed too: only approving it is someone else's
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ApprovalRequestDetailResponse reject(String requestId, String resolutionNote, CurrentUser actor) {
        lockTableFirstIfVeto(requestId);
        ApprovalRequest request = resolve(requestId, ApprovalStatus.Rejected, resolutionNote, actor);

        // This switch did not exist before F3.4, and its absence was not an omission: for the three
        // types of F3.2 a rejection genuinely had no effect, which is what a rejection normally is.
        // TablePause broke that. Asking for a pause already moved the table, so saying no has to
        // move it back - otherwise the refusal strands it in PauseRequested with no door out, and
        // the master's only recourse is to ask again in order to be refused again.
        switch (request.getRequestType()) {
            case TablePause -> gameTableService.revertRequestedPause(
                    request.getEntityId(), actor.userId(), resolutionNote);
            // A refused veto leaves the application exactly where it was, which is the whole of it:
            // nothing moved when the master asked, so nothing moves back.
            case PlayerBan, MasterGrant, TableOpen, General -> { }
        }

        notifyRequester(request, NotificationType.ApprovalRequestRejected, null);
        return detailOf(request);
    }

    /**
     * The effect of approving a {@code TableOpen}: a draft in the requester's name.
     *
     * @return the id of the draft
     * @throws ConflictException 409 {@code REQUESTER_NOT_ACTIVE} when the requester's account is no
     *                           longer Allowed
     */
    private String createRequestedDraft(ApprovalRequest request) {
        User requester = request.getRequestedBy();
        if (requester.getStatus() != UserStatus.Allowed) {
            throw new ConflictException(
                    "User " + requester.getId() + " is " + requester.getStatus()
                            + " and cannot be handed the table of request " + request.getId(),
                    ConflictException.REQUESTER_NOT_ACTIVE);
        }
        // Validated at submit; only a row written before the column existed has no name to give.
        String name = Objects.requireNonNull(
                request.getProposedTableName(), "TableOpen request " + request.getId() + " has no table name");
        return gameTableService.createDraftFor(requester.getId(), name);
    }

    /**
     * For a veto request, takes its table's lock before {@link #resolve} takes the request's.
     *
     * <p>Every veto path takes the table first - asking for one, lifting one, and the approval's own
     * effect - and this keeps resolving in that same order, so the request's status and the
     * application the approval acts on are both read after whatever another writer of the table
     * committed while this one waited. None of those paths locks the request before the table, so no
     * pair of them can wait on each other in a cycle; one order for all of them is what keeps it that
     * way. The lookup that finds the table is a bare-column read, and the public resolutions run in
     * {@code READ COMMITTED}, so nothing read before the wait is answered from a snapshot older than
     * the lock. The other types have no table to queue on.
     *
     * <p>The row is locked even when the table was deleted: approving then answers
     * {@code REQUEST_ENTITY_GONE} - the application went with the table - and refusing still has to be
     * possible, or the request would sit in the tray for ever.
     */
    private void lockTableFirstIfVeto(String requestId) {
        approvalRequestRepository
                .findEntityIdOfType(requestId, ApprovalRequestType.PlayerBan)
                .ifPresent(registrationId ->
                        registrationService.lockTableRowEvenIfDeleted(registrationService.tableIdOf(registrationId)));
    }

    /**
     * Everything the two resolutions share: the state check, the ADR-16 §2 check, and the four columns that
     * move together.
     *
     * <p>Written once rather than twice because the difference between approving and rejecting is the
     * effect, not the bookkeeping - and two copies of the bookkeeping is how a rejection ends up
     * skipping a check an approval makes.
     */
    private ApprovalRequest resolve(
            String requestId, ApprovalStatus status, String resolutionNote, CurrentUser actor) {
        // Locked first, and the locking read IS the status read (ADR-06 §2). Without it the guard below is
        // a check-then-act over nothing: two admins answering at the same moment each read Pending
        // from their own snapshot, both pass, and the second UPDATE merely waits for the first one's
        // row lock and overwrites it. The benign version of that is one resolution note lost; the
        // real one is a row reading Rejected over somebody who is now a Master, with the requester
        // told both things. Until this lock existed, the only thing stopping the MasterGrant case was
        // an accidental primary-key collision in users_roles - which covered one of the three types
        // and answered 500 to the loser.
        ApprovalRequest request = approvalRequestRepository
                .lockById(requestId)
                .orElseThrow(() -> new NotFoundException("Approval request not found: " + requestId));

        if (request.getStatus() != ApprovalStatus.Pending) {
            throw new ConflictException(
                    "Request " + requestId + " is " + request.getStatus() + " and cannot be resolved again",
                    ConflictException.REQUEST_ALREADY_RESOLVED);
        }

        // «Si lo toma uno, baja para todos» (ADR-16 §5): what is refused is answering a request another
        // admin reserved - a stale link, a second tab, a tray that has not refreshed. An unreserved
        // one is nobody's and answering it is an implicit claim, which is what keeps /admin/requests
        // working: that screen has Approve and Reject and no way to reserve anything.
        //
        // Second and not first, because "somebody already answered this" is the truer sentence when
        // both are true: naming a colleague who reserved a request that no longer needs answering
        // would send the reader to ask them about nothing.
        QueueClaimRule.requireNotHeldByAnother(request.getClaimedBy(), actor.userId(), "request " + requestId);

        // An admin who is also a master of the table can ask for a veto; deciding it is somebody else's.
        // Refusing their own request stays open - it is how they take it back.
        if (status == ApprovalStatus.Approved
                && request.getRequestType() == ApprovalRequestType.PlayerBan
                && request.getRequestedBy().getId().equals(actor.userId())) {
            throw new ConflictException(
                    "User " + actor.userId() + " cannot approve veto request " + requestId + " they asked for",
                    ConflictException.OWN_REQUEST_NOT_APPROVABLE);
        }

        // The row outlives what it points at on purpose - but approving it would be acting on a ghost, so
        // the reference is checked again here and not only at submit time. Refusing a veto whose
        // application was withdrawn is the exception: it has no effect, and it is the only way the
        // request leaves the tray.
        boolean refusingAVeto = status == ApprovalStatus.Rejected && request.getRequestType() == ApprovalRequestType.PlayerBan;
        if (!refusingAVeto) {
            requireEntityExists(request.getEntityType(), request.getEntityId(), requestId);
        }

        request.resolve(status, userService.getById(actor.userId()), resolutionNote);
        return request;
    }

    /** The ADR-16 §2 check, in the one shape both callers use. */
    private void requireEntityExists(String entityType, String entityId, @Nullable String requestId) {
        if (entityResolver.exists(entityType, entityId)) {
            return;
        }
        if (requestId == null) {
            throw new NotFoundException("Cannot open a request about a missing " + entityType + ": " + entityId);
        }
        throw new ConflictException(
                "Request " + requestId + " points at a " + entityType + " that no longer exists: " + entityId,
                ConflictException.REQUEST_ENTITY_GONE);
    }

    /** The resolution is the only thing that rings a bell, and only for the person who asked (ADR-16 §5). */
    private void notifyRequester(ApprovalRequest request, NotificationType type, @Nullable String createdTableId) {
        // A veto points at an application and an approved TableOpen at the requester; in both the
        // requester's click has to land on the table, so it travels too.
        String tableId = request.getRequestType() == ApprovalRequestType.PlayerBan
                ? registrationService.tableIdOf(request.getEntityId())
                : createdTableId;
        notificationService.notifyApprovalResolved(
                request.getRequestedBy().getId(), type, request.getEntityType(), request.getEntityId(), tableId);
    }

    private ApprovalRequest getById(String requestId) {
        return approvalRequestRepository
                .findById(requestId)
                .orElseThrow(() -> new NotFoundException("Approval request not found: " + requestId));
    }

    /**
     * The requester's row, locked, with their account checked - what every submit starts with.
     *
     * <p>The locking read is the first thing the transaction does with that row, and it has to be
     * (ADR-06 §2): two submits of the same type by one person each read "nothing pending" from their own
     * snapshot and both insert. See {@link #submit} for the whole reasoning. A veto request is the one
     * submit that does not lock this row: it serializes on its table instead (see {@link #submitPlayerBan}).
     */
    private User requireAllowedRequester(CurrentUser actor) {
        return requireAllowed(userRepository
                .lockById(actor.userId())
                .orElseThrow(() -> new NotFoundException("User not found: " + actor.userId())));
    }

    /** Only an account in good standing opens requests. */
    private static User requireAllowed(User requester) {
        if (requester.getStatus() != UserStatus.Allowed) {
            throw new ForbiddenActionException(
                    "User " + requester.getId() + " is " + requester.getStatus() + " and cannot open requests");
        }
        return requester;
    }

    /**
     * The row itself: the no-duplicates guard, the ADR-16 §2 check, then the insert.
     *
     * <p>Shared by the three submits so that the two F3.4 added cannot quietly skip the validation
     * the first one does - which is the exact failure {@link ApprovalEntityResolver}'s Javadoc
     * describes, seen from the writing side.
     *
     * <p><b>The guard is scoped to whatever the request is about, and that is not a refinement -
     * it is a correction.</b> F3.2 wrote «one pending request per type and per person», which was
     * exactly right while all three types pointed at the requester: there, the person <em>is</em> the
     * entity. The two types F3.4 added point somewhere else, and the per-person reading refuses two
     * perfectly legitimate second requests:
     *
     * <ul>
     *   <li>a master of <b>two</b> running tables asking to pause the second one - and because the
     *       submit is one transaction, the 409 would roll back the {@code PauseRequested} the first
     *       half of it had just written, so the table silently stayed as it was;</li>
     *   <li>a master asking to veto a second person while an admin has not yet answered the first -
     *       which {@link #listBanRequests} openly assumes is possible, since it exists to tell two open
     *       requests apart.</li>
     * </ul>
     *
     * <p>What the duplicate guard is actually for survives intact, because it moves with the entity:
     * «this table is already waiting on a pause» and «somebody already asked about this person».
     *
     * @param entityId          what the request is about. For the first three types it is the
     *                          requester; for {@code TablePause} and {@code PlayerBan} it is the table or
     *                          the application
     * @param proposedTableName the name of the table a {@code TableOpen} asks to open; null otherwise
     */
    private ApprovalRequest openRequest(
            ApprovalRequestType type,
            String entityId,
            User requester,
            String justification,
            @Nullable String proposedTableName) {
        requireNoPendingDuplicate(type, entityId, requester);
        // ADR-16 §2, the part the database cannot do: the reference is checked BEFORE the row exists.
        requireEntityExists(type.entityType(), entityId, null);
        // No notification to the admins, on purpose (ADR-16 §5): the queue is a view over this row.
        return approvalRequestRepository.save(
                new ApprovalRequest(type, type.entityType(), entityId, requester, justification, proposedTableName));
    }

    /**
     * «Is there already one of these open?», asked about the right thing.
     *
     * <p>One branch per shape of request and not a clever unified query: for a type whose entity is
     * the requester the two questions are literally the same row, and writing it as one would hide
     * that the difference is about <em>what a duplicate means</em>, not about which column to filter.
     *
     * @throws ConflictException 409 {@code REQUEST_ALREADY_PENDING}, with a message that names
     *                           whichever of the two the duplicate was about
     */
    private void requireNoPendingDuplicate(ApprovalRequestType type, String entityId, User requester) {
        if (type.entityType().equals(ApprovalEntityResolver.USER)) {
            if (approvalRequestRepository.existsByRequestTypeAndRequestedBy_IdAndStatus(
                    type, requester.getId(), ApprovalStatus.Pending)) {
                throw new ConflictException(
                        "User " + requester.getId() + " already has a pending " + type.wireName() + " request",
                        ConflictException.REQUEST_ALREADY_PENDING);
            }
            return;
        }
        if (approvalRequestRepository.existsByRequestTypeAndEntityIdAndStatus(
                type, entityId, ApprovalStatus.Pending)) {
            throw new ConflictException(
                    "A " + type.wireName() + " request about " + entityId + " is already pending",
                    ConflictException.REQUEST_ALREADY_PENDING);
        }
    }

    /** Holding the role right now, which is not the same as running a table (ADR-06 §4). */
    private boolean holdsMaster(String userId) {
        return userRoleRepository.findActiveRoleNames(userId).contains(PlatformRole.MASTER.roleName());
    }
}
