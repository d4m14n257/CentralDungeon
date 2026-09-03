package com.centraldungeon.users;

/**
 * Whether an account may be used at all. Read from the database on every request, never from the
 * token's claims: a JWT is a photograph of the past, and someone blocked has to be blocked now, not
 * when their token expires (#122).
 */
public enum UserStatus {

    /** Normal. The only status that can log in and be offered as a master or a player. */
    Allowed,

    /** Banned by an admin (#84, #86). Login is refused and every listing skips them. */
    Blocked,

    /** Soft delete (#25). The rows stay so history keeps making sense. */
    Deleted
}
