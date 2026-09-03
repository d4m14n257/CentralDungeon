package com.centraldungeon.tables.dto;

import com.centraldungeon.tables.TableSessionStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One session as the people running the table see it: the master's view, with the notes and the
 * whole roster.
 *
 * <p>The player's view is {@link PlayerSessionResponse} and is deliberately a different record - it
 * carries neither the notes nor anybody else's attendance.
 *
 * @param id             the session
 * @param sequenceNumber which session of the run this is, from 1. A cancelled session keeps its
 *                       number and the replacement of #194 takes the next one
 * @param scheduledAt    when it happens, <b>in UTC</b> (#22). The frontend converts it once, at the
 *                       edge, with {@code lib/date.ts}
 * @param status         planned, played or called off
 * @param notes          what the master wrote about it, or null. Never sent to a player
 * @param attendance     the roster: one line per active player of the table, whether or not
 *                       anything was recorded for them (#36)
 */
public record TableSessionResponse(
        String id,
        int sequenceNumber,
        LocalDateTime scheduledAt,
        TableSessionStatus status,
        @Nullable String notes,
        List<SessionAttendanceEntry> attendance) {
}
