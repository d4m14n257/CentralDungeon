package com.centraldungeon.features.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.centraldungeon.common.LockGate;
import com.centraldungeon.common.security.JwtService;
import com.centraldungeon.common.security.RefreshCookieFactory;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * The session's life through its only two cookie-authenticated endpoints: the refresh rotates, a
 * spent token presented again ends the whole session unless it is a rotation the browser never
 * received, and logging out ends it too.
 *
 * <p>A spent token still renews for a few seconds after its rotation, so the tests that present one
 * as a reuse first push every revocation past that window, as if the reuse came later.
 *
 * <p>Logging in goes through the test login, which issues the session the same way the Discord
 * callback does, so these tests do not depend on how a session is recorded - only on what the
 * endpoints answer.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class RefreshTokenRotationIT {

    private static final String LOGIN = "/api/v1/auth/test-login";
    private static final String REFRESH = "/api/v1/auth/refresh";
    private static final String LOGOUT = "/api/v1/auth/logout";

    @Container
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        // The local .env turns it off for plain-http development; the attribute under test is the deployed one.
        registry.add("app.cookie.secure", () -> "true");
    }

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    /** The cookie is out of reach of scripts, only sent over TLS, never cross-site, and only to the auth endpoints. */
    @Test
    void theRefreshCookieCarriesItsSecurityAttributes() throws Exception {
        MvcResult login = login();
        assertSecureCookie(login);

        MvcResult refreshed = mockMvc.perform(withCsrf(post(REFRESH)).cookie(refreshCookieOf(login)))
                .andExpect(status().isOk())
                .andReturn();
        assertSecureCookie(refreshed);
    }

    /** Logout is authenticated by the cookie, so a page on another site must not be able to force it. */
    @Test
    void logoutWithoutTheCsrfHeaderIsForbidden() throws Exception {
        mockMvc.perform(post(LOGOUT).cookie(refreshCookieOf(login()))).andExpect(status().isForbidden());
    }

    /** With the header, logout clears the cookie. */
    @Test
    void logoutWithTheCsrfHeaderClearsTheCookie() throws Exception {
        MvcResult logout = mockMvc.perform(withCsrf(post(LOGOUT)).cookie(refreshCookieOf(login())))
                .andExpect(status().isNoContent())
                .andReturn();

        Cookie cleared = logout.getResponse().getCookie(RefreshCookieFactory.COOKIE_NAME);
        assertThat(cleared).isNotNull();
        assertThat(cleared.getMaxAge()).isZero();
    }

    /** Rotating spends the presented token: it renews exactly once, and the new one renews in its place. */
    @Test
    void aRotatedRefreshTokenDoesNotRenewAgain() throws Exception {
        Cookie first = refreshCookieOf(login());
        Cookie second = refreshCookieOf(refresh(first, 200));
        pushRevocationsPastTheGraceWindow();

        refresh(first, 401);
        assertThat(second.getValue()).isNotEqualTo(first.getValue());
    }

    /** Presenting a spent token is a reuse: the token that replaced it dies too, whoever holds it. */
    @Test
    void reusingARotatedTokenRevokesTheWholeFamily() throws Exception {
        Cookie first = refreshCookieOf(login());
        Cookie second = refreshCookieOf(refresh(first, 200));
        pushRevocationsPastTheGraceWindow();

        refresh(first, 401);

        refresh(second, 401);
    }

    /**
     * A reload aborts the renewal response after the server rotated: the browser presents the spent token
     * again moments later. It renews, and the successor it never received is the one that dies.
     */
    @Test
    void aRotationTheBrowserNeverReceivedRenewsOnceMore() throws Exception {
        Cookie first = refreshCookieOf(login());
        Cookie lost = refreshCookieOf(refresh(first, 200));

        Cookie replacement = refreshCookieOf(refresh(first, 200));

        assertThat(isRevoked(lost)).as("the successor the browser never received").isTrue();
        assertThat(isRevoked(replacement)).isFalse();
        refresh(replacement, 200);
    }

    /** A reuse ends one session, not every session of the person: another login keeps working. */
    @Test
    void aReuseDoesNotTouchTheSameUsersOtherSessions() throws Exception {
        String discordId = "e2e-two-" + UUID.randomUUID().toString().substring(0, 12);
        Cookie laptop = refreshCookieOf(login(discordId));
        Cookie phone = refreshCookieOf(login(discordId));
        refreshCookieOf(refresh(laptop, 200));
        pushRevocationsPastTheGraceWindow();

        refresh(laptop, 401);

        refresh(phone, 200);
    }

    /** Logging out ends the session on the server too, not only in the browser that held the cookie. */
    @Test
    void logoutRevokesTheFamily() throws Exception {
        Cookie first = refreshCookieOf(login());
        Cookie second = refreshCookieOf(refresh(first, 200));

        mockMvc.perform(withCsrf(post(LOGOUT)).cookie(second)).andExpect(status().isNoContent());

        refresh(second, 401);
    }

    /** A token signed with the right key but never handed out by a login is not a session. */
    @Test
    void aSignedButNeverIssuedRefreshTokenIsRefused() throws Exception {
        MvcResult login = login();
        String userId = jwtService.verifyRefreshToken(refreshCookieOf(login).getValue()).userId();

        refresh(new Cookie(RefreshCookieFactory.COOKIE_NAME, jwtService.issueRefreshToken(userId)), 401);
    }

    /**
     * Two renewals with the same token at the same moment: both renew, and the later one kills the token
     * the earlier one received. Whoever holds the dead token - the owner, if the other one was a thief -
     * presents it past the window as a reuse, and that ends the session for both.
     */
    @Test
    void twoSimultaneousRenewalsWithOneTokenLeaveOneLiveTokenUntilTheDeadOneComesBack() throws Exception {
        Cookie presented = refreshCookieOf(login());
        String jtiHash = jtiHashOf(presented);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<MvcResult> results = new ArrayList<>();
        try (LockGate gate = LockGate.hold(transactionManager,
                () -> refreshTokenRepository.findByJtiHashForUpdate(jtiHash).orElseThrow())) {
            Callable<MvcResult> renew = () -> mockMvc.perform(withCsrf(post(REFRESH)).cookie(presented)).andReturn();
            List<Future<MvcResult>> contenders = List.of(pool.submit(renew), pool.submit(renew));
            gate.awaitWaiters(LockGate.monitor(mysql), 2, Duration.ofSeconds(20));
            gate.open();
            for (Future<MvcResult> contender : contenders) {
                results.add(contender.get(30, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(results).extracting(result -> result.getResponse().getStatus()).containsExactly(200, 200);
        List<Cookie> issued = results.stream().map(RefreshTokenRotationIT::refreshCookieOf).toList();
        List<Cookie> dead = issued.stream().filter(this::isRevoked).toList();
        List<Cookie> live = issued.stream().filter(cookie -> !isRevoked(cookie)).toList();
        assertThat(dead).hasSize(1);
        assertThat(live).hasSize(1);

        pushRevocationsPastTheGraceWindow();
        refresh(dead.getFirst(), 401);
        refresh(live.getFirst(), 401);
    }

    /** Logout answers the same with no session at all, so it can always be pressed. */
    @Test
    void logoutWithoutASessionStillSucceeds() throws Exception {
        mockMvc.perform(withCsrf(post(LOGOUT))).andExpect(status().isNoContent());
        mockMvc.perform(withCsrf(post(LOGOUT)).cookie(new Cookie(RefreshCookieFactory.COOKIE_NAME, "not-a-jwt")))
                .andExpect(status().isNoContent());
    }

    /** Ages every revocation beyond the grace window, so a spent token presented next is a reuse. */
    private void pushRevocationsPastTheGraceWindow() {
        jdbcTemplate.update("update refresh_tokens set revoked_at = revoked_at - interval ? second where revoked_at is not null",
                AuthService.ROTATION_GRACE.toSeconds() + 1);
    }

    private boolean isRevoked(Cookie refreshCookie) {
        return refreshTokenRepository.findByJtiHash(jtiHashOf(refreshCookie)).orElseThrow().isRevoked();
    }

    private String jtiHashOf(Cookie refreshCookie) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(jwtService.verifyRefreshToken(refreshCookie.getValue()).jti().getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private MvcResult login() throws Exception {
        return login("e2e-rot-" + UUID.randomUUID().toString().substring(0, 12));
    }

    private MvcResult login(String discordId) throws Exception {
        return mockMvc.perform(post(LOGIN).param("discordId", discordId)).andExpect(status().isOk()).andReturn();
    }

    private MvcResult refresh(Cookie refreshCookie, int expectedStatus) throws Exception {
        return mockMvc.perform(withCsrf(post(REFRESH)).cookie(refreshCookie))
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }

    private static MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) {
        String csrf = UUID.randomUUID().toString();
        return request.cookie(new Cookie("XSRF-TOKEN", csrf)).header("X-XSRF-TOKEN", csrf);
    }

    private static Cookie refreshCookieOf(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(RefreshCookieFactory.COOKIE_NAME);
        assertThat(cookie).as("the response sets the refresh cookie").isNotNull();
        return new Cookie(RefreshCookieFactory.COOKIE_NAME, cookie.getValue());
    }

    /** Read off the cookie and not the header: MockMvc does not render generic attributes such as SameSite. */
    private static void assertSecureCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(RefreshCookieFactory.COOKIE_NAME);
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).as("HttpOnly").isTrue();
        assertThat(cookie.getSecure()).as("Secure").isTrue();
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Strict");
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
    }
}
