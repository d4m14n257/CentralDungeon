package com.centraldungeon.common.security;

/**
 * Which of the two tokens this application issues a JWT is (decisiones.md #125). It travels as a
 * claim so an access token can never be replayed at the refresh endpoint, or the other way round.
 *
 * <p>Discord's own access token is not in here on purpose: it is discarded when the login callback
 * ends and never becomes a credential of this system.
 */
public enum TokenType {

    /** Short-lived, sent as {@code Authorization: Bearer} on every call. Kept in memory only. */
    ACCESS,

    /** Long-lived and rotating, carried in an httpOnly SameSite=Strict cookie. */
    REFRESH
}
