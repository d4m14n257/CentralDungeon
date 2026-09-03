package com.centraldungeon.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The JWT settings, bound from {@code app.jwt.*}. A record and not a class with setters: this is
 * configuration read once at startup, and nothing should be able to change it afterwards.
 *
 * @param secret          the HMAC signing key. Comes from the environment through {@code .env}
 *                        (arquitectura.md 6.1) and is never written literally in a file that is
 *                        committed
 * @param accessTokenTtl  how long an access token stays valid. Short on purpose (~15 min, #125):
 *                        it is the window in which a stolen bearer token is worth anything
 * @param refreshTokenTtl how long a refresh token stays valid, and the max age of the cookie that
 *                        carries it
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {
}
