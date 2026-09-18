package com.centraldungeon.approvals;

import com.centraldungeon.adminqueue.AdminQueueClaimRule;
import com.centraldungeon.approvals.dto.ApprovalRequestDetailResponse;
import com.centraldungeon.approvals.dto.ApprovalRequestSummaryResponse;
import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.exception.ForbiddenActionException;
import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.common.search.SearchQuery;
import com.centraldungeon.common.search.SearchQueryParser;
import com.centraldungeon.common.security.CurrentUser;
import com.centraldungeon.notifications.NotificationService;
import com.centraldungeon.notifications.NotificationType;
import com.centraldungeon.users.PlatformRole;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserRepository;
import com.centraldungeon.users.UserRoleRepository;
import com.centraldungeon.users.UserRoleService;
import com.centraldungeon.users.UserService;
import com.centraldungeon.users.UserStatus;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one mechanism every request that needs an approval goes through (#42, #90).
 *
 * <p>Four rules carry this class, and none of them is visible in the table:
 *
 * <ul>
 *   <li><b>The referenced entity is validated before anything is inserted</b> (#78, #126). There is
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
 *   <li><b>A request another admin reserved is not yours to answer</b> ({@link AdminQueueClaimRule},
 *       #100). Arrived with F3.3. Note what it is <em>not</em>: it does not require having reserved
 *       it, because this screen resolves without ever passing through the shared tray. Correctness
 *       does not rest on it either - the pessimistic lock above is what makes two answers impossible.
 *       This is about not taking work off a colleague's desk.</li>
 *   <li><b>Approving a {@code MasterGrant} grants the role through {@link UserRoleService}</b>
 *       (fase-3-admin-owner.md 4). Not a second path that writes the same row: who may move which
 *       rank, the exclusion of #169 and the owner invariant all live over there, and a copy here
 *       would be a copy of the rules or - worse - a version without them.</li>
 * </ul>
 *
 * <p><b>Submitting notifies nobody</b> (#100). Admin work items are not duplicated as notifications;
 * the shared queue is a view over these rows. The <em>resolution</em> notifies, and only the person
 * who asked.
 */
@Service
public class ApprovalService {

    /** The {@code approval_requests} table, queried by specification for the admin search box. */
    private final ApprovalRequestRepository approvalRequestRepository;

    /** The half of the polymorphic reference the database cannot do (#78). */
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

    /** The <b>only</b> way a role is ever granted (fase-3-admin-owner.md 4). */
    private final UserRoleService userRoleService;

    /** Tells the requester their request was answered. Nothing is sent when it is made (#100). */
    private final NotificationService notificationService;

    /** Entity to DTO. */
    private final ApprovalMapper approvalMapper;

    /**
     * @param approvalRequestRepository the {@code approval_requests} table
     * @param entityResolver            resolves the polymorphic reference (#78)
     * @param userService               loads people
     * @param userRepository            locks the requester's row
     * @param userRoleRepository        reads live grants
     * @param userRoleService           the one place a role is written
     * @param notificationService       tells the requester the outcome
     * @param approvalMapper            entity to DTO
     */
    public ApprovalService(
            ApprovalRequestRepository approvalRequestRepository,
            ApprovalEntityResolver entityResolver,
            UserService userService,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            UserRoleService userRoleService,
            NotificationService notificationService,
            ApprovalMapper approvalMapper) {
        this.approvalRequestRepository = approvalRequestRepository;
        this.entityResolver = entityResolver;
        this.userService = userService;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.userRoleService = userRoleService;
        this.notificationService = notificationService;
        this.approvalMapper = approvalMapper;
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
     * @param actor         the actor, from the token (#121). The request is always about them: the
     *                      body carries no id and could not be trusted with one (arquitectura.md 2.6)
     * @return the request as it was opened
     * @throws NotFoundException        404 when the actor's account is gone - the #78 validation,
     *                                  run before the insert
     * @throws ForbiddenActionException 403 when the account is not Allowed
     * @throws ConflictException        409 {@code MASTER_ROLE_ALREADY_HELD} when asking for a role
     *                                  already held, or {@code REQUEST_ALREADY_PENDING} when one of
     *                                  this type is already open
     */
    @Transactional
    public ApprovalRequestDetailResponse submit(ApprovalRequestType type, String justification, CurrentUser actor) {
        // The locking read is the FIRST thing this transaction does with the requester's row, and it
        // has to be (#252). Two submits of the same type by one person each read "no pending request
        // of this type" from their own snapshot, both pass the guard below, and both insert - the
        // duplicate the rule exists to stop, with neither of them refused. A plain load here followed
        // by a lock would be wrong in the way that is hardest to see: under REPEATABLE READ the
        // waiting transaction wakes up and answers from the snapshot it opened before it began
        // waiting, and a stale instance already in the persistence context is what the lock would
        // refresh. So the lock comes first, and everything below reads through it.
        User requester = userRepository
                .lockById(actor.userId())
                .orElseThrow(() -> new NotFoundException("User not found: " + actor.userId()));
        if (requester.getStatus() != UserStatus.Allowed) {
            throw new ForbiddenActionException(
                    "User " + requester.getId() + " is " + requester.getStatus() + " and cannot open requests");
        }

        if (type == ApprovalRequestType.MasterGrant && holdsMaster(requester.getId())) {
            throw new ConflictException(
                    "User " + requester.getId() + " already holds the Master role",
                    ConflictException.MASTER_ROLE_ALREADY_HELD);
        }

        if (approvalRequestRepository.existsByRequestTypeAndRequestedBy_IdAndStatus(
                type, requester.getId(), ApprovalStatus.Pending)) {
            throw new ConflictException(
                    "User " + requester.getId() + " already has a pending " + type.wireName() + " request",
                    ConflictException.REQUEST_ALREADY_PENDING);
        }

        // #78, the part the database cannot do: the reference is checked BEFORE the row exists. For
        // the three types of F3.2 the entity is the requester, so this re-states what was just
        // loaded - and it is written through the resolver anyway, because that is the check F3.4's
        // types plug into and the one the orphan sweep runs on a schedule. A validation that only
        // exists for the current set of types is a validation the next type forgets.
        String entityType = type.entityType();
        String entityId = requester.getId();
        requireEntityExists(entityType, entityId, null);

        ApprovalRequest request =
                approvalRequestRepository.save(new ApprovalRequest(type, entityType, entityId, requester, justification));
        // No notification to the admins, on purpose (#100): the queue is a view over this row.
        return approvalMapper.toDetailResponse(request);
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
     * parameter of its own because one search box per endpoint is the rule (arquitectura.md 2.5), and
     * because {@code /request_type} then works here for free.
     *
     * <p>The actor's own filter is <b>not</b> part of that language: it is forced inside
     * {@link ApprovalSearchSpecification#mine}, where no string from the request can reach it (#121).
     *
     * @param actorId  the requester, always the actor from the token. There is no id parameter that
     *                 could point at somebody else (#121)
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
        return PageResponse.from(page.map(approvalMapper::toSummaryResponse));
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
     *                 An unrecognized value matches nothing and is never a 400 (arquitectura.md 2.5)
     * @param pageable page, size and sort, with a tie-break by id (#171, #173)
     * @return one page of requests
     */
    @Transactional(readOnly = true)
    public PageResponse<ApprovalRequestSummaryResponse> search(@Nullable String rawQuery, Pageable pageable) {
        SearchQuery query = SearchQueryParser.parse(rawQuery, ApprovalSearchField.wireNames());
        Page<ApprovalRequest> page =
                approvalRequestRepository.findAll(ApprovalSearchSpecification.forAdmin(query), pageable);
        return PageResponse.from(page.map(approvalMapper::toSummaryResponse));
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
        return approvalMapper.toDetailResponse(getById(requestId));
    }

    /**
     * Says yes, and applies whatever saying yes means for this type.
     *
     * <p>The effect per type, and two of the three are deliberately nothing:
     *
     * <ul>
     *   <li>{@code MasterGrant} grants the role through {@link UserRoleService#grantRole} - the one
     *       place that writes a role, with the reason it was granted being the resolution note.</li>
     *   <li>{@code TableOpen} <b>does not create the table</b>. Approving records that the request
     *       stands; an admin then creates it {@code Unassigned} and assigns a master (#72), which is
     *       the same circuit from its other end (#90). Creating it automatically is not a refused
     *       option but an impossible one: the request carries no name, no system, no seats and no
     *       agenda. The screen links to the form; the backend does not invent a table.</li>
     *   <li>{@code General} has no effect beyond being resolved. That is its nature, and saying so
     *       here is the point - an admin reading this class should not have to wonder what it
     *       silently did.</li>
     * </ul>
     *
     * @param requestId      the request
     * @param resolutionNote why, never blank - mandatory in both directions (#42)
     * @param actor          the admin, from the token (#121)
     * @return the request afterwards, so the screen does not have to re-fetch
     * @throws NotFoundException 404 when no request has that id
     * @throws ConflictException 409 {@code REQUEST_ALREADY_RESOLVED} when it is not Pending,
     *                           {@code ITEM_ALREADY_CLAIMED} when <em>another</em> admin reserved it
     *                           from the shared queue (#100), or {@code REQUEST_ENTITY_GONE} when
     *                           what it points at no longer exists
     */
    @Transactional
    public ApprovalRequestDetailResponse approve(String requestId, String resolutionNote, CurrentUser actor) {
        ApprovalRequest request = resolve(requestId, ApprovalStatus.Approved, resolutionNote, actor);

        switch (request.getRequestType()) {
            case MasterGrant -> userRoleService.grantRole(
                    request.getRequestedBy().getId(), PlatformRole.MASTER, resolutionNote, actor);
            // Nothing to do, and each for its own reason - see the Javadoc above.
            case TableOpen, General -> { }
        }

        notifyRequester(request, NotificationType.ApprovalRequestApproved);
        return approvalMapper.toDetailResponse(request);
    }

    /**
     * Says no, with a reason.
     *
     * <p>A rejection has no effect of its own - that is what makes it a rejection - but the reason is
     * as mandatory as it is on an approval (#42). Being told no and learning nothing is the half of
     * the mechanism that would make the whole thing not worth having.
     *
     * @param requestId      the request
     * @param resolutionNote why, never blank
     * @param actor          the admin, from the token (#121)
     * @return the request afterwards
     * @throws NotFoundException 404 when no request has that id
     * @throws ConflictException 409 {@code REQUEST_ALREADY_RESOLVED} when it is not Pending,
     *                           {@code ITEM_ALREADY_CLAIMED} when <em>another</em> admin reserved it
     *                           from the shared queue (#100), or {@code REQUEST_ENTITY_GONE} when
     *                           what it points at no longer exists
     */
    @Transactional
    public ApprovalRequestDetailResponse reject(String requestId, String resolutionNote, CurrentUser actor) {
        ApprovalRequest request = resolve(requestId, ApprovalStatus.Rejected, resolutionNote, actor);
        notifyRequester(request, NotificationType.ApprovalRequestRejected);
        return approvalMapper.toDetailResponse(request);
    }

    /**
     * Everything the two resolutions share: the state check, the #78 check, and the four columns that
     * move together.
     *
     * <p>Written once rather than twice because the difference between approving and rejecting is the
     * effect, not the bookkeeping - and two copies of the bookkeeping is how a rejection ends up
     * skipping a check an approval makes.
     */
    private ApprovalRequest resolve(
            String requestId, ApprovalStatus status, String resolutionNote, CurrentUser actor) {
        // Locked first, and the locking read IS the status read (#252). Without it the guard below is
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

        // «Si lo toma uno, baja para todos» (#100): what is refused is answering a request another
        // admin reserved - a stale link, a second tab, a tray that has not refreshed. An unreserved
        // one is nobody's and answering it is an implicit claim, which is what keeps /admin/requests
        // working: that screen has Approve and Reject and no way to reserve anything.
        //
        // Second and not first, because "somebody already answered this" is the truer sentence when
        // both are true: naming a colleague who reserved a request that no longer needs answering
        // would send the reader to ask them about nothing.
        AdminQueueClaimRule.requireNotHeldByAnother(request.getClaimedBy(), actor.userId(), "request " + requestId);

        // The row outlives what it points at on purpose (#126) - but resolving it would be acting on
        // a ghost, so the reference is checked again here and not only at submit time.
        requireEntityExists(request.getEntityType(), request.getEntityId(), requestId);

        request.resolve(status, userService.getById(actor.userId()), resolutionNote);
        return request;
    }

    /** The #78 check, in the one shape both callers use. */
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

    /** The resolution is the only thing that rings a bell, and only for the person who asked (#100). */
    private void notifyRequester(ApprovalRequest request, NotificationType type) {
        notificationService.notifyApprovalResolved(
                request.getRequestedBy().getId(), type, request.getEntityType(), request.getEntityId());
    }

    private ApprovalRequest getById(String requestId) {
        return approvalRequestRepository
                .findById(requestId)
                .orElseThrow(() -> new NotFoundException("Approval request not found: " + requestId));
    }

    /** Holding the role right now, which is not the same as running a table (#135). */
    private boolean holdsMaster(String userId) {
        return userRoleRepository.findActiveRoleNames(userId).contains(PlatformRole.MASTER.roleName());
    }
}
