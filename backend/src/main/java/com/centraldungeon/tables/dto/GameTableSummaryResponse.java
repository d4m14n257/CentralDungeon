package com.centraldungeon.tables.dto;

import java.time.LocalTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A table as a card in a listing shows it. The explorer's shape.
 *
 * @param id               the table's identifier
 * @param name             the table's title
 * @param status           where the table is in its lifecycle, as a string
 * @param tableTypeName    how the table is run, resolved to its label. Null when none was chosen
 * @param maxPlayers       the player cap (#24), or null for no cap
 * @param playerCount      how many people are accepted right now, so the card can show "3/5"
 * @param duration         how long one session lasts, so the card can show when a slot ends and not
 *                         only when it starts
 * @param schedule         the weekly agenda, in UTC (#22). The card converts it to the reader's own
 *                         time with {@code lib/date.ts}; the API never sends a local time
 * @param scheduleConflict whether this table's agenda overlaps something the <b>actor of the
 *                         token</b> is already committed to (#178). Derived per request and never
 *                         for an id taken from the URL (#121), which is what makes it impossible to
 *                         ask on somebody else's behalf. False on listings where the question does
 *                         not arise - a table you already play at cannot clash with itself
 * @param primaryMaster    who runs the table. Non-null here because every table this endpoint lists
 *                         already has a Primary - the Unassigned case has its own shape,
 *                         {@link AdminTableSummaryResponse}
 */
public record GameTableSummaryResponse(
        String id,
        String name,
        String status,
        @Nullable String tableTypeName,
        @Nullable Integer maxPlayers,
        int playerCount,
        @Nullable LocalTime duration,
        List<TableScheduleEntry> schedule,
        boolean scheduleConflict,
        MasterSummaryResponse primaryMaster) {
}
