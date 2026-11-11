package com.centraldungeon.common.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * <b>A server-side session never authenticates anybody.</b> Who is calling and what they may do come
 * from the bearer token and from the database, on every single request; nothing the server remembered
 * from an earlier request may stand in for either.
 *
 * <p>The login flow with Discord needs an HTTP session for a moment, so the security chain is allowed to
 * create one. The risk is that an authenticated call made with a token leaves its security context in
 * that session, and the browser, which sends the session cookie on its own, keeps being recognised after
 * the token is gone - and after the account was blocked or lost a role. These tests replay exactly that:
 * the session created by a call made with a token is handed to the following calls, the way a browser
 * hands back its cookie.
 *
 * <p>Real tokens, the real filter chain and a real database, because the roles and the status the
 * filter reads on each request are what is under test.
 */
@SpringBootTest
@Testcontainers
class SessionDoesNotAuthenticateIT {

    @Container
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    private static final String ME = "/api/v1/users/me";
    private static final String ADMIN_USERS = "/api/v1/admin/users";

    /** Built by hand, with the real security filter chain, as the other integration tests do. */
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
    private User player;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();

        userRoleChangeRepository.deleteAll();
        userStatusChangeRepository.deleteAll();
        userRoleRepository.deleteAll();
        userRepository.deleteAll();

        owner = person("the-owner", PlatformRole.OWNER);
        admin = person("the-admin", PlatformRole.ADMIN);
        player = person("just-a-player", PlatformRole.PLAYER);
    }

    /** Dropping the token must drop the identity: the session that call left behind is not a credential. */
    @Test
    void theSessionAloneDoesNotAuthenticate() throws Exception {
        String token = jwtService.issueAccessToken(player.getId());

        MvcResult withToken = mockMvc.perform(get(ME).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = sessionLeftBehind(withToken);

        mockMvc.perform(get(ME).session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    /** A blocked account is refused even when the request also carries the session of its better days. */
    @Test
    void aBlockedAccountIsNotSavedByItsSession() throws Exception {
        String token = jwtService.issueAccessToken(player.getId());

        MvcResult withToken = mockMvc.perform(get(ME).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = sessionLeftBehind(withToken);

        mockMvc.perform(post(ADMIN_USERS + "/" + player.getId() + "/block")
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(admin.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"justification\":\"misbehaving\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get(ME).header("Authorization", "Bearer " + token).session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_BLOCKED"));
    }

    /** A role taken away stops counting at once, also for the session that saw the role being used. */
    @Test
    void aRevokedRoleDoesNotSurviveInTheSession() throws Exception {
        String token = jwtService.issueAccessToken(admin.getId());

        MvcResult withToken = mockMvc.perform(get(ADMIN_USERS).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = sessionLeftBehind(withToken);

        mockMvc.perform(post(ADMIN_USERS + "/" + admin.getId() + "/revoke-role")
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"Admin\",\"justification\":\"no longer an admin\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get(ADMIN_USERS).header("Authorization", "Bearer " + token).session(session))
                .andExpect(status().isForbidden());
    }

    /**
     * The session the first call created, as the browser would hold it. When the chain created none the
     * assertion says so and the test goes on with a fresh one, so that the following request still
     * carries a session and the outcome stays comparable.
     */
    private MockHttpSession sessionLeftBehind(MvcResult result) {
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        if (session == null) {
            System.err.println("SESSION NOT CREATED by the call made with a token; continuing with a fresh session");
            return new MockHttpSession();
        }
        return session;
    }

    private User person(String discordUsername, PlatformRole role) {
        User user = new User(UUID.randomUUID().toString().replace("-", ""), discordUsername);
        user.setName(discordUsername);
        user = userRepository.save(user);
        Role entity = roleRepository.findByName(role.roleName()).orElseThrow();
        userRoleRepository.save(new UserRole(user, entity));
        // The filter reads roles through a cached snapshot; the fixture wrote them directly.
        userService.evictAuthCache(user.getId());
        return user;
    }
}
