package com.centraldungeon.tables.dto;

import jakarta.validation.constraints.NotNull;

/**
 * The body of the call that turns the search for players on or off.
 *
 * @param seeking whether the table wants applications. Required: a missing value is a malformed
 *                request, not an implicit {@code false}
 */
public record SetSeekingPlayersRequest(@NotNull Boolean seeking) {
}
