package com.centraldungeon.approvals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.centraldungeon.common.security.JwtService;
import com.centraldungeon.notifications.Notification;
import com.centraldungeon.notifications.NotificationRepository;
import com.centraldungeon.notifications.NotificationType;
import com.centraldungeon.tables.GameTableRepository;
import com.centraldungeon.users.PlatformRole;
import com.centraldungeon.users.Role;
import com.centraldungeon.users.RoleRepository;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserRepository;
import com.centraldungeon.users.UserRole;
import com.centraldungeon.users.UserRoleChangeRepository;
import com.centraldungeon.users.UserRoleRepository;
import com.centraldungeon.users.UserService;
import com.centraldungeon.users.UserStatusChangeRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * The six endpoints of F3.2, over a socket and against MySQL.
 *
 * <p>Four things exist nowhere else, and a service test cannot reach any of them: the
 * {@code @PreAuthorize} on each method, Jackson's binding of {@link ApprovalRequestType} from a
 * request body, the shape of the {@code ProblemDetail} the frontend branches on (#197) - the four
 * new codes included - and the {@code @PageableDefault}. The {@code Location} of the 201 is a fifth.
 *
 * <p><b>The matrix of fase-3-admin-owner.md 3, walked.</b> §7 of that document names the risk:
 * {@code hasRole('ADMIN')} where {@code hasAnyRole('ADMIN','OWNER')} was meant locks the owner out
 * of a screen and nobody notices, because in development the test actor is usually an admin. The
 * same shape {@code AdminUserApiIT.everyRouteAnswersTheSameToAnAdminAndToAnOwner} has, for the four
 * admin routes of this slice.
 *
 * <p>And the IDOR of {@code /requests/mine}, proven against the database rather than read off the
 * service: the actor filter is forced with an {@code AND} inside
 * {@code ApprovalSearchSpecification.mine}, so the explicit attempt - {@code ?q=/requested_by
 * somebody-else} - has to come back empty rather than come back with their rows.
 *
 * <p>Starting the context at all is itself a check, and it caught the one defect that stopped
 * everything: {@code approval_requests} is created by {@code V1__baseline.sql} and has been since
 * before there was a backend, so a migration of its own is a second {@code CREATE TABLE} of the same
 * table and Flyway refuses it on every fresh database. With the schema built by the baseline alone,
 * this is also where {@code ddl-auto: validate} gets to accept {@link ApprovalRequest} against the
 * real table - {@code deleted_at}, deliberately unmapped, included.
 */
@SpringBootTest
@Testcontainers
class ApprovalRequestApiIT {

    @Container
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    private static final String MINE = "/api/v1/requests";
    private static final String ADMIN = "/api/v1/admin/requests";

    /**
     * Built by hand rather than with {@code @AutoConfigureMockMvc}: Boot 4 moved that annotation into
     * a {@code spring-boot-webmvc-test} module this project does not depend on, and the security
     * filter chain - which is half of what is being tested - is wired explicitly instead.
     */
    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private UserRoleChangeRepository userRoleChangeRepository;

    @Autowired
    private UserStatusChangeRepository userStatusChangeRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ApprovalRequestRepository approvalRequestRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    /** Only ever asked how many rows it has: approving a {@code TableOpen} must not add one. */
    @Autowired
    private GameTableRepository gameTableRepository;

    /** Used once, to put a row into a state no endpoint of F3.2 can produce - see {@code breakTheReferenceOf}. */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserService userService;

    private User owner;
    private User admin;
    private User player;
    private User other;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();

        approvalRequestRepository.deleteAll();
        notificationRepository.deleteAll();
        userRoleChangeRepository.deleteAll();
        userStatusChangeRepository.deleteAll();
        userRoleRepository.deleteAll();
        userRepository.deleteAll();

        owner = person("the-owner");
        grant(owner, PlatformRole.OWNER);
        admin = person("the-admin");
        grant(admin, PlatformRole.ADMIN);
        player = person("just-a-player");
        grant(player, PlatformRole.PLAYER);
        other = person("somebody-else");
        grant(other, PlatformRole.PLAYER);

        // The filter reads roles through the cached snapshot; the fixtures wrote them directly.
        for (User user : List.of(owner, admin, player, other)) {
            userService.evictAuthCache(user.getId());
        }
    }

    // ---------------------------------------------------------------- the matrix

    /**
     * <b>The assertion a {@code hasRole('ADMIN')} would not survive.</b> The four admin routes, both
     * ranks, and the owner's answer has to be the admin's answer. The two resolutions need a request
     * each, because the second one on the same row would be a 409 about the state and not about the
     * rank - which would hide exactly the failure this test is for.
     */
    @Test
    void everyAdminRouteAnswersTheSameToAnAdminAndToAnOwner() throws Exception {
        for (User actor : new User[] {admin, owner}) {
            as(get(ADMIN), actor).andExpect(status().isOk());

            String toApprove = openRequest(player, ApprovalRequestType.General, "for the " + actor.getName());
            as(get(ADMIN + "/" + toApprove), actor).andExpect(status().isOk());
            approve(actor, toApprove, "fine by me").andExpect(status().isOk());

            String toReject = openRequest(other, ApprovalRequestType.General, "for the " + actor.getName());
            reject(actor, toReject, "not this time").andExpect(status().isOk());
        }
    }

    /** Neither rank: the {@code @PreAuthorize} answers, with the code the frontend branches on. */
    @Test
    void aPlayerIsRefusedEveryAdminRouteWithForbidden() throws Exception {
        String request = openRequest(player, ApprovalRequestType.General, "mine");

        as(get(ADMIN), player)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
        // Their own request, through the admin door: still a 403. The rank is what the route asks for.
        as(get(ADMIN + "/" + request), player).andExpect(status().isForbidden());
        approve(player, request, "approving myself").andExpect(status().isForbidden());
        reject(player, request, "rejecting myself").andExpect(status().isForbidden());
    }

    /** And the asking end is open to anybody logged in - that is what makes #42 a door, not a privilege. */
    @Test
    void thePlainPlayerCanAskAndCanReadTheirOwn() throws Exception {
        submit(player, "General", "I need help with something").andExpect(status().isCreated());
        as(get(MINE + "/mine"), player)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    /** No token at all is a 401 and not a 403: the difference is what the frontend's ForbiddenState reads. */
    @Test
    void anAnonymousCallerIsUnauthorized() throws Exception {
        mockMvc.perform(get(ADMIN)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(MINE + "/mine")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(MINE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"General\",\"justification\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- the 201 and its shape

    /** The {@code Location} of the 201 points at where the request actually lives, and the body is the detail. */
    @Test
    void submittingAnswersCreatedWithALocationAndTheDetail() throws Exception {
        String body = submit(player, "MasterGrant", "quiero dirigir una mesa")
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.type").value("MasterGrant"))
                .andExpect(jsonPath("$.status").value("Pending"))
                .andExpect(jsonPath("$.entityType").value("user"))
                .andExpect(jsonPath("$.entityId").value(player.getId()))
                .andExpect(jsonPath("$.requestedByName").value(player.getName()))
                .andExpect(jsonPath("$.justification").value("quiero dirigir una mesa"))
                .andExpect(jsonPath("$.claimedByName").doesNotExist())
                .andExpect(jsonPath("$.resolvedByName").doesNotExist())
                .andExpect(jsonPath("$.resolvedAt").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String id = idOf(body);
        // The header is not decoration: an admin has to be able to follow it.
        as(get(ADMIN + "/" + id), admin).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
    }

    /**
     * {@link ApprovalRequestType} crosses HTTP, so its {@code @JsonValue} carries the binding in both
     * directions. This is the bloqueante of #253 in the one place it could repeat: the API publishes
     * {@code "MasterGrant"}, and a body that sends anything else is a 400 with the code the frontend
     * reads - never a 500, and never a silent success.
     */
    @Test
    void theRequestTypeIsBoundFromTheSpellingTheApiPublishes() throws Exception {
        submit(player, "MasterGrant", "the published spelling").andExpect(status().isCreated());
        submit(other, "MASTERGRANT", "the shouted one")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        submit(other, "master_grant", "the snake one")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        // The two F3.4 leaves out are not types yet, and the wire says so rather than accepting them.
        submit(other, "TablePause", "not until F3.4")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        submit(other, "PlayerBan", "not until F3.4")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    /** The justification is mandatory at both ends (#42), and a missing type is a 400 too. */
    @Test
    void aBlankReasonIsRejectedOnBothEnds() throws Exception {
        submit(player, "General", "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        mockMvc.perform(bearer(post(MINE), player)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"justification\":\"no type at all\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        String request = openRequest(player, ApprovalRequestType.General, "something");
        approve(admin, request, "  ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        reject(admin, request, "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        // Refused whole: nothing was half-applied.
        assertThat(approvalRequestRepository.findById(request).orElseThrow().getStatus())
                .isEqualTo(ApprovalStatus.Pending);
    }

    // ---------------------------------------------------------------- the four new codes

    /** The four 409s of the contract, each over the wire and each with its own code (#197). */
    @Test
    void theFourNewConflictCodesTravelAsTheContractDeclaresThem() throws Exception {
        // REQUEST_ALREADY_PENDING: the same type twice.
        submit(player, "General", "first").andExpect(status().isCreated());
        submit(player, "General", "second")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_ALREADY_PENDING"));
        // A different type from the same person is not the same request.
        submit(player, "TableOpen", "another kind").andExpect(status().isCreated());

        // MASTER_ROLE_ALREADY_HELD: asking for what is already held.
        User master = person("already-a-master");
        grant(master, PlatformRole.MASTER);
        userService.evictAuthCache(master.getId());
        submit(master, "MasterGrant", "again please")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MASTER_ROLE_ALREADY_HELD"));

        // REQUEST_ALREADY_RESOLVED: a resolution is never re-resolved.
        String resolved = openRequest(other, ApprovalRequestType.General, "answer me");
        approve(admin, resolved, "yes").andExpect(status().isOk());
        approve(admin, resolved, "yes again")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_ALREADY_RESOLVED"));
        reject(owner, resolved, "no, actually")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_ALREADY_RESOLVED"));

        // REQUEST_ENTITY_GONE: the row outlives what it points at (#126), but resolving it would be
        // acting on a ghost.
        //
        // The reference is broken with one UPDATE rather than by deleting the account, and that is
        // not a shortcut - it is the only way in. **In F3.2 this 409 cannot be reached by any
        // sequence of API calls**: all three types point at the requester, and `requested_by` has a
        // real foreign key, so the row the reference names is exactly the row the database refuses to
        // delete while the request exists. The guard is still worth having and worth pinning here -
        // F3.4 adds `game_table` and `table_registration`, neither of which any foreign key protects,
        // and that is the day it starts firing. See the report: this is an observation, not a defect.
        String orphaned = openRequest(other, ApprovalRequestType.TableOpen, "about to point at nothing");
        breakTheReferenceOf(orphaned);
        approve(admin, orphaned, "cannot")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_ENTITY_GONE"));
        reject(admin, orphaned, "cannot either")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_ENTITY_GONE"));
    }

    /** Nobody with that id is a 404 with NOT_FOUND, on the read and on the two writes. */
    @Test
    void anUnknownRequestIsNotFound() throws Exception {
        String missing = UUID.randomUUID().toString().replace("-", "");
        as(get(ADMIN + "/" + missing), admin)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        approve(admin, missing, "into the void").andExpect(status().isNotFound());
        reject(admin, missing, "into the void").andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- what approving does

    /**
     * The sentence of fase-3-admin-owner.md:128, over HTTP: approving a {@code MasterGrant} grants
     * the role <b>through {@code UserRoleService}</b>, which is visible from outside precisely
     * because that service is what writes the audit row and evicts the snapshot. A second path that
     * wrote {@code users_roles} directly would leave the trail empty.
     */
    @Test
    void approvingAMasterGrantGrantsTheRoleThroughTheOnePathThatWritesRoles() throws Exception {
        String request = openRequest(player, ApprovalRequestType.MasterGrant, "quiero dirigir");

        approve(admin, request, "tiene experiencia")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Approved"))
                .andExpect(jsonPath("$.resolvedByName").value(admin.getName()))
                .andExpect(jsonPath("$.resolutionNote").value("tiene experiencia"))
                .andExpect(jsonPath("$.resolvedAt").exists());

        assertThat(userRoleRepository.findActiveRoleNames(player.getId()))
                .as("the role has to be live, and granted by UserRoleService")
                .contains(PlatformRole.MASTER.roleName());
        assertThat(userRoleChangeRepository.findByUser_IdOrderByCreatedAtAsc(player.getId()))
                .as("the audit row is what proves it went through UserRoleService and not a second path")
                .isNotEmpty()
                .allSatisfy(change -> assertThat(change.getJustification()).isEqualTo("tiene experiencia"));

        // And the snapshot was evicted, so the new master is a master on the very next request.
        assertThat(userService.loadAuthSnapshot(player.getId()).roles())
                .contains(PlatformRole.MASTER.roleName());
    }

    /**
     * <b>Approving a {@code TableOpen} creates no table.</b> The rule that is easiest to implement
     * one step too far: the request carries no name, no system, no seats and no agenda, so there is
     * nothing to create - the admin creates it {@code Unassigned} and assigns a master (#72), which
     * is the same circuit from its other end (#90).
     */
    @Test
    void approvingATableOpenCreatesNoTable() throws Exception {
        long before = countGameTables();
        String request = openRequest(player, ApprovalRequestType.TableOpen, "no hay mesas de terror");

        approve(admin, request, "abrimos una").andExpect(status().isOk());

        assertThat(countGameTables()).as("approving a TableOpen must not create a table").isEqualTo(before);
        assertThat(userRoleRepository.findActiveRoleNames(player.getId()))
                .as("and it must not grant anything either")
                .doesNotContain(PlatformRole.MASTER.roleName());
    }

    /** Rejecting has no effect beyond being resolved - and the reason is as mandatory as on a yes (#42). */
    @Test
    void rejectingAMasterGrantGrantsNothing() throws Exception {
        String request = openRequest(player, ApprovalRequestType.MasterGrant, "quiero dirigir");

        reject(owner, request, "todavía no")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Rejected"))
                .andExpect(jsonPath("$.resolutionNote").value("todavía no"));

        assertThat(userRoleRepository.findActiveRoleNames(player.getId()))
                .doesNotContain(PlatformRole.MASTER.roleName());
    }

    /**
     * Submitting rings no bell (#100) and resolving rings exactly one, for the person who asked - and
     * it points where they can actually go: their own profile, never {@code /admin/requests}.
     */
    @Test
    void theResolutionNotifiesTheRequesterAndNothingElseDoes() throws Exception {
        String request = openRequest(player, ApprovalRequestType.MasterGrant, "quiero dirigir");
        assertThat(notificationRepository.findAll())
                .as("a request notifies nobody: the queue is a view over the row (#100)")
                .isEmpty();

        approve(admin, request, "adelante").andExpect(status().isOk());

        List<Notification> inbox = notificationRepository.findAll();
        assertThat(inbox).hasSize(1);
        Notification notification = inbox.getFirst();
        assertThat(notification.getUser().getId()).isEqualTo(player.getId());
        assertThat(notification.getNotificationType()).isEqualTo(NotificationType.ApprovalRequestApproved);
        assertThat(notification.getRelatedEntityType()).isEqualTo("user");
        assertThat(notification.getRelatedEntityId()).isEqualTo(player.getId());

        String rejected = openRequest(other, ApprovalRequestType.General, "una consulta");
        reject(admin, rejected, "no corresponde").andExpect(status().isOk());
        assertThat(notificationRepository.findAll())
                .filteredOn(n -> n.getNotificationType() == NotificationType.ApprovalRequestRejected)
                .singleElement()
                .satisfies(n -> assertThat(n.getUser().getId()).isEqualTo(other.getId()));
    }

    // ---------------------------------------------------------------- /requests/mine, and the IDOR

    /**
     * <b>The IDOR, tried on purpose.</b> The actor filter is not part of the query language: it is
     * forced with an {@code AND} inside {@code ApprovalSearchSpecification.mine}, so asking for
     * somebody else by name cannot widen the result - it can only narrow it to nothing. Anything
     * else here would be one person reading another's justifications.
     */
    @Test
    void mineNeverLeaksSomebodyElsesRequestsHoweverTheQueryAsks() throws Exception {
        openRequest(player, ApprovalRequestType.MasterGrant, "lo mío");
        openRequest(other, ApprovalRequestType.MasterGrant, "lo ajeno");

        as(get(MINE + "/mine"), player)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].justification").value("lo mío"));

        // The explicit attempt, by display name and by Discord handle.
        for (String needle : new String[] {other.getName(), other.getDiscordUsername(), "lo ajeno"}) {
            as(get(MINE + "/mine").param("q", "/requested_by " + needle), player)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }
        // Bare text matching only the other person's justification is the same attempt without a command.
        as(get(MINE + "/mine").param("q", "ajeno"), player)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        // An `/or` cannot widen it either: the forced predicate is ANDed with the whole fold.
        as(get(MINE + "/mine").param("q", "/requested_by " + other.getName() + " /or lo"), player)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].justification").value("lo mío"));
    }

    /**
     * The question the screen actually asks, in the string the frontend builds: "do I have one of
     * these open?". If the parser and the builder disagree the answer is silently wrong and the
     * button comes back - which is the fragile point F3.1 found the expensive way.
     */
    @Test
    void mineAnswersTheDoIAlreadyHaveOneQuestion() throws Exception {
        String resolved = openRequest(player, ApprovalRequestType.MasterGrant, "el viejo");
        approve(admin, resolved, "concedido").andExpect(status().isOk());
        openRequest(player, ApprovalRequestType.General, "una consulta abierta");

        as(get(MINE + "/mine").param("q", "/status Pending"), player)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("General"));
        as(get(MINE + "/mine").param("q", "/status Pending /and /request_type MasterGrant"), player)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        as(get(MINE + "/mine").param("q", "/status Pending /and /request_type General"), player)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        // A value outside the enum is empty, never a 400 (arquitectura.md 2.5).
        as(get(MINE + "/mine").param("q", "/status Maybe"), player)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // ---------------------------------------------------------------- the admin listing

    /** {@code @PageableDefault}: twenty per page, oldest first with a tie-break by id (#171, #173). */
    @Test
    void theAdminListingDefaultsToTwentyPerPageOldestFirst() throws Exception {
        for (int i = 0; i < 25; i++) {
            User asker = person(String.format("bulk-%02d", i));
            openRequest(asker, ApprovalRequestType.General, String.format("motivo %02d", i));
        }

        as(get(ADMIN), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.content.length()").value(20))
                .andExpect(jsonPath("$.content[0].justification").value("motivo 00"))
                .andExpect(jsonPath("$.content[19].justification").value("motivo 19"));

        // The tie-break is what makes page two the rest of them rather than a reshuffle (#171).
        as(get(ADMIN).param("page", "1"), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.content[0].justification").value("motivo 20"));
    }

    /** No implicit filter: an empty query is every request in every state, like every other listing. */
    @Test
    void theAdminListingWithoutAQueryShowsEveryState() throws Exception {
        String approved = openRequest(player, ApprovalRequestType.MasterGrant, "aprobado");
        approve(admin, approved, "sí").andExpect(status().isOk());
        String rejected = openRequest(other, ApprovalRequestType.MasterGrant, "rechazado");
        reject(admin, rejected, "no").andExpect(status().isOk());
        openRequest(player, ApprovalRequestType.General, "pendiente");

        as(get(ADMIN), admin).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
        as(get(ADMIN).param("q", "/status Pending"), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].justification").value("pendiente"));
    }

    /**
     * The three commands, the bare text and the connectors - the whole search language of this
     * screen, through the URL and against MySQL. Neither side of it had ever met the other.
     */
    @Test
    void theSearchLanguageNarrowsTheAdminListingOverHttp() throws Exception {
        openRequest(player, ApprovalRequestType.MasterGrant, "quiero dirigir terror");
        openRequest(other, ApprovalRequestType.TableOpen, "no hay mesas de terror");
        openRequest(person("carla"), ApprovalRequestType.General, "una consulta suelta");

        // /request_type
        as(get(ADMIN).param("q", "/request_type MasterGrant"), admin)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].requestedByName").value(player.getName()));
        // /requested_by, by display name and by Discord handle - both, because whoever searches knows
        // one of the two and not which one the system keeps where.
        as(get(ADMIN).param("q", "/requested_by carla"), admin).andExpect(jsonPath("$.totalElements").value(1));
        as(get(ADMIN).param("q", "/requested_by somebody-else"), admin).andExpect(jsonPath("$.totalElements").value(1));
        // Bare text: the justification *and* the requester's name at once.
        as(get(ADMIN).param("q", "terror"), admin).andExpect(jsonPath("$.totalElements").value(2));
        as(get(ADMIN).param("q", "carla"), admin).andExpect(jsonPath("$.totalElements").value(1));
        // The connectors fold left to right with no precedence (#164).
        as(get(ADMIN).param("q", "/request_type MasterGrant /or /request_type TableOpen"), admin)
                .andExpect(jsonPath("$.totalElements").value(2));
        as(get(ADMIN).param("q", "terror /and /request_type TableOpen"), admin)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].requestedByName").value(other.getName()));
        // A value outside either enum matches nothing and is never a 400 (arquitectura.md 2.5).
        as(get(ADMIN).param("q", "/request_type TablePause"), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        as(get(ADMIN).param("q", "/status Maybe"), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        // And a command the parser does not know is text, not a 400 either.
        as(get(ADMIN).param("q", "/nonsense terror"), admin).andExpect(status().isOk());
    }

    /**
     * <b>The page's total and the page's content come from the same predicate.</b> Spring Data builds
     * a second {@code CriteriaQuery} for the count, and the join to {@code users} that
     * {@code /requested_by} adds has to survive it - the failure being guarded against is a screen
     * showing rows with a total of zero, silently and only for one command. It is the same shape
     * {@code UserRoleServiceIT.theRoleFilterCountsTheSamePeopleItLists} pins for F3.1.
     */
    @Test
    void aFilteredListingCountsTheSameRowsItShows() throws Exception {
        // One Pending request per type and per person, so seven rows means seven people - which is
        // also what makes the `/requested_by` join the thing being counted.
        for (int i = 0; i < 7; i++) {
            openRequest(person("noisy-" + i), ApprovalRequestType.General, String.format("ruido %02d", i));
        }
        for (int i = 0; i < 3; i++) {
            openRequest(person("quiet-" + i), ApprovalRequestType.General, "silencio");
        }

        as(get(ADMIN).param("q", "/requested_by noisy-").param("size", "4"), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(4))
                .andExpect(jsonPath("$.totalElements").value(7))
                .andExpect(jsonPath("$.totalPages").value(2));
        as(get(ADMIN).param("q", "/requested_by noisy-").param("size", "4").param("page", "1"), admin)
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.totalElements").value(7));
        // The same, through the bare-text branch, which ORs the justification with two joined columns.
        as(get(ADMIN).param("q", "ruido").param("size", "4"), admin)
                .andExpect(jsonPath("$.totalElements").value(7));
    }

    /** {@code claimedByName} is null on every row: F3.2 leaves the two columns and no behaviour (#100). */
    @Test
    void nothingInThisSliceEverClaimsARequest() throws Exception {
        openRequest(player, ApprovalRequestType.General, "sin reservar");
        as(get(ADMIN), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].claimedByName").doesNotExist());
        assertThat(approvalRequestRepository.findAll()).allSatisfy(request -> {
            assertThat(request.getClaimedBy()).isNull();
            assertThat(request.getClaimedAt()).isNull();
        });
    }

    // ---------------------------------------------------------------- helpers

    private ResultActions submit(User actor, String type, String justification) throws Exception {
        return mockMvc.perform(bearer(post(MINE), actor)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"" + type + "\",\"justification\":\"" + justification + "\"}"));
    }

    private ResultActions approve(User actor, String id, String note) throws Exception {
        return mockMvc.perform(bearer(post(ADMIN + "/" + id + "/approve"), actor)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resolutionNote\":\"" + note + "\"}"));
    }

    private ResultActions reject(User actor, String id, String note) throws Exception {
        return mockMvc.perform(bearer(post(ADMIN + "/" + id + "/reject"), actor)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resolutionNote\":\"" + note + "\"}"));
    }

    /**
     * Points one request at an id nothing has, with an UPDATE and no JPA in the way.
     *
     * <p>There is no API that does this and there cannot be one in F3.2 - see the comment at the call
     * site. A plain UPDATE is the honest way to put the row into the state F3.4 will produce on its
     * own, so that the guard reading it is exercised rather than assumed.
     */
    private void breakTheReferenceOf(String requestId) {
        jdbcTemplate.update(
                "update approval_requests set entity_id = ? where id = ?",
                UUID.randomUUID().toString().replace("-", ""),
                requestId);
    }

    /** Opens one through the real endpoint - the fixture and the feature are the same code path. */
    private String openRequest(User actor, ApprovalRequestType type, String justification) throws Exception {
        String body = submit(actor, type.wireName(), justification)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return idOf(body);
    }

    private static String idOf(String json) {
        int start = json.indexOf("\"id\":\"") + 6;
        return json.substring(start, json.indexOf('"', start));
    }

    private long countGameTables() {
        return gameTableRepository.count();
    }

    private ResultActions as(MockHttpServletRequestBuilder request, User actor) throws Exception {
        return mockMvc.perform(bearer(request, actor));
    }

    private MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder request, User actor) {
        return request.header("Authorization", "Bearer " + jwtService.issueAccessToken(actor.getId()));
    }

    private User person(String discordUsername) {
        User user = new User(UUID.randomUUID().toString().replace("-", ""), discordUsername);
        user.setName(discordUsername);
        return userRepository.save(user);
    }

    private void grant(User user, PlatformRole role) {
        Role entity = roleRepository.findByName(role.roleName()).orElseThrow();
        userRoleRepository.save(new UserRole(user, entity));
    }
}
