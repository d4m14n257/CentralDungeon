package com.centraldungeon.tables.dto;

import com.centraldungeon.tables.AttendanceStatus;
import com.centraldungeon.tables.TableSessionStatus;
import java.time.LocalDateTime;

/**
 * One session as the player sitting at the table sees it, on {@code /my/tables/:id}.
 *
 * <p>Not a projection of {@link TableSessionResponse} but a record of its own, because it answers a
 * different question: it carries <b>my</b> attendance and not the roster, and no notes at all - what
 * a master writes about a session is theirs.
 *
 * @param id             the session
 * @param sequenceNumber which session of the run this is, from 1
 * @param scheduledAt    when it happens, <b>in UTC</b> (#22)
 * @param status         planned, played or called off
 * @param myAttendance   what was recorded for the actor of the token and nobody else (#121), or
 *                       {@code Unknown} when nothing was
 */
public record PlayerSessionResponse(
        String id, int sequenceNumber, LocalDateTime scheduledAt, TableSessionStatus status, AttendanceStatus myAttendance) {
}
