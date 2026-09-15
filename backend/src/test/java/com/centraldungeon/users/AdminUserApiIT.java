package com.centraldungeon.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.centraldungeon.common.security.JwtService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
 * <b>The matrix of fase-3-admin-owner.md 3, walked rather than tabulated.</b> §7 of that document
 * names the risk this class exists to answer: {@code hasRole('ADMIN')} where
 * {@code hasAnyRole('ADMIN','OWNER')} was meant leaves the owner outside a screen and nobody
 * notices, because in development the test actor is usually an admin. So every one of the seven
 * routes is called by <em>both</em> ranks, and the expectation for the owner is the same 200 the
 * admin gets - not a 403 the table would have let slip.
 *
 * <p>It goes over HTTP because that is the only place four things exist at all: the
 * {@code @PreAuthorize} on each method, Jackson's binding of {@code PlatformRole} from the body,
 * the shape of the {@code ProblemDetail} the frontend branches on (#197), and the
 * {@code @PageableDefault}. None of them is reachable from a service test.
 *
 * <p>Real tokens through the real {@link JwtService} and the real filter chain, so the roles are
 * read from the database the way {@code JwtAuthenticationFilter} reads them on every request (#122)
 * - which is also what makes the Testcontainers MySQL necessary here.
 */
@SpringBootTest
@Testcontainers
class AdminUserApiIT {

    @Container
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    private static final String BASE = "/api/v1/admin/users";

    /**
     * Built by hand rather than with {@code @AutoConfigureMockMvc}: Boot 4 moved that annotation into
     * a {@code spring-boot-webmvc-test} module this project does not depend on, and the security
     * filter chain - which is the whole point here - is wired explicitly instead.
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
    private UserService userService;

    private User owner;
    private User admin;
    private User plainPlayer;
    private User target;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();

        userRoleChangeRepository.deleteAll();
        userStatusChangeRepository.deleteAll();
        userRoleRepository.deleteAll();
        userRepository.deleteAll();

        owner = person("the-owner");
        grant(owner, PlatformRole.OWNER);
        admin = person("the-admin");
        grant(admin, PlatformRole.ADMIN);
        plainPlayer = person("just-a-player");
        grant(plainPlayer, PlatformRole.PLAYER);
        target = person("the-target");
        grant(target, PlatformRole.PLAYER);

        // The filter reads roles through the cached snapshot; the fixtures wrote them directly.
        userService.evictAuthCache(owner.getId());
        userService.evictAuthCache(admin.getId());
        userService.evictAuthCache(plainPlayer.getId());
        userService.evictAuthCache(target.getId());
    }

    // ---------------------------------------------------------------- the shared surface

    /**
     * Row 1 of the matrix: the whole administration surface is shared. Seven routes, both ranks, and
     * the owner's answer has to be the admin's answer - this is the assertion that would have caught
     * a {@code hasRole('ADMIN')}.
     */
    @Test
    void everyRouteAnswersTheSameToAnAdminAndToAnOwner() throws Exception {
        for (User actor : new User[] {admin, owner}) {
            as(get(BASE), actor).andExpect(status().isOk());
            as(get(BASE + "/" + target.getId()), actor).andExpect(status().isOk());
            as(get(BASE + "/" + target.getId() + "/history"), actor).andExpect(status().isOk());
            grantRole(actor, target, "Master", "shared surface").andExpect(status().isOk());
            revokeRole(actor, target, "Master", "shared surface").andExpect(status().isOk());
            block(actor, target, "shared surface").andExpect(status().isOk());
            unblock(actor, target, "shared surface").andExpect(status().isOk());
        }
    }

    /** Neither rank: the {@code @PreAuthorize} answers, with the code the frontend branches on. */
    @Test
    void aPlayerIsRefusedEveryRouteWithForbidden() throws Exception {
        as(get(BASE), plainPlayer)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
        as(get(BASE + "/" + target.getId()), plainPlayer).andExpect(status().isForbidden());
        as(get(BASE + "/" + target.getId() + "/history"), plainPlayer).andExpect(status().isForbidden());
        grantRole(plainPlayer, target, "Master", "nope").andExpect(status().isForbidden());
        revokeRole(plainPlayer, target, "Master", "nope").andExpect(status().isForbidden());
        block(plainPlayer, target, "nope").andExpect(status().isForbidden());
        unblock(plainPlayer, target, "nope").andExpect(status().isForbidden());
    }

    /** No token at all is a 401, not a 403: the difference matters to the frontend's ForbiddenState. */
    @Test
    void anAnonymousCallerIsUnauthorized() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE + "/" + target.getId() + "/block")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"justification\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- the one row that differs

    /**
     * <b>The whole difference between the two ranks in F3</b>: who may hand out the rank. An admin
     * reaching for Admin or Owner is a 403 and not a 400 - the request is well formed and what makes
     * it fail is who sent it.
     */
    @Test
    void anAdminCannotGrantOrRevokeTheRank() throws Exception {
        for (String rank : new String[] {"Admin", "Owner"}) {
            grantRole(admin, target, rank, "trying to make another one")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("ROLE_GRANT_FORBIDDEN"));
            revokeRole(admin, target, rank, "trying to unmake one")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("ROLE_GRANT_FORBIDDEN"));
        }
    }

    /** And the owner may, which is the other half of the same row. */
    @Test
    void anOwnerCanGrantTheRank() throws Exception {
        grantRole(owner, target, "Admin", "promoting")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.hasItem("Admin")));
    }

    // ---------------------------------------------------------------- blocking

    /** Nobody blocks a privileged account - not an admin, not the owner, not themselves. */
    @Test
    void nobodyCanBlockAnAccountHoldingTheRank() throws Exception {
        block(owner, admin, "peer")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("CANNOT_BLOCK_PRIVILEGED"));
        block(admin, owner, "peer")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("CANNOT_BLOCK_PRIVILEGED"));
        block(owner, owner, "myself")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("CANNOT_BLOCK_PRIVILEGED"));
        block(admin, admin, "myself")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("CANNOT_BLOCK_PRIVILEGED"));
    }

    /** The two conflicts of the status pair, over the wire. */
    @Test
    void blockingTwiceAndUnblockingSomebodyAllowedAreTheirOwnConflicts() throws Exception {
        block(admin, target, "first").andExpect(status().isOk());
        block(admin, target, "second")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("USER_ALREADY_BLOCKED"));

        unblock(admin, target, "first").andExpect(status().isOk());
        unblock(admin, target, "second")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_BLOCKED"));
    }

    // ---------------------------------------------------------------- the owner invariant

    /** The `Se prueba:` of F3.1, over HTTP: the owner cannot step down. */
    @Test
    void anOwnerRevokingTheirOwnRoleGetsTheConflict() throws Exception {
        revokeRole(owner, owner, "Owner", "stepping down")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CANNOT_REVOKE_OWN_OWNER"));
    }

    /**
     * The demotion takes effect on the very next request, which is the eviction of #128 seen from
     * outside: the owner who was just demoted comes back holding nothing, and the
     * {@code @PreAuthorize} - not the service - is what turns them away. A cache that had not been
     * dropped would have let them through for the rest of the TTL.
     */
    @Test
    void aDemotedOwnerIsTurnedAwayOnTheVeryNextRequest() throws Exception {
        User second = person("second-owner");
        grant(second, PlatformRole.OWNER);
        userService.evictAuthCache(second.getId());

        revokeRole(second, owner, "Owner", "demoting the other one").andExpect(status().isOk());
        revokeRole(second, second, "Owner", "and now myself")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CANNOT_REVOKE_OWN_OWNER"));
        revokeRole(owner, second, "Owner", "the actor is no longer an owner")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    // ---------------------------------------------------------------- shape of the wire

    /**
     * {@code PlatformRole} is the one enum whose constants are not spelled the way the wire spells
     * them, so its {@code @JsonValue} carries the binding in both directions. The API publishes
     * {@code "Master"}; a body that sends back {@code "MASTER"} is a 400 and not a silent success.
     *
     * <p><b>Currently red, and on purpose.</b> The contract of F3.1 lists {@code VALIDATION_ERROR}
     * for an invalid role, and the answer is a 500 {@code INTERNAL_ERROR}: nothing in
     * {@code GlobalExceptionHandler} handles {@code HttpMessageNotReadableException}, so a body
     * Jackson cannot bind falls through to {@code handleUnexpected} - the very hole its own
     * {@code handleTypeMismatch} was written to close for query parameters.
     */
    @Test
    void theRoleIsBoundFromTheSpellingTheApiPublishes() throws Exception {
        grantRole(owner, target, "Master", "the published spelling").andExpect(status().isOk());
        grantRole(owner, target, "MASTER", "the enum constant")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    /** A blank reason is a 400 on all four mutators - the justification is what makes a block answerable. */
    @Test
    void aBlankJustificationIsRejectedOnEveryMutator() throws Exception {
        grantRole(owner, target, "Master", "  ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        revokeRole(owner, target, "Master", "").andExpect(status().isBadRequest());
        block(owner, target, "  ").andExpect(status().isBadRequest());
        unblock(owner, target, "").andExpect(status().isBadRequest());
    }

    /** Nobody with that id is a 404 with NOT_FOUND, on the read and on the write. */
    @Test
    void anUnknownPersonIsNotFound() throws Exception {
        String missing = UUID.randomUUID().toString().replace("-", "");
        as(get(BASE + "/" + missing), owner)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        as(get(BASE + "/" + missing + "/history"), owner).andExpect(status().isNotFound());
        mockMvc.perform(bearer(post(BASE + "/" + missing + "/block"), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"justification\":\"why\"}"))
                .andExpect(status().isNotFound());
    }

    /** {@code @PageableDefault}: twenty per page, ordered by Discord handle with a tie-break by id (#171, #173). */
    @Test
    void theListingDefaultsToTwentyPerPageSortedByHandle() throws Exception {
        for (int i = 0; i < 25; i++) {
            person(String.format("bulk-%02d", i));
        }
        as(get(BASE), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.content.length()").value(20))
                .andExpect(jsonPath("$.content[0].discordUsername").value("bulk-00"));
    }

    /** The listing's rows carry no {@code discordId}: it is third-party data no administrator needs. */
    @Test
    void theListingNeverPublishesTheDiscordId() throws Exception {
        String body = as(get(BASE), owner).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("discordId");
    }

    /** The `?q=` the frontend builds is the `?q=` the parser reads - `/role` included. */
    @Test
    void theRoleCommandNarrowsTheListingOverHttp() throws Exception {
        as(get(BASE).param("q", "/role Owner"), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].discordUsername").value("the-owner"));
        as(get(BASE).param("q", "/role Wizard"), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        as(get(BASE).param("q", "/status Blocked"), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    /** The history is the panel's source, and it reads what the mutators wrote. */
    @Test
    void theHistoryReadsBackWhatTheMutatorsWrote() throws Exception {
        grantRole(owner, target, "Master", "runs a table").andExpect(status().isOk());
        block(owner, target, "behaviour").andExpect(status().isOk());

        as(get(BASE + "/" + target.getId() + "/history"), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("RoleGranted"))
                .andExpect(jsonPath("$[0].role").value("Master"))
                .andExpect(jsonPath("$[0].justification").value("runs a table"))
                .andExpect(jsonPath("$[0].changedByName").value("the-owner"))
                .andExpect(jsonPath("$[1].type").value("StatusChanged"))
                .andExpect(jsonPath("$[1].toStatus").value("Blocked"));
    }

    // ---------------------------------------------------------------- helpers

    private ResultActions grantRole(User actor, User on, String role, String justification) throws Exception {
        return mockMvc.perform(bearer(post(BASE + "/" + on.getId() + "/grant-role"), actor)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"" + role + "\",\"justification\":\"" + justification + "\"}"));
    }

    private ResultActions revokeRole(User actor, User on, String role, String justification) throws Exception {
        return mockMvc.perform(bearer(post(BASE + "/" + on.getId() + "/revoke-role"), actor)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"" + role + "\",\"justification\":\"" + justification + "\"}"));
    }

    private ResultActions block(User actor, User on, String justification) throws Exception {
        return mockMvc.perform(bearer(post(BASE + "/" + on.getId() + "/block"), actor)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"justification\":\"" + justification + "\"}"));
    }

    private ResultActions unblock(User actor, User on, String justification) throws Exception {
        return mockMvc.perform(bearer(post(BASE + "/" + on.getId() + "/unblock"), actor)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"justification\":\"" + justification + "\"}"));
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
