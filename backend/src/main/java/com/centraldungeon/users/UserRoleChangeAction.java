package com.centraldungeon.users;

/**
 * What a row of {@code user_role_changes} records. There are only two things that can happen to a
 * grant, and {@link UserRoleStatus} already spells them from the grant's side - this enum spells
 * them from the history's side, where a row is an event and not a state.
 */
public enum UserRoleChangeAction {

    /** The role was given. The grant's status is {@code Allowed} from this moment on. */
    Granted,

    /** The role was taken away. The row of {@code users_roles} stays, marked {@code Deleted} (#25). */
    Revoked
}
