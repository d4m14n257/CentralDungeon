package com.centraldungeon.tables;

/**
 * Whether a slot of a table's weekly agenda is still part of it.
 *
 * <p>A slot is never physically dropped: the primary key of {@code table_schedules} is
 * {@code (table, weekday, hourtime)}, so a master who removes Tuesday 20:00 and puts it back a
 * minute later would collide with the row they just deleted. Marking the row instead makes the
 * round trip work and keeps the record of what the agenda used to be - which is what the sessions
 * already materialized from it were built on (ADR-08 §3).
 */
public enum TableScheduleStatus {

    /** Live: the table plays at this slot, and it counts for the clash rules of ADR-08 §2. */
    Created,

    /** Removed from the agenda. Skipped by every read, and revived rather than re-inserted. */
    Deleted
}
