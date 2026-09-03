package com.centraldungeon.common.security;

import java.util.Set;

/**
 * The actor of the current request, resolved from the JWT - never from a path variable
 * (decisiones.md #121).
 *
 * <p>The roles in here were read from the database by {@code JwtAuthenticationFilter} during this
 * request, not lifted from the token's claims: the JWT asserts identity, not authorization (#122).
 *
 * @param userId the authenticated user's id. This is the id that goes into a {@code WHERE}, and the
 *               only one a service may trust
 * @param roles  the role names the user holds right now, as read from the database this request.
 *               Never null; a user with no roles has an empty set
 */
public record CurrentUser(String userId, Set<String> roles) {

    /**
     * Tells whether the actor holds a role.
     *
     * <p>Careful: a role is not membership. Holding {@code Master} means "can create tables of my
     * own", not "runs this table" - that one is decided by a row in {@code masters} (#135).
     *
     * @param roleName the role to look for, as it is named in {@code roles}
     * @return true when the actor currently holds it
     */
    public boolean hasRole(String roleName) {
        return roles.contains(roleName);
    }
}
