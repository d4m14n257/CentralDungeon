package com.centraldungeon.users;

/**
 * Whether a row of {@code users_roles} still grants its role. Roles are revoked by marking, never by
 * deleting: the record of who once held what is worth keeping (#25).
 */
public enum UserRoleStatus {

    /** Live: the user holds this role right now. */
    Allowed,

    /** Revoked. The row stays; the role no longer counts for any authorization check. */
    Deleted
}
