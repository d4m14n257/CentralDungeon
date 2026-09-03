package com.centraldungeon.auth;

/**
 * What a successful refresh produces, before it is split between the response body and the cookie.
 *
 * <p>Package-private and not in {@code dto/} on purpose: it never crosses HTTP. The access token
 * goes in a {@link com.centraldungeon.auth.dto.TokenResponse} and the refresh token goes into an
 * httpOnly cookie, and keeping them in one object here is what stops the controller from
 * accidentally putting the refresh token in the body (#125).
 *
 * @param accessToken      the new short-lived access token, for the response body
 * @param refreshToken     the new rotated refresh token, for the cookie only
 * @param expiresInSeconds how long the access token lasts, so the client can refresh ahead of time
 */
record RefreshResult(String accessToken, String refreshToken, long expiresInSeconds) {
}
