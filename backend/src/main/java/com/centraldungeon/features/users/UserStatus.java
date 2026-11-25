package com.centraldungeon.features.users;

/**
 * Whether an account may be used at all. Read from the database on every request, never from the
 * token's claims: a JWT is a photograph of the past, and someone blocked has to be blocked now, not
 * when their token expires.
 */
public enum UserStatus {

    /** Normal. The only status that can log in and be offered as a master or a player. */
    Allowed,

    /** Banned by an admin. Login is refused and every listing skips them. */
    Blocked
}
