package com.centraldungeon.users.dto;

import org.jspecify.annotations.Nullable;

/**
 * A person as a search result or a reference: enough to recognize and pick them, nothing else.
 * No discordId, no status, no karma - see arquitectura.md 2.3.
 */
public record UserSummaryResponse(String id, String discordUsername, @Nullable String name) {
}
