package com.centraldungeon.auth;

import com.centraldungeon.common.exception.UnauthorizedException;
import com.centraldungeon.common.security.InvalidJwtException;
import com.centraldungeon.common.security.JwtService;
import com.centraldungeon.common.security.TokenType;
import com.centraldungeon.users.UserAuthSnapshot;
import com.centraldungeon.users.UserService;
import com.centraldungeon.users.UserStatus;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Renewing a session. It is the moment the system re-asks "is this person still allowed in", because
 * a refresh is the only regular point where the answer can change without the user doing anything
 * (#122, #125).
 *
 * <p>Rotating: every refresh issues a new refresh token, so a leaked one has a short useful life and
 * a reused one is detectable.
 */
@Service
public class AuthService {

    private final JwtService jwtService;
    private final UserService userService;

    /**
     * @param jwtService  validates the incoming refresh token and issues the new pair
     * @param userService  re-reads status and roles, which is the point of the re-affirmation
     */
    public AuthService(JwtService jwtService, UserService userService) {
        this.jwtService = jwtService;
        this.userService = userService;
    }

    /** The refresh is the re-affirmation point: status and roles are re-read from the database, never trusted from the old token (decisiones.md #122). */
    @Transactional(readOnly = true)
    public RefreshResult refresh(@Nullable String refreshCookie) {
        if (refreshCookie == null || refreshCookie.isBlank()) {
            throw new UnauthorizedException("Missing refresh token");
        }

        String userId;
        try {
            userId = jwtService.verifyAndGetSubject(refreshCookie, TokenType.REFRESH);
        } catch (InvalidJwtException e) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        UserAuthSnapshot snapshot = userService.loadAuthSnapshot(userId);
        if (snapshot.status() != UserStatus.Allowed) {
            throw new UnauthorizedException("User is not allowed to authenticate");
        }

        String accessToken = jwtService.issueAccessToken(userId);
        String refreshToken = jwtService.issueRefreshToken(userId);
        return new RefreshResult(accessToken, refreshToken, jwtService.accessTokenTtl().toSeconds());
    }
}
