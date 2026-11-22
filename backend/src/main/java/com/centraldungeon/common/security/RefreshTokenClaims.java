package com.centraldungeon.common.security;

import java.time.Instant;

/**
 * What a verified refresh token says about itself. Never crosses HTTP.
 *
 * @param userId    who the token identifies
 * @param jti       the token's unique id, which is how the server finds the row it recorded for it
 * @param expiresAt when the token expires
 */
public record RefreshTokenClaims(String userId, String jti, Instant expiresAt) {
}
