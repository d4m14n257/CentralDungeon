package com.centraldungeon.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.centraldungeon.common.exception.UnauthorizedException;
import com.centraldungeon.common.security.InvalidJwtException;
import com.centraldungeon.common.security.JwtService;
import com.centraldungeon.common.security.TokenType;
import com.centraldungeon.users.UserAuthSnapshot;
import com.centraldungeon.users.UserService;
import com.centraldungeon.users.UserStatus;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserService userService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(jwtService, userService);
    }

    @Test
    void rejectsANullRefreshCookie() {
        assertThatThrownBy(() -> authService.refresh(null)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectsABlankRefreshCookie() {
        assertThatThrownBy(() -> authService.refresh("   ")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectsAnInvalidRefreshToken() {
        when(jwtService.verifyAndGetSubject("bad-token", TokenType.REFRESH)).thenThrow(new InvalidJwtException("expired"));

        assertThatThrownBy(() -> authService.refresh("bad-token")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectsARefreshForABlockedUser() {
        when(jwtService.verifyAndGetSubject("token", TokenType.REFRESH)).thenReturn("user-1");
        when(userService.loadAuthSnapshot("user-1")).thenReturn(new UserAuthSnapshot("user-1", UserStatus.Blocked, Set.of()));

        assertThatThrownBy(() -> authService.refresh("token")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void reAffirmsStatusAndIssuesFreshTokens() {
        when(jwtService.verifyAndGetSubject("token", TokenType.REFRESH)).thenReturn("user-1");
        when(userService.loadAuthSnapshot("user-1")).thenReturn(new UserAuthSnapshot("user-1", UserStatus.Allowed, Set.of("Player")));
        when(jwtService.issueAccessToken("user-1")).thenReturn("new-access");
        when(jwtService.issueRefreshToken("user-1")).thenReturn("new-refresh");
        when(jwtService.accessTokenTtl()).thenReturn(Duration.ofMinutes(15));

        RefreshResult result = authService.refresh("token");

        assertThat(result.accessToken()).isEqualTo("new-access");
        assertThat(result.refreshToken()).isEqualTo("new-refresh");
        assertThat(result.expiresInSeconds()).isEqualTo(900);
    }
}
