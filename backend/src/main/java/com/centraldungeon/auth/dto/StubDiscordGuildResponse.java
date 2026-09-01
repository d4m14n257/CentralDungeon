package com.centraldungeon.auth.dto;

/**
 * One entry of Discord's /users/@me/guilds list, trimmed to what the membership check reads.
 * Only produced by the "test" profile stub (TestDiscordController).
 */
public record StubDiscordGuildResponse(String id, String name) {
}
