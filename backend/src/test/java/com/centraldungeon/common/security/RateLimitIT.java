package com.centraldungeon.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.centraldungeon.features.users.PlatformRole;
import com.centraldungeon.features.users.Role;
import com.centraldungeon.features.users.RoleRepository;
import com.centraldungeon.features.users.User;
import com.centraldungeon.features.users.UserRepository;
import com.centraldungeon.features.users.UserRole;
import com.centraldungeon.features.users.UserRoleRepository;
import com.centraldungeon.features.users.UserService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * The rate limits over HTTP, through the real security chain: the request past the limit gets a 429
 * with its code and {@code Retry-After}, per IP on the doors without a session and per user on the
 * writes of a signed-in person.
 *
 * <p>Every limit is lowered to {@value #LIMIT} so the tests stay short. The counters live as long as
 * the context, so each test uses its own IP or its own users and never sees another test's counts.
 */
@SpringBootTest(properties = {
    "app.rate-limit.login.requests=" + RateLimitIT.LIMIT,
    "app.rate-limit.refresh.requests=" + RateLimitIT.LIMIT,
    "app.rate-limit.upload.requests=" + RateLimitIT.LIMIT,
    "app.rate-limit.registration.requests=" + RateLimitIT.LIMIT,
    "app.rate-limit.catalog-proposal.requests=" + RateLimitIT.LIMIT,
    "app.rate-limit.master-candidates.requests=" + RateLimitIT.LIMIT,
})
@Testcontainers
class RateLimitIT {

    static final int LIMIT = 3;

    @Container
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

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
    private RoleRepository roleRepository;

    @Autowired
    private UserService userService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    void theLoginIsLimitedPerIp() throws Exception {
        for (int i = 0; i < LIMIT; i++) {
            mockMvc.perform(from("10.1.0.1", get("/oauth2/authorization/discord"))).andExpect(status().is3xxRedirection());
        }

        expectRateLimited(from("10.1.0.1", get("/oauth2/authorization/discord")));
        // The callback is the same door: it is refused too, before Spring even looks at its parameters.
        expectRateLimited(from("10.1.0.1", get("/login/oauth2/code/discord")));
        // Another address has its own counter.
        mockMvc.perform(from("10.1.0.2", get("/oauth2/authorization/discord"))).andExpect(status().is3xxRedirection());
    }

    @Test
    void anIpv6ClientIsCountedByItsSlash64NotByItsAddress() throws Exception {
        for (int i = 0; i < LIMIT; i++) {
            mockMvc.perform(from("2001:db8:a:b::" + (i + 1), get("/oauth2/authorization/discord")))
                    .andExpect(status().is3xxRedirection());
        }

        // Another address of the same /64 shares the counter; a different /64 has its own.
        expectRateLimited(from("2001:db8:a:b:ffff::9", get("/oauth2/authorization/discord")));
        mockMvc.perform(from("2001:db8:a:c::1", get("/oauth2/authorization/discord"))).andExpect(status().is3xxRedirection());
    }

    @Test
    void theRefreshIsLimitedPerIpEvenWhenItWouldBeRefused() throws Exception {
        // Without the cookie or the CSRF token every attempt is refused anyway, and still counts.
        for (int i = 0; i < LIMIT; i++) {
            int status = mockMvc.perform(from("10.2.0.1", post("/api/v1/auth/refresh"))).andReturn().getResponse().getStatus();
            assertThat(status).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        }

        expectRateLimited(from("10.2.0.1", post("/api/v1/auth/refresh")));
    }

    @Test
    void uploadingIsLimitedPerUserAndTwoUsersDoNotShareACounter() throws Exception {
        String first = bearer(player());
        String second = bearer(player());

        for (int i = 0; i < LIMIT; i++) {
            expectNotLimited(multipart("/api/v1/files").header("Authorization", first));
        }
        expectRateLimited(multipart("/api/v1/files").header("Authorization", first));
        // The library upload is an upload too, and spends the same allowance.
        expectRateLimited(multipart("/api/v1/admin/files").header("Authorization", first));

        expectNotLimited(multipart("/api/v1/files").header("Authorization", second));
    }

    @Test
    void proposingCatalogValuesIsLimitedPerUserAcrossTheThreeCatalogs() throws Exception {
        String first = bearer(player());
        String second = bearer(player());

        expectNotLimited(post("/api/v1/tags").header("Authorization", first));
        expectNotLimited(post("/api/v1/systems").header("Authorization", first));
        expectNotLimited(post("/api/v1/platforms").header("Authorization", first));

        // One counter for the three: the next proposal is refused whichever catalog it is for.
        expectRateLimited(post("/api/v1/tags").header("Authorization", first));
        expectRateLimited(post("/api/v1/systems").header("Authorization", first));
        expectRateLimited(post("/api/v1/platforms").header("Authorization", first));

        expectNotLimited(post("/api/v1/tags").header("Authorization", second));
    }

    @Test
    void theCoMasterSearchIsLimitedPerUser() throws Exception {
        String first = bearer(player());
        String second = bearer(player());

        for (int i = 0; i < LIMIT; i++) {
            expectNotLimited(get("/api/v1/game-tables/{id}/master-candidates", "missing-table").header("Authorization", first));
        }

        expectRateLimited(get("/api/v1/game-tables/{id}/master-candidates", "other-table").header("Authorization", first));
        expectNotLimited(get("/api/v1/game-tables/{id}/master-candidates", "missing-table").header("Authorization", second));
    }

    @Test
    void theUserIsTheOneFromTheTokenNotTheAddress() throws Exception {
        String token = bearer(player());

        for (int i = 0; i < LIMIT; i++) {
            expectNotLimited(from("10.3.0." + i, post("/api/v1/game-tables/{id}/registrations", "missing-table")
                    .header("Authorization", token)));
        }

        // A new address does not reset a per-user counter.
        expectRateLimited(from("10.3.0.99", post("/api/v1/game-tables/{id}/registrations", "missing-table")
                .header("Authorization", token)));
    }

    @Test
    void anAnonymousWriteIsNotCountedAndGetsItsOwn401() throws Exception {
        for (int i = 0; i <= LIMIT; i++) {
            mockMvc.perform(post("/api/v1/game-tables/{id}/registrations", "missing-table"))
                    .andExpect(status().isUnauthorized());
        }
    }

    private void expectRateLimited(RequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.errorParams.retryAfterSeconds").isString());
    }

    private void expectNotLimited(RequestBuilder request) throws Exception {
        int status = mockMvc.perform(request).andReturn().getResponse().getStatus();
        assertThat(status).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value()).isNotEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    private static MockHttpServletRequestBuilder from(String ip, MockHttpServletRequestBuilder request) {
        return request.with(r -> {
            r.setRemoteAddr(ip);
            return r;
        });
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.issueAccessToken(user.getId());
    }

    private User player() {
        String name = "rate-" + UUID.randomUUID().toString().substring(0, 8);
        User user = new User(UUID.randomUUID().toString().replace("-", ""), name);
        user.setName(name);
        user = userRepository.save(user);
        Role role = roleRepository.findByName(PlatformRole.PLAYER.roleName()).orElseThrow();
        userRoleRepository.save(new UserRole(user, role));
        userService.evictAuthCache(user.getId());
        return user;
    }
}
