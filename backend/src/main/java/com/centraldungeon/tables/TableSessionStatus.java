package com.centraldungeon.tables;

/**
 * What happened to one materialized session of a table (ADR-08 §3).
 *
 * <p>The three values are the ones the baseline schema declares for {@code table_sessions.status},
 * and they are a record of fact rather than a workflow: a session is planned, it was played, or it
 * did not happen. Nothing here is derived from the clock - a session whose date has passed is still
 * {@code Scheduled} until a master says otherwise (ADR-08 §4), because only they know whether people
 * actually showed up.
 */
public enum TableSessionStatus {

    /** Planned and still ahead. The only status from which a session can be corrected, held or cancelled. */
    Scheduled,

    /** It was played. A master says so explicitly; registering attendance does not imply it (ADR-08 §4). */
    Held,

    /**
     * It did not happen. The row stays with its {@code sequence_number} as the record that it was
     * planned, and the table gets a replacement session at the end so it still plays the number it
     * promised (ADR-08 §3).
     */
    Cancelled
}
