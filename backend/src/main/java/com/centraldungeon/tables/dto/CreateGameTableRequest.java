package com.centraldungeon.tables.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Creating a table, from the master's wizard or from an admin creating an unassigned one (#72).
 *
 * <p>Everything past the name is optional because a table is filled in over several steps and
 * publishes only when its master submits it for review. <b>Format is validated here; the business
 * rules are not</b> - which statuses allow what, whether the agenda clashes with another table
 * (#178), whether a catalog value may still be linked (#81) all live in the service
 * (arquitectura.md 2.3).
 *
 * @param name          the table's title. The one field a draft cannot do without
 * @param description   what the table is about, as rich text. Sanitized before it is stored (#62)
 * @param permitted     the house rules - what is allowed at this table. Rich text, same sanitization
 * @param requirements  what is asked of a player to be accepted, as rich text. Same sanitization
 * @param tableTypeId   how the table is run, from /api/v1/table-types
 * @param systemIds     the game systems the table uses, as the master picked them (#56, #58). Null
 *                      and empty mean the same thing: nothing chosen
 * @param tagIds        the tags the table is labelled with
 * @param platformIds   where the table is played
 * @param startDate     when the first session happens, in UTC (#22)
 * @param duration      how long <b>one</b> session lasts, not the campaign. It is what turns each
 *                      slot of the agenda into the interval the clash check compares (#178)
 * @param totalSessions how many sessions are planned (#26). Positive when present
 * @param maxPlayers    the player cap (#24). Positive when present; absent means no cap
 * @param schedule      the weekly agenda, in UTC (#22). Null and empty mean no agenda yet, and a
 *                      table without one never clashes with anything
 */
public record CreateGameTableRequest(
        @NotBlank @Size(max = 128) String name,
        @Nullable String description,
        @Nullable String permitted,
        @Nullable String requirements,
        @Nullable String tableTypeId,
        @Nullable List<String> systemIds,
        @Nullable List<String> tagIds,
        @Nullable List<String> platformIds,
        @Nullable LocalDateTime startDate,
        @Nullable LocalTime duration,
        @Positive @Nullable Integer totalSessions,
        @Positive @Nullable Integer maxPlayers,
        @Valid @Nullable List<TableScheduleEntry> schedule) {
}
