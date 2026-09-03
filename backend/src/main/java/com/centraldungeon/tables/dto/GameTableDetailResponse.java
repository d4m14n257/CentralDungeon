package com.centraldungeon.tables.dto;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A table as its own screen shows it: everything the summary has, plus the long text, the agenda
 * fields and the people who run it.
 *
 * <p>Separate from {@link GameTableSummaryResponse} because returning this for fifty rows of the
 * explorer would load fifty times the relations nobody is going to look at (arquitectura.md 2.3).
 *
 * @param id            the table's identifier
 * @param name          the table's title
 * @param description   what the table is about, as sanitized rich text
 * @param requirements  what is asked of a player to be accepted, as sanitized rich text
 * @param tableTypeName how the table is run, resolved to its label. Null when none was chosen
 * @param status        where the table is in its lifecycle, as a string (arquitectura.md 2.3)
 * @param maxPlayers    the player cap (#24), or null for no cap
 * @param playerCount   how many people are accepted right now. Derived, never stored
 * @param startDate     when the first session happens, in UTC. The frontend converts (#22, #111)
 * @param duration      how long one session lasts
 * @param totalSessions how many sessions are planned (#26)
 * @param masters       everyone who runs the table, Primary first. Never null; an Unassigned table
 *                      has an empty list
 * @param createdAt     when the table was created, in UTC
 */
public record GameTableDetailResponse(
        String id,
        String name,
        @Nullable String description,
        @Nullable String requirements,
        @Nullable String tableTypeName,
        String status,
        @Nullable Integer maxPlayers,
        int playerCount,
        @Nullable LocalDateTime startDate,
        @Nullable LocalTime duration,
        @Nullable Integer totalSessions,
        List<MasterSummaryResponse> masters,
        LocalDateTime createdAt) {
}
