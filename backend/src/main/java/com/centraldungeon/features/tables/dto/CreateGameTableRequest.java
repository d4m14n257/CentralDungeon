package com.centraldungeon.features.tables.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Creating a table, from the master's wizard or from an admin creating an unassigned one.
 *
 * <p>Everything past the name is optional because a table is filled in over several steps and
 * publishes only when its master submits it for review. <b>Format is validated here; the business
 * rules are not</b> - which statuses allow what, whether the agenda clashes with another table, whether a
 * catalog value may still be linked all live in the service.
 *
 * @param name          the table's title. The one field a draft cannot do without
 * @param description   what the table is about, as rich text. Sanitized before it is stored
 * @param permitted     the house rules - what is allowed at this table. Rich text, same sanitization
 * @param requirements  what is asked of a player to be accepted, as rich text. Same sanitization
 * @param tableTypeId   how the table is run, from /api/v1/table-types
 * @param systemIds     the game systems the table uses, as the master picked them. Null
 *                      and empty mean the same thing: nothing chosen. At most 64, like the tags and
 *                      the platforms - each value costs a lookup when the links are written
 * @param tagIds        the tags the table is labelled with. At most 64
 * @param platformIds   where the table is played. At most 64
 * @param startDate     the day the table starts running. A plain date: no session takes
 *                      its hour from here, the weekly agenda does
 * @param totalSessions how many sessions are planned. Positive when present
 * @param maxPlayers    the player cap. Positive when present; absent means no cap
 * @param schedule      the weekly agenda, in UTC. Null and empty mean no agenda yet, and a
 *                      table without one never clashes with anything
 * @param sections      the four optional sections of rich text. Null means none was written, the same
 *                      as four nulls; each one is sanitized before it is stored
 */
public record CreateGameTableRequest(
        @NotBlank @Size(max = 128) String name,
        @Size(max = TableSections.MAX_TEXT_LENGTH) @Nullable String description,
        @Size(max = TableSections.MAX_TEXT_LENGTH) @Nullable String permitted,
        @Size(max = TableSections.MAX_TEXT_LENGTH) @Nullable String requirements,
        @Nullable String tableTypeId,
        @Size(max = 64) @Nullable List<String> systemIds,
        @Size(max = 64) @Nullable List<String> tagIds,
        @Size(max = 64) @Nullable List<String> platformIds,
        @Nullable LocalDate startDate,
        @Positive @Nullable Integer totalSessions,
        @Positive @Nullable Integer maxPlayers,
        @Valid @Nullable List<TableScheduleEntry> schedule,
        @Valid @Nullable TableSections sections) {
}
