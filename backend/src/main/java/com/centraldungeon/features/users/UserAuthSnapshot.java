package com.centraldungeon.features.users;

import java.util.Set;

/** What JwtAuthenticationFilter needs on every request - cached, never the whole entity. */
public record UserAuthSnapshot(String userId, UserStatus status, Set<String> roles) {
}
