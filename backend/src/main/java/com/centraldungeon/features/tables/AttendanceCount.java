package com.centraldungeon.features.tables;

/**
 * How many times one attendance value was recorded for somebody. Internal projection of
 * {@link SessionAttendanceRepository}, not a DTO: it never crosses HTTP -
 * {@code AttendanceSummaryResponse} carries the numbers.
 *
 * <p>It exists so the historical attendance count is one grouped query and not one per value. The real
 * risk is the N+1, not the volume.
 *
 * @param attendance the value counted. {@code Unknown} never appears: the query leaves it out of the
 *                   denominator, which is the whole point of the count
 * @param total      how many sessions were recorded with it. A value never recorded is absent from
 *                   the result, so a missing entry means zero
 */
public record AttendanceCount(AttendanceStatus attendance, long total) {
}
