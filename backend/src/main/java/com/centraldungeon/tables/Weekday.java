package com.centraldungeon.tables;

import java.time.DayOfWeek;

/**
 * The day of the week one slot of a table's agenda falls on. Column {@code table_schedules.weekday}.
 *
 * <p>Its own enum and not {@link java.time.DayOfWeek}: the column stores these seven names as text
 * and has since the inherited schema, so persisting {@code DayOfWeek} would either change the stored
 * values or need a converter that maps them one to one anyway. The mapping to {@code DayOfWeek}
 * lives in {@link #toDayOfWeek()}, for the arithmetic that needs it.
 *
 * <p><b>The day is in UTC</b>, like the hour it goes with (#22). A table the community plays on
 * Tuesday night in America is a Wednesday slot here, and that is the single most likely source of an
 * off-by-one-day bug in the whole feature - which is why the conversion happens once, in the
 * frontend, and never again.
 */
public enum Weekday {

    /** Monday, day 1 of the ISO week - the week's origin for {@link WeeklyInterval}. */
    Monday(DayOfWeek.MONDAY),

    /** Tuesday. */
    Tuesday(DayOfWeek.TUESDAY),

    /** Wednesday. */
    Wednesday(DayOfWeek.WEDNESDAY),

    /** Thursday. */
    Thursday(DayOfWeek.THURSDAY),

    /** Friday. */
    Friday(DayOfWeek.FRIDAY),

    /** Saturday. */
    Saturday(DayOfWeek.SATURDAY),

    /** Sunday, day 7 of the ISO week. */
    Sunday(DayOfWeek.SUNDAY);

    /** The equivalent {@code java.time} constant, kept so the mapping is declared once. */
    private final DayOfWeek dayOfWeek;

    /**
     * @param dayOfWeek the {@code java.time} constant this value stands for
     */
    Weekday(DayOfWeek dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    /**
     * Returns the equivalent {@code java.time} constant.
     *
     * @return the day as {@link DayOfWeek}, for the week arithmetic of {@link WeeklyInterval} and for
     *         materializing sessions later (#26)
     */
    public DayOfWeek toDayOfWeek() {
        return dayOfWeek;
    }

    /**
     * Where in the week this day starts, counting from Monday.
     *
     * @return 0 for Monday through 6 for Sunday - the offset {@link WeeklyInterval} multiplies by a
     *         day's worth of minutes
     */
    public int ordinalFromMonday() {
        return dayOfWeek.getValue() - 1;
    }

    /**
     * Resolves the weekday an instant falls on.
     *
     * @param dayOfWeek the {@code java.time} constant to translate
     * @return the matching value of this enum
     */
    public static Weekday from(DayOfWeek dayOfWeek) {
        return values()[dayOfWeek.getValue() - 1];
    }
}
