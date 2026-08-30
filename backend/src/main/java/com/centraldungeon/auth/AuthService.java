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

@Service
public class AuthService {

    private final JwtService jwtService;
    private final UserService userService;

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
