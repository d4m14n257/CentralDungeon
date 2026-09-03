package com.centraldungeon.tables.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.jspecify.annotations.Nullable;

/**
 * Creating a table, from the master's wizard or from an admin creating an unassigned one (#72).
 *
 * <p>Everything past the name is optional because a table is filled in over several steps and
 * publishes only when its master submits it for review. Format is validated here; the business
 * rules - which statuses allow what, schedule clashes, catalog depth - live in the service
 * (arquitectura.md 2.3).
 *
 * @param name          the table's title. The one field a draft cannot do without
 * @param description   what the table is about, as rich text. Sanitized before it is stored (#62)
 * @param requirements  what is asked of a player to be accepted, as rich text. Same sanitization
 * @param tableTypeId   how the table is run, from /api/v1/table-types
 * @param startDate     when the first session happens, in UTC (#22)
 * @param duration      how long <b>one</b> session lasts, not the campaign
 * @param totalSessions how many sessions are planned (#26). Positive when present
 * @param maxPlayers    the player cap (#24). Positive when present; absent means no cap
 */
public record CreateGameTableRequest(
        @NotBlank @Size(max = 128) String name,
        @Nullable String description,
        @Nullable String requirements,
        @Nullable String tableTypeId,
        @Nullable LocalDateTime startDate,
        @Nullable LocalTime duration,
        @Positive @Nullable Integer totalSessions,
        @Positive @Nullable Integer maxPlayers) {
}
