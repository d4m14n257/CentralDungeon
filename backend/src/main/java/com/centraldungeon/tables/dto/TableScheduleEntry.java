package com.centraldungeon.tables.dto;

import com.centraldungeon.tables.Weekday;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

/**
 * One slot of a table's weekly agenda, both on the way in and on the way out. The same shape is
 * used for reading and for writing because there is nothing to it beyond the two fields, and giving
 * the write side a twin would mean keeping two records in step for no gain.
 *
 * <p><b>UTC on both ends</b> (#22). The frontend converts to and from the reader's zone with
 * {@code lib/date.ts}; the API never sees a local time, and nothing here carries a zone to be
 * mis-read.
 *
 * @param weekday  the day of the week, in UTC. A Tuesday-night table in America is a Wednesday here
 * @param hourtime the time of day the session starts, in UTC. Seconds are dropped when it is saved:
 *                 the agenda is written in minutes and the column is part of the primary key
 */
public record TableScheduleEntry(@NotNull Weekday weekday, @NotNull LocalTime hourtime) {
}
