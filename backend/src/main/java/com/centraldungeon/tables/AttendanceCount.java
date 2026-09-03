package com.centraldungeon.tables;

/**
 * How many times one attendance value was recorded for somebody. Internal projection of
 * {@link SessionAttendanceRepository}, not a DTO: it never crosses HTTP -
 * {@code AttendanceSummaryResponse} carries the numbers.
 *
 * <p>It exists so the historical count of #137 is one grouped query and not one per value. The real
 * risk that decision names is the N+1, not the volume.
 *
 * @param attendance the value counted. {@code Unknown} never appears: the query leaves it out of the
 *                   denominator, which is the whole point of #137
 * @param total      how many sessions were recorded with it. A value never recorded is absent from
 *                   the result, so a missing entry means zero
 */
public record AttendanceCount(AttendanceStatus attendance, long total) {
}
