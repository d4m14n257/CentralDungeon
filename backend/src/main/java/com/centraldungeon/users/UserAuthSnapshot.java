package com.centraldungeon.users;

import java.util.Set;

/** What JwtAuthenticationFilter needs on every request - cached, never the whole entity (arquitectura.md 2.6). */
public record UserAuthSnapshot(String userId, UserStatus status, Set<String> roles) {
}
