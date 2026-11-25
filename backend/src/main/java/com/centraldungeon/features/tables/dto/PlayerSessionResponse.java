package com.centraldungeon.features.tables.dto;

import com.centraldungeon.features.tables.AttendanceStatus;
import com.centraldungeon.features.tables.TableSessionStatus;
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
 * @param scheduledAt    when it happens, <b>in UTC</b>
 * @param status         planned, played or called off
 * @param myAttendance   what was recorded for the actor of the token and nobody else, or
 *                       {@code Unknown} when nothing was
 */
public record PlayerSessionResponse(
        String id, int sequenceNumber, LocalDateTime scheduledAt, TableSessionStatus status, AttendanceStatus myAttendance) {
}
