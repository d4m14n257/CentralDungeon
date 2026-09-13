package com.centraldungeon.tables.dto;

import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * A table as the player's history shows it (#133a) - a screen of its own, not
 * {@link GameTableSummaryResponse} with a different filter, because what the two cards carry is
 * genuinely different and not only which rows come back.
 *
 * <p>{@code maxPlayers}, {@code playerCount} and {@code scheduleConflict} are absent on purpose:
 * a seat count is a question about a table still recruiting, and a table that already closed
 * cannot clash with anything the actor is committed to. {@code closedAt} and {@code attendance}
 * are absent from {@code GameTableSummaryResponse} for the opposite reason - a live table has
 * not closed yet, and its own attendance is still being written session by session.
 *
 * @param id            the table's identifier
 * @param name          the table's title
 * @param status        where the table ended: {@code Finished} or {@code Canceled}
 * @param tableTypeName how the table was run, resolved to its label. Null when none was chosen
 * @param tableTypeCode the identifier the frontend translates by, when the application shipped
 *                       that type. Null for one a person named, whose label is read verbatim (#225)
 * @param closedAt      when the table closed (#180). Null only for a row that predates that
 *                       column ever being stamped
 * @param attendance    the actor's own historical attendance on this table, the aggregate of #137
 */
public record GameTableHistoryResponse(
        String id,
        String name,
        String status,
        @Nullable String tableTypeName,
        @Nullable String tableTypeCode,
        @Nullable LocalDateTime closedAt,
        AttendanceSummaryResponse attendance) {
}
