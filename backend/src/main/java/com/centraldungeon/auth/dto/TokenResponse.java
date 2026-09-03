package com.centraldungeon.auth.dto;

/**
 * What the client receives after a login or a refresh.
 *
 * <p>The refresh token is deliberately <b>not</b> here: it travels in an httpOnly SameSite=Strict
 * cookie the frontend never reads (#125). The access token stays in memory, never in
 * {@code localStorage}, because the rich text editor makes XSS the most direct surface in the
 * system (#62).
 *
 * @param accessToken      the token to send as {@code Authorization: Bearer} on every call
 * @param expiresInSeconds how long it lasts, so the client can refresh before it dies rather than
 *                         after a failed call
 */
public record TokenResponse(String accessToken, long expiresInSeconds) {
}
