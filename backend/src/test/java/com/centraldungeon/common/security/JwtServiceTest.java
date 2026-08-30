package com.centraldungeon.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.centraldungeon.common.config.JwtProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(
            new JwtProperties("test-secret-at-least-32-bytes-long-0123456789", Duration.ofMinutes(15), Duration.ofDays(30)));

    @Test
    void roundTripsAnAccessToken() {
        String token = jwtService.issueAccessToken("user-1");

        String subject = jwtService.verifyAndGetSubject(token, TokenType.ACCESS);

        assertThat(subject).isEqualTo("user-1");
    }

    @Test
    void roundTripsARefreshToken() {
        String token = jwtService.issueRefreshToken("user-2");

        String subject = jwtService.verifyAndGetSubject(token, TokenType.REFRESH);

        assertThat(subject).isEqualTo("user-2");
    }

    @Test
    void rejectsAnAccessTokenPresentedAsRefresh() {
        String token = jwtService.issueAccessToken("user-1");

        assertThatThrownBy(() -> jwtService.verifyAndGetSubject(token, TokenType.REFRESH))
                .isInstanceOf(InvalidJwtException.class);
    }

    @Test
    void rejectsAMalformedToken() {
        assertThatThrownBy(() -> jwtService.verifyAndGetSubject("not-a-jwt", TokenType.ACCESS))
                .isInstanceOf(InvalidJwtException.class);
    }

    @Test
    void rejectsATokenSignedWithADifferentSecret() {
        JwtService otherService = new JwtService(
                new JwtProperties("a-completely-different-secret-32-bytes-min", Duration.ofMinutes(15), Duration.ofDays(30)));
        String token = otherService.issueAccessToken("user-1");

        assertThatThrownBy(() -> jwtService.verifyAndGetSubject(token, TokenType.ACCESS))
                .isInstanceOf(InvalidJwtException.class);
    }

    @Test
    void rejectsAnExpiredToken() throws InterruptedException {
        JwtService shortLivedService = new JwtService(
                new JwtProperties("test-secret-at-least-32-bytes-long-0123456789", Duration.ofMillis(1), Duration.ofDays(30)));
        String token = shortLivedService.issueAccessToken("user-1");
        Thread.sleep(50);

        assertThatThrownBy(() -> shortLivedService.verifyAndGetSubject(token, TokenType.ACCESS))
                .isInstanceOf(InvalidJwtException.class);
    }

    @Test
    void rejectsASecretShorterThan32Bytes() {
        JwtProperties tooShort = new JwtProperties("short-secret", Duration.ofMinutes(15), Duration.ofDays(30));

        assertThatThrownBy(() -> new JwtService(tooShort)).isInstanceOf(IllegalStateException.class);
    }
}
