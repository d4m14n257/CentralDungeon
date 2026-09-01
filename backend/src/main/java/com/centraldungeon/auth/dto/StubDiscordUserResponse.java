package com.centraldungeon.auth.dto;

/**
 * Discord's /users/@me payload, trimmed to what we actually read (id and username, both strings).
 * Only produced by the "test" profile stub (TestDiscordController).
 */
public record StubDiscordUserResponse(String id, String username) {
}
