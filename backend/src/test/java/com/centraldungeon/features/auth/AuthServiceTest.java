package com.centraldungeon.features.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.centraldungeon.common.exception.UnauthorizedException;
import com.centraldungeon.common.security.InvalidJwtException;
import com.centraldungeon.common.security.JwtService;
import com.centraldungeon.common.security.RefreshTokenClaims;
import com.centraldungeon.features.users.UserAuthSnapshot;
import com.centraldungeon.features.users.UserService;
import com.centraldungeon.features.users.UserStatus;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserService userService;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(jwtService, userService, refreshTokenRepository);
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
        when(jwtService.verifyRefreshToken("bad-token")).thenThrow(new InvalidJwtException("expired"));

        assertThatThrownBy(() -> authService.refresh("bad-token"))
                .isInstanceOf(UnauthorizedException.class)
                .extracting(e -> ((UnauthorizedException) e).getErrorCode())
                .isEqualTo("UNAUTHORIZED");
    }

    @Test
    void rejectsASignedTokenTheServerNeverIssued() {
        when(jwtService.verifyRefreshToken("token")).thenReturn(claims("jti-1"));
        when(refreshTokenRepository.findByJtiHashForUpdate(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("token")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void aSpentTokenRevokesItsWholeFamily() {
        RefreshToken spent = new RefreshToken("hash", "family-1", "user-1", LocalDateTime.now().plusDays(1));
        spent.revoke(LocalDateTime.now());
        when(jwtService.verifyRefreshToken("token")).thenReturn(claims("jti-1"));
        when(refreshTokenRepository.findByJtiHashForUpdate(anyString())).thenReturn(Optional.of(spent));

        assertThatThrownBy(() -> authService.refresh("token"))
                .isInstanceOf(UnauthorizedException.class)
                .extracting(e -> ((UnauthorizedException) e).getErrorCode())
                .isEqualTo("UNAUTHORIZED");
        verify(refreshTokenRepository).revokeFamily(eq("family-1"), any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void aTokenSpentMomentsAgoWithALiveFamilyRenewsAndKillsTheUnreceivedSuccessor() {
        RefreshToken spent = new RefreshToken("hash", "family-1", "user-1", LocalDateTime.now().plusDays(1));
        spent.revoke(LocalDateTime.now().minusSeconds(5));
        when(jwtService.verifyRefreshToken("token")).thenReturn(claims("jti-1"));
        when(refreshTokenRepository.findByJtiHashForUpdate(anyString())).thenReturn(Optional.of(spent));
        when(refreshTokenRepository.existsByFamilyIdAndRevokedAtIsNullAndExpiresAtAfter(eq("family-1"), any())).thenReturn(true);
        when(userService.loadAuthSnapshot("user-1")).thenReturn(new UserAuthSnapshot("user-1", UserStatus.Allowed, Set.of("Player")));
        when(jwtService.issueAccessToken("user-1")).thenReturn("new-access");
        when(jwtService.issueRefreshToken("user-1")).thenReturn("new-refresh");
        when(jwtService.verifyRefreshToken("new-refresh")).thenReturn(claims("jti-2"));
        when(jwtService.accessTokenTtl()).thenReturn(Duration.ofMinutes(15));

        RefreshResult result = authService.refresh("token");

        assertThat(result.refreshToken()).isEqualTo("new-refresh");
        verify(refreshTokenRepository).revokeFamily(eq("family-1"), any());
        ArgumentCaptor<RefreshToken> issued = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(issued.capture());
        assertThat(issued.getValue().getFamilyId()).isEqualTo("family-1");
        assertThat(issued.getValue().isRevoked()).isFalse();
    }

    @Test
    void aTokenSpentPastTheGraceWindowIsAReuseEvenWithALiveFamily() {
        RefreshToken spent = new RefreshToken("hash", "family-1", "user-1", LocalDateTime.now().plusDays(1));
        spent.revoke(LocalDateTime.now().minus(AuthService.ROTATION_GRACE).minusSeconds(5));
        when(jwtService.verifyRefreshToken("token")).thenReturn(claims("jti-1"));
        when(refreshTokenRepository.findByJtiHashForUpdate(anyString())).thenReturn(Optional.of(spent));

        assertThatThrownBy(() -> authService.refresh("token")).isInstanceOf(UnauthorizedException.class);
        verify(refreshTokenRepository).revokeFamily(eq("family-1"), any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void aTokenSpentMomentsAgoWhoseFamilyWasEndedIsAReuse() {
        RefreshToken loggedOut = new RefreshToken("hash", "family-1", "user-1", LocalDateTime.now().plusDays(1));
        loggedOut.revoke(LocalDateTime.now().minusSeconds(5));
        when(jwtService.verifyRefreshToken("token")).thenReturn(claims("jti-1"));
        when(refreshTokenRepository.findByJtiHashForUpdate(anyString())).thenReturn(Optional.of(loggedOut));
        when(refreshTokenRepository.existsByFamilyIdAndRevokedAtIsNullAndExpiresAtAfter(eq("family-1"), any())).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh("token")).isInstanceOf(UnauthorizedException.class);
        verify(refreshTokenRepository, never()).save(any());
        verify(userService, never()).loadAuthSnapshot(anyString());
    }

    @Test
    void rejectsARefreshForABlockedUserWithoutSpendingTheToken() {
        RefreshToken live = new RefreshToken("hash", "family-1", "user-1", LocalDateTime.now().plusDays(1));
        when(jwtService.verifyRefreshToken("token")).thenReturn(claims("jti-1"));
        when(refreshTokenRepository.findByJtiHashForUpdate(anyString())).thenReturn(Optional.of(live));
        when(userService.loadAuthSnapshot("user-1")).thenReturn(new UserAuthSnapshot("user-1", UserStatus.Blocked, Set.of()));

        assertThatThrownBy(() -> authService.refresh("token"))
                .isInstanceOf(UnauthorizedException.class)
                .extracting(e -> ((UnauthorizedException) e).getErrorCode())
                .isEqualTo(UnauthorizedException.ACCOUNT_BLOCKED);
        assertThat(live.isRevoked()).isFalse();
    }

    @Test
    void spendsThePresentedTokenAndIssuesItsSuccessorInTheSameFamily() {
        RefreshToken live = new RefreshToken("hash", "family-1", "user-1", LocalDateTime.now().plusDays(1));
        when(jwtService.verifyRefreshToken("token")).thenReturn(claims("jti-1"));
        when(refreshTokenRepository.findByJtiHashForUpdate(anyString())).thenReturn(Optional.of(live));
        when(userService.loadAuthSnapshot("user-1")).thenReturn(new UserAuthSnapshot("user-1", UserStatus.Allowed, Set.of("Player")));
        when(jwtService.issueAccessToken("user-1")).thenReturn("new-access");
        when(jwtService.issueRefreshToken("user-1")).thenReturn("new-refresh");
        when(jwtService.verifyRefreshToken("new-refresh")).thenReturn(claims("jti-2"));
        when(jwtService.accessTokenTtl()).thenReturn(Duration.ofMinutes(15));

        RefreshResult result = authService.refresh("token");

        assertThat(result.accessToken()).isEqualTo("new-access");
        assertThat(result.refreshToken()).isEqualTo("new-refresh");
        assertThat(result.expiresInSeconds()).isEqualTo(900);
        assertThat(live.isRevoked()).isTrue();
        ArgumentCaptor<RefreshToken> successor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(successor.capture());
        assertThat(successor.getValue().getFamilyId()).isEqualTo("family-1");
        assertThat(successor.getValue().isRevoked()).isFalse();
    }

    @Test
    void logoutWithoutAUsableTokenRevokesNothing() {
        when(jwtService.verifyRefreshToken("bad")).thenThrow(new InvalidJwtException("malformed"));

        authService.logout(null);
        authService.logout("bad");

        verify(refreshTokenRepository, never()).revokeFamily(anyString(), any());
    }

    private static RefreshTokenClaims claims(String jti) {
        return new RefreshTokenClaims("user-1", jti, Instant.now().plusSeconds(3600));
    }
}
