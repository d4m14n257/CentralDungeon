package com.centraldungeon.matrix;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.centraldungeon.common.config.JwtProperties;
import com.centraldungeon.common.security.JwtService;
import com.centraldungeon.features.users.PlatformRole;
import com.centraldungeon.features.users.User;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * <b>One access token, many requests: what the person may do is whatever the database says at the
 * moment of each request.</b> The token is minted once and reused across a revoked role, a block and
 * an unblock, and no wait for the cache's time-to-live sits between the steps - the change itself
 * drops the cached entry. Also what the filter does with tokens that must never authenticate anybody.
 */
class StaleTokenIT extends AbstractRoleMatrixIT {

    private static final String TABLES = "/api/v1/game-tables";
    private static final String ADMIN_USERS = "/api/v1/admin/users";
    private static final String ME = "/api/v1/users/me";

    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private CacheManager cacheManager;

    private MatrixFixtures world;
    private User owner;
    private User admin;
    private User master;

    @Override
    List<MatrixRow> rows() {
        return List.of();
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context)
                .addFilters(context.getBean(FilterChainProxy.class))
                .build();
        world = MatrixFixtures.forCell(context, MatrixActor.ANONYMOUS);
        owner = world.person("owner", PlatformRole.OWNER);
        admin = world.person("admin", PlatformRole.ADMIN);
        master = world.person("master", PlatformRole.PLAYER, PlatformRole.MASTER);
        cacheManager.getCache("userAuth").clear();
    }

    @Test
    void aRevokedMasterRoleStopsWorkingWithTheSameToken() throws Exception {
        String token = jwtService.issueAccessToken(master.getId());

        createTable(token).andExpect(status().isCreated());
        revokeRole(admin, master, "Master").andExpect(status().isOk());

        createTable(token).andExpect(status().isForbidden());
    }

    @Test
    void aRevokedAdminRoleStopsWorkingWithTheSameToken() throws Exception {
        String token = jwtService.issueAccessToken(admin.getId());

        mockMvc.perform(get(ADMIN_USERS).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        revokeRole(owner, admin, "Admin").andExpect(status().isOk());

        mockMvc.perform(get(ADMIN_USERS).header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
    }

    @Test
    void anUnblockedAccountPassesAgainWithTheSameToken() throws Exception {
        String token = jwtService.issueAccessToken(master.getId());
        String adminToken = jwtService.issueAccessToken(admin.getId());

        mockMvc.perform(get(ME).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mockMvc.perform(post(ADMIN_USERS + "/" + master.getId() + "/block")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"justification\":\"misbehaving\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get(ME).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_BLOCKED"));

        mockMvc.perform(post(ADMIN_USERS + "/" + master.getId() + "/unblock")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"justification\":\"forgiven\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get(ME).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    @Test
    void aRefreshTokenIsNotAnAccessToken() throws Exception {
        expectUnauthorized(jwtService.issueRefreshToken(master.getId()));
    }

    @Test
    void anUnsignedAlgNoneTokenIsRejected() throws Exception {
        expectUnauthorized(new PlainJWT(claims(master.getId(), Duration.ofMinutes(5))).serialize());
    }

    @Test
    void aTokenSignedWithAnotherKeyIsRejected() throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims(master.getId(), Duration.ofMinutes(5)));
        jwt.sign(new MACSigner("another-key-that-is-at-least-32-bytes-long".getBytes()));

        expectUnauthorized(jwt.serialize());
    }

    @Test
    void anExpiredTokenIsRejected() throws Exception {
        JwtService expired = new JwtService(
                new JwtProperties(jwtProperties.secret(), Duration.ofMinutes(-5), jwtProperties.refreshTokenTtl()));

        expectUnauthorized(expired.issueAccessToken(master.getId()));
    }

    @Test
    void aTokenOfAnUnknownPersonIsRejected() throws Exception {
        expectUnauthorized(jwtService.issueAccessToken(UUID.randomUUID().toString().replace("-", "")));
    }

    private void expectUnauthorized(String token) throws Exception {
        mockMvc.perform(get(ME).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    private ResultActions createTable(String token) throws Exception {
        return mockMvc.perform(post(TABLES)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(world.tableJson()));
    }

    private ResultActions revokeRole(User actor, User target, String role) throws Exception {
        return mockMvc.perform(post(ADMIN_USERS + "/" + target.getId() + "/revoke-role")
                .header("Authorization", "Bearer " + jwtService.issueAccessToken(actor.getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"" + role + "\",\"justification\":\"stale token test\"}"));
    }

    private static JWTClaimsSet claims(String subject, Duration ttl) {
        return new JWTClaimsSet.Builder()
                .subject(subject)
                .claim("type", "ACCESS")
                .expirationTime(new Date(System.currentTimeMillis() + ttl.toMillis()))
                .build();
    }
}
