package com.centraldungeon.features.tables;

/**
 * Whether somebody was at a session.
 *
 * <p><b>The four values do not collapse into two</b>. Mixing a warned absence with a no-show
 * would throw away the distinction a master actually reads, the same way attendance is kept out of
 * karma: a number that already decided for the reader explains nothing. So the summary carries three
 * counts and never a ratio.
 *
 * <p>{@link #Unknown} is not an absence. It means nobody recorded anything, and it stays out of the
 * denominator of every historical count - a table of twelve sessions that started yesterday has
 * eleven unrecorded ones, and counting those would make every player look like a chronic absentee
 * precisely while a master is judging them.
 */
public enum AttendanceStatus {

    /** They were there. */
    Present,

    /** They were not there and did not say so beforehand. */
    Absent,

    /** They were not there and warned. A different fact from {@link #Absent}, and never merged with it. */
    Excused,

    /** Nothing was recorded. Outside the denominator of every count. */
    Unknown
}
