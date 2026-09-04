package com.centraldungeon.tables;

import java.time.LocalDateTime;

/**
 * How many sessions of one table were due and never closed, and the date of the oldest.
 *
 * <p>An internal projection: the result of a grouped query that <b>never crosses HTTP</b>
 * (arquitectura.md 2.3) - the master dashboard turns it into a work item. Same shape and same
 * reason as {@code AttendanceCount}: one query for every table somebody runs, instead of one count
 * per table.
 *
 * <p>"Unrecorded" means still {@code Scheduled} with its date in the past. Marking a session held
 * is an explicit act (#195), so a date going by does not close anything on its own - which is
 * precisely what makes this something waiting for the master.
 *
 * @param gameTableId the table the count belongs to
 * @param unrecorded  how many of its sessions are overdue and still open
 * @param oldest      when the earliest of them was scheduled, in UTC (#22)
 */
public record UnrecordedSessionCount(String gameTableId, long unrecorded, LocalDateTime oldest) {
}
