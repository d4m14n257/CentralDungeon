package com.centraldungeon.features.auth;

import com.centraldungeon.common.exception.UnauthorizedException;
import com.centraldungeon.common.model.IdGenerator;
import com.centraldungeon.common.security.InvalidJwtException;
import com.centraldungeon.common.security.JwtService;
import com.centraldungeon.common.security.RefreshTokenClaims;
import com.centraldungeon.features.users.UserAuthSnapshot;
import com.centraldungeon.features.users.UserService;
import com.centraldungeon.features.users.UserStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The session's whole life: opening it at login, renewing it, and ending it.
 *
 * <p>A session is a <b>family</b> of refresh tokens. The login opens one; every renewal spends the
 * presented token and issues its successor in the same family; logging out revokes the family. Since
 * every token the server hands out is recorded, a token that was already spent can be recognised when it
 * comes back, and that is a reuse: somebody holds a copy. The server cannot tell the thief from the
 * person, so it ends the family for both - except within {@link #ROTATION_GRACE} of the rotation while
 * the family is still alive, which is a renewal the browser never received, not a copy.
 *
 * <p>The renewal is also the moment the system re-asks "is this person still allowed in": status and
 * roles are re-read from the database, never trusted from the old token, because it is the only regular
 * point where the answer can change without the user doing anything.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /**
     * How long after a rotation the spent token still renews, as long as its family is alive. Long enough
     * to cover a reload that aborts the renewal response in flight; short enough that a copy stolen later
     * is a reuse.
     */
    static final Duration ROTATION_GRACE = Duration.ofSeconds(30);

    private final JwtService jwtService;
    private final UserService userService;
    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * @param jwtService             signs and verifies the tokens
     * @param userService            re-reads status and roles at every renewal
     * @param refreshTokenRepository the record of every refresh token handed out
     */
    public AuthService(JwtService jwtService, UserService userService, RefreshTokenRepository refreshTokenRepository) {
        this.jwtService = jwtService;
        this.userService = userService;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /**
     * Opens a session: a new family with its first refresh token. Called when a login succeeds.
     *
     * @param userId who logged in
     * @return the refresh token, for the cookie only
     */
    @Transactional
    public String openSession(String userId) {
        return issueInFamily(userId, IdGenerator.newId());
    }

    /**
     * Renews the access token, spending the presented refresh token and issuing its successor.
     *
     * <p><b>A rotation the browser never received</b> is not a reuse. Reloading the page while a renewal
     * is in flight aborts its response after the server already rotated: the new cookie never arrives,
     * and the next page presents the spent token. So a token spent less than {@link #ROTATION_GRACE} ago,
     * whose family still has a live token, renews once more: the live token - the successor nobody
     * received - is revoked and a new one is issued in the same family. Outside that window, or once the
     * family has nothing live (it was logged out, or a reuse already ended it), presenting a spent token
     * is a reuse: the family is revoked and the call gets a 401.
     *
     * <p><b>Two renewals with the same token</b>, at the same time, run one after the other: the row of
     * the presented token is locked for the whole transaction. The first spends it and gets the
     * successor; the second finds it spent within the window with a live family, so it also renews, and
     * the successor the first one received dies. Both get a 200, and only the last token issued keeps
     * renewing. That window is acceptable even against a stolen copy: the thief only gets in right after
     * a legitimate rotation, and the token the thief obtains kills the owner's; when the owner presents
     * theirs again, past the window, it is a reuse and the whole family - the thief's token included - is
     * revoked.
     *
     * <p>The revocation of a reuse is committed even though the call ends in a 401, which is why this
     * transaction does not roll back on {@link UnauthorizedException}.
     *
     * @param refreshCookie the refresh token from the cookie, or null when the browser sent none
     * @return the new access token and the successor refresh token
     * @throws UnauthorizedException when there is no token, it is invalid or expired, the server never
     *         issued it, it was already spent and is not a lost rotation, or with {@code ACCOUNT_BLOCKED} when the account
     *         is blocked
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public RefreshResult refresh(@Nullable String refreshCookie) {
        if (refreshCookie == null || refreshCookie.isBlank()) {
            throw new UnauthorizedException("Missing refresh token");
        }
        RefreshTokenClaims claims;
        try {
            claims = jwtService.verifyRefreshToken(refreshCookie);
        } catch (InvalidJwtException e) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        RefreshToken presented = refreshTokenRepository.findByJtiHashForUpdate(hash(claims.jti()))
                .orElseThrow(() -> new UnauthorizedException("Refresh token was never issued, user " + claims.userId()));
        LocalDateTime now = LocalDateTime.now();
        boolean lostRotation = presented.isRevoked() && isLostRotation(presented, now);
        if (presented.isRevoked() && !lostRotation) {
            int revoked = refreshTokenRepository.revokeFamily(presented.getFamilyId(), now);
            log.warn("Refresh token reused for user {}: family {} revoked ({} live tokens)",
                    presented.getUserId(), presented.getFamilyId(), revoked);
            throw new UnauthorizedException("Refresh token reused, family " + presented.getFamilyId());
        }

        String userId = presented.getUserId();
        UserAuthSnapshot snapshot = userService.loadAuthSnapshot(userId);
        if (snapshot.status() != UserStatus.Allowed) {
            throw new UnauthorizedException("Account is blocked", UnauthorizedException.ACCOUNT_BLOCKED);
        }

        if (lostRotation) {
            // The successor never reached the browser: it dies, and the one issued below takes its place.
            refreshTokenRepository.revokeFamily(presented.getFamilyId(), now);
        } else {
            presented.revoke(now);
        }
        String refreshToken = issueInFamily(userId, presented.getFamilyId());
        return new RefreshResult(jwtService.issueAccessToken(userId), refreshToken, jwtService.accessTokenTtl().toSeconds());
    }

    /**
     * Ends the session the token belongs to, on the server: every live token of its family is revoked.
     *
     * <p>Never fails. With no token, an invalid one or one the server does not know there is nothing to
     * end, and logging out must work regardless - the caller clears the cookie either way.
     *
     * @param refreshCookie the refresh token from the cookie, or null when the browser sent none
     */
    @Transactional
    public void logout(@Nullable String refreshCookie) {
        if (refreshCookie == null || refreshCookie.isBlank()) {
            return;
        }
        RefreshTokenClaims claims;
        try {
            claims = jwtService.verifyRefreshToken(refreshCookie);
        } catch (InvalidJwtException e) {
            return;
        }
        refreshTokenRepository.findByJtiHash(hash(claims.jti()))
                .ifPresent(token -> refreshTokenRepository.revokeFamily(token.getFamilyId(), LocalDateTime.now()));
    }

    /**
     * A rotation whose response never arrived: the token was spent moments ago and its family is still
     * alive, so the only token that revoked it singly is the successor - logging out and a reuse revoke
     * the whole family and leave nothing live.
     */
    private boolean isLostRotation(RefreshToken presented, LocalDateTime now) {
        return presented.wasRevokedAfter(now.minus(ROTATION_GRACE))
                && refreshTokenRepository.existsByFamilyIdAndRevokedAtIsNullAndExpiresAtAfter(presented.getFamilyId(), now);
    }

    private String issueInFamily(String userId, String familyId) {
        String token = jwtService.issueRefreshToken(userId);
        RefreshTokenClaims claims = jwtService.verifyRefreshToken(token);
        refreshTokenRepository.save(new RefreshToken(
                hash(claims.jti()), familyId, userId, LocalDateTime.ofInstant(claims.expiresAt(), ZoneOffset.UTC)));
        return token;
    }

    /** Hex SHA-256. The jti is a random UUID, so an unsalted hash is enough to make the row useless as a token. */
    private static String hash(String jti) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(jti.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
