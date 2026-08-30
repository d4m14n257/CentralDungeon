package com.centraldungeon.common.security;

import java.util.Set;

/** The actor of the current request, resolved from the JWT - never from a path variable (decisiones.md #121). */
public record CurrentUser(String userId, Set<String> roles) {

    public boolean hasRole(String roleName) {
        return roles.contains(roleName);
    }
}
