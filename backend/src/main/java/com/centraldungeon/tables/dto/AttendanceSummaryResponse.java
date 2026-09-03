package com.centraldungeon.tables.dto;

/**
 * Somebody's historical attendance on one table (#137).
 *
 * <p><b>Three numbers and never a ratio.</b> A percentage would have decided for the reader, and the
 * distinction it hides is the one that matters: a warned absence and a no-show are different facts,
 * the same way #98 keeps attendance out of karma so both stay explainable.
 *
 * <p>{@code registered} is the denominator, and it is the count of sessions <b>with something
 * recorded</b> - the {@code Unknown} ones are out. Counting them would make everybody read as a
 * chronic absentee on a table that has barely started.
 *
 * @param present    how many sessions they were at
 * @param excused    how many they missed having warned
 * @param absent     how many they missed without warning
 * @param registered the denominator: how many sessions have any record at all for them
 */
public record AttendanceSummaryResponse(int present, int excused, int absent, int registered) {
}
