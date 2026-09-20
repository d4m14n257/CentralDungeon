package com.centraldungeon.approvals;

import com.centraldungeon.adminqueue.AdminQueueClaimRule;
import com.centraldungeon.approvals.dto.ApprovalRequestDetailResponse;
import com.centraldungeon.approvals.dto.ApprovalRequestSummaryResponse;
import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.exception.ForbiddenActionException;
import com.centraldungeon.common.exception.InvalidRequestException;
import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.common.search.SearchQuery;
import com.centraldungeon.common.search.SearchQueryParser;
import com.centraldungeon.common.security.CurrentUser;
import com.centraldungeon.notifications.NotificationService;
import com.centraldungeon.notifications.NotificationType;
import com.centraldungeon.registrations.RegistrationService;
import com.centraldungeon.registrations.TableRegistration;
import com.centraldungeon.registrations.dto.BanRequestResponse;
import com.centraldungeon.registrations.dto.RegistrationResponse;
import com.centraldungeon.tables.GameTableService;
import com.centraldungeon.tables.MasterService;
import com.centraldungeon.tables.dto.GameTableDetailResponse;
import com.centraldungeon.users.PlatformRole;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserRepository;
import com.centraldungeon.users.UserRoleRepository;
import com.centraldungeon.users.UserRoleService;
import com.centraldungeon.users.UserService;
import com.centraldungeon.users.UserStatus;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    /**
     * Where the two pause transitions live (#32). Approving a {@code TablePause} moves the table and
     * <b>rejecting one moves it back</b>; neither is written here, for the same reason
     * {@code MasterGrant} does not write a role: the lifecycle has one owner and a copy of it here
     * would be a copy without the locking and the trail.
     */
    private final GameTableService gameTableService;

    /** Where {@code Blocked} is written (#39). Approving a {@code PlayerBan} goes through it, never around it. */
    private final RegistrationService registrationService;

    /**
     * Answers who runs a table, for the one type whose resolver is not an admin.
     *
     * <p>{@code PlayerBan} is decided by the table's {@code Primary} (#39), and being <em>this</em>
     * table's Primary is a row in {@code masters} that no {@code @PreAuthorize} can see (#17, #121,
     * #135). So the check is here, keyed off the request's type - the same principle F3.1 settled:
     * «el otorgamiento es la regla, no la puerta».
     */
    private final MasterService masterService;

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
     * @param gameTableService          the table's lifecycle - the effect of a {@code TablePause}
     * @param registrationService       the veto - the effect of a {@code PlayerBan}
     * @param masterService             answers pertenencia, for who may resolve a {@code PlayerBan}
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
            GameTableService gameTableService,
            RegistrationService registrationService,
            MasterService masterService,
            ApprovalMapper approvalMapper) {
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
     * @throws InvalidRequestException  400 when the type is one whose entity is not the requester -
     *                                  those are opened from the resource they are about
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
        User requester = requireAllowedRequester(actor);

        if (type == ApprovalRequestType.MasterGrant && holdsMaster(requester.getId())) {
            throw new ConflictException(
                    "User " + requester.getId() + " already holds the Master role",
                    ConflictException.MASTER_ROLE_ALREADY_HELD);
        }

        // This endpoint only ever opens a request about the person asking, which is why it takes no
        // entity id and could not be trusted with one (F3.2 §0d, arquitectura.md 2.6). The two types
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

        return approvalMapper.toDetailResponse(
                openRequest(type, requester.getId(), requester, justification));
    }

    /**
     * A master asking for their table to be paused (#32).
     *
     * <p><b>Two things in one transaction</b>: the table moves to {@code PauseRequested} and the
     * request is written. Not two endpoints and not two calls from a controller - a table sitting in
     * {@code PauseRequested} with nobody asked is the orphan this slice came to close, and the
     * reverse - a request about a table still reading {@code InProgress} - is an admin approving
     * something that never happened.
     *
     * <p><b>Why the door is on the table and not on {@code POST /api/v1/requests}.</b> That endpoint
     * deliberately takes no {@code entityId} (F3.2 §0d), so nobody can file a request in somebody
     * else's name; here the entity is not the requester, and putting the table in the path is what
     * lets the authorization - running <em>this</em> table - be checked where it can be seen at all
     * (§2.6, #121).
     *
     * <p>The table moves first, which is also the lock: {@code markPauseRequested} takes the table
     * row, so two masters of the same table asking at once produce one request and one
     * {@code PAUSE_ALREADY_REQUESTED} rather than two rows in the tray.
     *
     * @param gameTableId   the table to pause
     * @param justification why, never blank - it is what the admin reads in the tray
     * @param actor         the actor, from the token (#121); has to run the table
     * @return the table, now PauseRequested
     * @throws com.centraldungeon.common.exception.ForbiddenActionException 403 when the actor does
     *         not run the table, or their account is not Allowed
     * @throws ConflictException 409 {@code PAUSE_ALREADY_REQUESTED} when it is already waiting
     */
    @Transactional
    public GameTableDetailResponse submitTablePause(String gameTableId, String justification, CurrentUser actor) {
        User requester = requireAllowedRequester(actor);
        GameTableDetailResponse table = gameTableService.markPauseRequested(gameTableId, actor.userId());
        openRequest(ApprovalRequestType.TablePause, gameTableId, requester, justification);
        return table;
    }

    /**
     * A co-master asking the {@code Primary} to veto somebody (#39).
     *
     * <p>The other half of the pair {@code RegistrationService.block} is: a {@code Primary} vetoes,
     * a {@code Secondary} asks. <b>Two endpoints and not one that behaves differently</b> - the
     * screen already knows which the reader is and says so before the button is pressed
     * (fase-3-admin-owner.md §4), and one endpoint that sometimes vetoes and sometimes asks would
     * answer with a record half of whose fields are null, which R3 forbids.
     *
     * <p>Refused for somebody the veto could no longer apply to, and <em>before</em> the row is
     * written: a request the {@code Primary} can only answer with an error is worse than a 409 now.
     *
     * @param registrationId the application to veto
     * @param justification  why, never blank - the {@code Primary} reads exactly this
     * @param actor          the actor, from the token; has to be a master of that table
     * @return the request as it was opened
     * @throws com.centraldungeon.common.exception.ForbiddenActionException 403 when the actor does
     *         not run the table the application belongs to
     * @throws ConflictException 409 {@code REGISTRATION_ALREADY_BLOCKED} or
     *                           {@code REQUEST_ALREADY_PENDING}
     */
    @Transactional
    public ApprovalRequestDetailResponse submitPlayerBan(
            String registrationId, String justification, CurrentUser actor) {
        User requester = requireAllowedRequester(actor);
        String gameTableId = registrationService.tableIdOf(registrationId);
        // Pertenencia of the table the application belongs to, not of the application: asking is a
        // thing the people running the table do (#17, #121, #135).
        if (!masterService.isMasterOf(gameTableId, actor.userId())) {
            throw new ForbiddenActionException("Only a master of this table can ask for a veto on it");
        }
        if (!registrationService.isBlockable(registrationId)) {
            throw new ConflictException(
                    "Registration " + registrationId + " cannot be blocked",
                    ConflictException.REGISTRATION_ALREADY_BLOCKED);
        }
        return approvalMapper.toDetailResponse(
                openRequest(ApprovalRequestType.PlayerBan, registrationId, requester, justification));
    }

    /**
     * The veto requests waiting on one table, for the {@code Primary} who has to answer them.
     *
     * <p>Readable by <b>any</b> master of the table and not only by the {@code Primary}: the
     * {@code Secondary} who asked has to be able to see that they did, which is the same reason
     * {@code /requests/mine} exists. Only the {@code Primary} can resolve them.
     *
     * <p>Pending only. A resolved veto request is not work waiting on anybody, and what became of a
     * veto is on the roster row itself, with who decided it and when.
     *
     * <p><b>It answers with {@link BanRequestResponse} and not with the shared summary</b>, because
     * the shared one cannot say who is going to be vetoed: for the four admin types the entity is
     * the requester, so «who asked» is the whole sentence. Here it is not - the request is about a
     * third person, and a {@code Primary} with two open requests on one table would otherwise be
     * telling them apart by the wording of the reason.
     *
     * @param gameTableId the table
     * @param actorId     the actor, from the token (#121)
     * @return the pending veto requests about this table's applications, oldest first, each naming
     *         the person it is about
     * @throws com.centraldungeon.common.exception.ForbiddenActionException 403 when the actor does
     *         not run the table
     */
    @Transactional(readOnly = true)
    public List<BanRequestResponse> listBanRequests(String gameTableId, String actorId) {
        if (!masterService.isMasterOf(gameTableId, actorId)) {
            throw new ForbiddenActionException("Only a master of this table can read its veto requests");
        }
        Map<String, TableRegistration> registrations = registrationService.registrationsOf(gameTableId);
        if (registrations.isEmpty()) {
            return List.of();
        }
        return approvalRequestRepository
                .findByRequestTypeAndStatusAndEntityIdInOrderByCreatedAtAsc(
                        ApprovalRequestType.PlayerBan, ApprovalStatus.Pending, registrations.keySet())
                .stream()
                .map(request -> toBanRequest(request, registrations.get(request.getEntityId())))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * One veto request as its {@code Primary} reads it.
     *
     * <p>Null when the application it points at is not among the table's - which the polymorphic
     * reference makes possible in principle (#78, #126) and the caller filters out. The row outliving
     * what it points at is the accepted cost of that decision; showing a line that names nobody is
     * not.
     */
    private static @Nullable BanRequestResponse toBanRequest(
            ApprovalRequest request, @Nullable TableRegistration target) {
        if (target == null) {
            return null;
        }
        return new BanRequestResponse(
                request.getId(),
                target.getId(),
                target.getUser().getId(),
                RegistrationService.applicantNameOf(target),
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
            // The resolution note IS the pause's justification (#32, modelo-datos.md:835). Not a
            // copy of it and not a second reason invented here: the admin already wrote why, and
            // asking them twice would produce two answers to one question.
            case TablePause -> gameTableService.applyApprovedPause(
                    request.getEntityId(), actor.userId(), resolutionNote);
            // Through RegistrationService and never a second write of `Blocked`: the same reasoning
            // MasterGrant records above, and the reason a veto applied this way still lands in
            // `registration_status_changes` with the Primary's name on it.
            case PlayerBan -> registrationService.applyApprovedBlock(
                    request.getEntityId(), actor.userId(), resolutionNote);
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

        // This switch did not exist before F3.4, and its absence was not an omission: for the three
        // types of F3.2 a rejection genuinely had no effect, which is what a rejection normally is.
        // TablePause broke that. Asking for a pause already moved the table, so saying no has to
        // move it back - otherwise the refusal strands it in PauseRequested with no door out, and
        // the master's only recourse is to ask again in order to be refused again.
        switch (request.getRequestType()) {
            case TablePause -> gameTableService.revertRequestedPause(
                    request.getEntityId(), actor.userId(), resolutionNote);
            // A refused veto leaves the application exactly where it was, which is the whole of it:
            // nothing moved when the co-master asked, so nothing moves back.
            case PlayerBan, MasterGrant, TableOpen, General -> { }
        }

        notifyRequester(request, NotificationType.ApprovalRequestRejected);
        return approvalMapper.toDetailResponse(request);
    }

    /**
     * The {@code Primary} vetoing somebody directly, <b>and closing out whatever was asked about
     * them</b> (#39).
     *
     * <p>The veto itself is {@code RegistrationService}'s and is not written here. What is written
     * here is the second half, which nothing else could do: any {@code PlayerBan} a co-master had
     * open about that same person has just been answered <em>in fact</em>, and leaving those rows
     * {@code Pending} strands them three ways - the request sits in the {@code Primary}'s list for
     * ever, approving it afterwards answers {@code REGISTRATION_ALREADY_BLOCKED} because the person
     * is already vetoed, and the co-master who asked is never told what happened.
     *
     * <p><b>Why the orchestration is here and not in {@code RegistrationService}.</b> Resolving an
     * {@code approval_requests} row is this class's job and only this class's (#42), and the reverse
     * dependency would close a bean cycle - {@code ApprovalService} already needs
     * {@code RegistrationService} for the effect of an approval. So the direction stays one-way and
     * the two writes stay in one transaction, which is what makes «vetado pero el pedido sigue
     * pendiente» unreachable rather than merely unlikely.
     *
     * <p>They are resolved as {@code Approved}, with the {@code Primary}'s own reason as the note.
     * That is the true record: the thing the co-master asked for is what happened, and it is the same
     * sentence the veto carries.
     *
     * @param gameTableId    the table, which is what the authorization is about
     * @param registrationId the application to veto
     * @param justification  why, never blank
     * @param actor          the actor, from the token; has to be this table's {@code Primary}
     * @return the application, now Blocked
     */
    @Transactional
    public RegistrationResponse blockDirectly(
            String gameTableId, String registrationId, String justification, CurrentUser actor) {
        RegistrationResponse blocked =
                registrationService.block(gameTableId, registrationId, actor.userId(), justification);
        closePendingBansOn(registrationId, justification, actor);
        return blocked;
    }

    /**
     * Answers every open veto request about one application, because the answer already happened.
     *
     * <p>Normally none, one at most in practice, and a loop because nothing guarantees that: two
     * co-masters can each have asked about the same person, and closing one while leaving the other
     * would be the same bug in smaller print.
     */
    private void closePendingBansOn(String registrationId, String note, CurrentUser actor) {
        List<ApprovalRequest> pending = approvalRequestRepository
                .findByRequestTypeAndEntityIdAndStatusOrderByCreatedAtAsc(
                        ApprovalRequestType.PlayerBan, registrationId, ApprovalStatus.Pending);
        if (pending.isEmpty()) {
            return;
        }
        User resolver = userService.getById(actor.userId());
        for (ApprovalRequest request : pending) {
            request.resolve(ApprovalStatus.Approved, resolver, note);
            // The co-master who asked is told, exactly as if the Primary had pressed Approve on
            // their request - which, in every sense that matters to them, is what happened.
            notifyRequester(request, NotificationType.ApprovalRequestApproved);
        }
    }

    /**
     * The {@code Primary} granting a co-master's veto request (#39), answering with the application.
     *
     * <p>It is {@link #approve} with two things added and nothing removed: the request is confirmed
     * to belong to this table - so a request id from another table cannot be resolved through this
     * route - and the answer is the roster row rather than the request, because that is what the
     * screen re-renders. The veto itself is applied by the {@code switch} in {@code approve}, through
     * {@code RegistrationService}: there is exactly one place that writes {@code Blocked}.
     *
     * @param gameTableId    the table the request must belong to
     * @param requestId      the request
     * @param resolutionNote why, never blank. It becomes the veto's own reason
     * @param actor          the actor, from the token; has to be this table's {@code Primary}
     * @return the application, now Blocked
     * @throws NotFoundException 404 when the request is not there, or belongs to another table
     */
    @Transactional
    public RegistrationResponse approveBanRequest(
            String gameTableId, String requestId, String resolutionNote, CurrentUser actor) {
        String registrationId = requireBanRequestOf(gameTableId, requestId);
        approve(requestId, resolutionNote, actor);
        return registrationService.findResponse(registrationId);
    }

    /**
     * The {@code Primary} refusing a co-master's veto request (#39).
     *
     * <p>Answers with the request and not with the application, unlike its sibling, and the
     * asymmetry is the truth: nothing about the person changed, so handing back their row would
     * suggest something did. What changed is the request, and the co-master who asked is its reader.
     *
     * @param gameTableId    the table the request must belong to
     * @param requestId      the request
     * @param resolutionNote why, never blank (#42)
     * @param actor          the actor, from the token; has to be this table's {@code Primary}
     * @return the request, now Rejected
     * @throws NotFoundException 404 when the request is not there, or belongs to another table
     */
    @Transactional
    public ApprovalRequestDetailResponse rejectBanRequest(
            String gameTableId, String requestId, String resolutionNote, CurrentUser actor) {
        requireBanRequestOf(gameTableId, requestId);
        return reject(requestId, resolutionNote, actor);
    }

    /**
     * Confirms a request is a veto request about an application of <em>this</em> table.
     *
     * <p>404 and not 403 for a mismatch, the same answer the rest of the slice gives: a request of
     * another table is not this caller's business to learn the existence of, and the id in the path
     * is the only thing that named it.
     *
     * @return the id of the application the request points at
     */
    private String requireBanRequestOf(String gameTableId, String requestId) {
        ApprovalRequest request = getById(requestId);
        if (request.getRequestType() != ApprovalRequestType.PlayerBan
                || !gameTableId.equals(registrationService.tableIdOf(request.getEntityId()))) {
            throw new NotFoundException("No veto request " + requestId + " on table " + gameTableId);
        }
        return request.getEntityId();
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

        // Who may answer depends on the TYPE. Four of the five go to the admins and their
        // @PreAuthorize says so; PlayerBan goes to the table's Primary and to nobody else (#39),
        // which no annotation can express - being this table's Primary is a row in `masters`
        // (#17, #121). The rule that an annotation cannot carry lives here.
        requireMayResolve(request, actor);

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

    /**
     * Who is entitled to answer this request, decided by its type.
     *
     * <p>{@code PlayerBan} is the exception the F3.4 contract spent its longest paragraph on. #90 is
     * generic - {@code approval_requests} covers «todo pedido dirigido a los admins» - and #39 is
     * specific: a co-master «necesita aprobación del {@code Primary}», and the slice's own "se
     * prueba" reads «un {@code Secondary} pide un veto y <b>el {@code Primary} lo resuelve</b>». The
     * specific decision wins: a veto between a co-master and a player of <em>that</em> table is
     * decided by whoever runs it, not by the platform.
     *
     * <p><b>Only the exception is written here, and that is the point.</b> «Admin or owner» is
     * exactly what {@code hasAnyRole('ADMIN','OWNER')} says, and fase-3-admin-owner.md §3 is explicit
     * that each endpoint enumerates its roles in the annotation rather than in a configuration far
     * away (#37, #89, #123) - so {@code AdminApprovalRequestController} keeps that half. What an
     * annotation <em>cannot</em> say is «the Primary of the table this registration belongs to», and
     * that is the half that lives here.
     *
     * <p>The two halves meet: an admin reaching a {@code PlayerBan} through {@code /admin/requests}
     * passes the annotation and is stopped here, and a {@code Primary} who is not an admin never
     * passes the annotation at all - they resolve from {@code /game-tables/{id}/ban-requests}, where
     * the route is {@code isAuthenticated()} and this check is the whole authorization.
     *
     * @throws ForbiddenActionException 403 {@code NOT_PRIMARY_MASTER} when a veto request is being
     *                                  answered by somebody who is not the table's Primary
     */
    private void requireMayResolve(ApprovalRequest request, CurrentUser actor) {
        if (request.getRequestType() != ApprovalRequestType.PlayerBan) {
            return;
        }
        String gameTableId = registrationService.tableIdOf(request.getEntityId());
        if (!masterService.isPrimaryOf(gameTableId, actor.userId())) {
            throw new ForbiddenActionException(
                    "Only the Primary master of table " + gameTableId + " can resolve a veto request on it",
                    ForbiddenActionException.NOT_PRIMARY_MASTER);
        }
    }

    /**
     * The requester's row, locked, with their account checked - what every submit starts with.
     *
     * <p>The locking read is the first thing the transaction does with that row, and it has to be
     * (#252): two submits of the same type by one person each read "nothing pending" from their own
     * snapshot and both insert. See {@link #submit} for the whole reasoning; it is written once here
     * so the three submits cannot drift apart on it.
     */
    private User requireAllowedRequester(CurrentUser actor) {
        User requester = userRepository
                .lockById(actor.userId())
                .orElseThrow(() -> new NotFoundException("User not found: " + actor.userId()));
        if (requester.getStatus() != UserStatus.Allowed) {
            throw new ForbiddenActionException(
                    "User " + requester.getId() + " is " + requester.getStatus() + " and cannot open requests");
        }
        return requester;
    }

    /**
     * The row itself: the no-duplicates guard, the #78 check, then the insert.
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
     *   <li>a {@code Secondary} asking to veto a second person while the {@code Primary} has not yet
     *       answered the first - which {@link #listBanRequests} openly assumes is possible, since it
     *       exists to let a {@code Primary} tell two open requests apart.</li>
     * </ul>
     *
     * <p>What the duplicate guard is actually for survives intact, because it moves with the entity:
     * «this table is already waiting on a pause» and «somebody already asked about this person».
     *
     * @param entityId what the request is about. For the first three types it is the requester; for
     *                 {@code TablePause} and {@code PlayerBan} it is the table or the application
     */
    private ApprovalRequest openRequest(
            ApprovalRequestType type, String entityId, User requester, String justification) {
        requireNoPendingDuplicate(type, entityId, requester);
        // #78, the part the database cannot do: the reference is checked BEFORE the row exists.
        requireEntityExists(type.entityType(), entityId, null);
        // No notification to the admins, on purpose (#100): the queue is a view over this row.
        return approvalRequestRepository.save(
                new ApprovalRequest(type, type.entityType(), entityId, requester, justification));
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

    /** Holding the role right now, which is not the same as running a table (#135). */
    private boolean holdsMaster(String userId) {
        return userRoleRepository.findActiveRoleNames(userId).contains(PlatformRole.MASTER.roleName());
    }
}
