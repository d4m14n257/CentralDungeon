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
 * Editing a table a master already created - the other half of the wizard, and what a table sent
 * back with {@code ChangesRequested} is corrected with.
 *
 * <p><b>A full replacement, not a patch.</b> The wizard shows the whole table and sends the whole
 * table back, so an absent field means "empty this", not "leave it alone" - which is the only
 * reading under which clearing the agenda or removing the last tag is expressible at all. Its own
 * record and not a reuse of {@link CreateGameTableRequest} because the two differ in what they are
 * allowed to say: creating decides nothing about who runs the table, editing cannot change it
 * either, and keeping them apart is what stops a field added to one from silently appearing in the
 * other (arquitectura.md 2.3).
 *
 * @param name          the table's title
 * @param description   what the table is about, as rich text. Re-sanitized on every save (#62)
 * @param permitted     the house rules. Rich text, same sanitization
 * @param requirements  what is asked of a player to be accepted, as rich text. Same sanitization
 * @param tableTypeId   how the table is run, or null to leave it unclassified
 * @param systemIds     the game systems the table ends up using. Empty clears them
 * @param tagIds        the tags the table ends up labelled with. Empty clears them
 * @param platformIds   where the table ends up being played. Empty clears them
 * @param startDate     when the first session happens, in UTC (#22)
 * @param duration      how long one session lasts. Changing it re-measures the whole agenda against
 *                      the master's other commitments, because it is what gives a slot its length (#178)
 * @param totalSessions how many sessions are planned (#26)
 * @param maxPlayers    the player cap (#24), or null for no cap
 * @param schedule      the weekly agenda the table ends up with, in UTC. Empty clears it
 */
public record UpdateGameTableRequest(
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
