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
 * Editing a table a master already created - the other half of the wizard, and what a table sent
 * back with {@code ChangesRequested} is corrected with.
 *
 * <p><b>A full replacement, not a patch.</b> The wizard shows the whole table and sends the whole
 * table back, so an absent field means "empty this", not "leave it alone" - which is the only
 * reading under which clearing the agenda or removing the last tag is expressible at all. Its own
 * record and not a reuse of {@link CreateGameTableRequest} because the two differ in what they are
 * allowed to say: creating decides nothing about who runs the table, editing cannot change it
 * either, and keeping them apart is what stops a field added to one from silently appearing in the
 * other (arquitectura §2.3).
 *
 * @param name          the table's title
 * @param description   what the table is about, as rich text. Re-sanitized on every save (ADR-03 §8)
 * @param permitted     the house rules. Rich text, same sanitization
 * @param requirements  what is asked of a player to be accepted, as rich text. Same sanitization
 * @param tableTypeId   how the table is run, or null to leave it unclassified
 * @param systemIds     the game systems the table ends up using. Empty clears them. At most 64: each
 *                      value costs a lookup when the links are rewritten
 * @param tagIds        the tags the table ends up labelled with. Empty clears them. At most 64
 * @param platformIds   where the table ends up being played. Empty clears them. At most 64
 * @param startDate     the day the table starts running (ADR-07 §4). A plain date: no session takes
 *                      its hour from here, the weekly agenda does
 * @param totalSessions how many sessions are planned (ADR-08 §3)
 * @param maxPlayers    the player cap (ADR-07 §5), or null for no cap
 * @param schedule      the weekly agenda the table ends up with, in UTC. Empty clears it
 * @param sections      the four optional sections of rich text the table ends up with. Absent clears
 *                      all four, like any other field of a full replacement
 */
public record UpdateGameTableRequest(
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
