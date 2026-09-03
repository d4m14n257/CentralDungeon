package com.centraldungeon.tables;

import java.time.LocalTime;
import java.util.List;

/**
 * One occupied stretch of the week, in UTC minutes counted from Monday 00:00. The unit every
 * schedule clash of #178 is decided in.
 *
 * <p>Why an interval and not the {@code (weekday, hourtime)} pair the table stores: a three-hour
 * table at 20:00 and another at 22:00 do not share an {@code hourtime} and collide anyway, so
 * comparing the pair for equality let through precisely the common case (#178). What is compared is
 * {@code [start, start + duration)}.
 *
 * <p><b>Half-open on purpose.</b> A table that begins exactly when another one ends does not clash:
 * chaining two sessions is something people do, and refusing it would be a false positive nobody
 * could explain to themselves (#178).
 *
 * <p><b>It wraps.</b> The community plays at night in America, which is the small hours of the next
 * day in UTC (#22), so Tuesday 23:00 plus three hours is a stretch that ends Wednesday 02:00 - and a
 * Sunday-night slot runs past the end of the week into Monday. An interval that runs off the end
 * continues from Monday 00:00, which is why this is a record with its own overlap test rather than
 * two numbers compared inline.
 */
public record WeeklyInterval(int startMinute, int durationMinutes) {

    /** Minutes in a day. */
    private static final int MINUTES_PER_DAY = 24 * 60;

    /** Minutes in a week: the modulus every start is reduced to, and the length that covers it whole. */
    public static final int MINUTES_PER_WEEK = 7 * MINUTES_PER_DAY;

    /**
     * Builds an interval, normalizing the start into the week.
     *
     * @param startMinute     when it starts, in minutes from Monday 00:00 UTC. Reduced modulo the
     *                        week, so a caller may hand in an already-wrapped value
     * @param durationMinutes how long it lasts. Has to be positive: a slot with no length occupies
     *                        nothing and has no business being compared against anything
     * @throws IllegalArgumentException if the duration is not positive
     */
    public WeeklyInterval {
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("A weekly interval needs a positive duration, got " + durationMinutes);
        }
        startMinute = Math.floorMod(startMinute, MINUTES_PER_WEEK);
    }

    /**
     * Builds the interval one row of {@code table_schedules} occupies.
     *
     * @param weekday  the day the slot falls on, in UTC (#22)
     * @param hourtime the time of day it starts, in UTC
     * @param duration how long one session of the table lasts. It is a property of the table, not of
     *                 the slot: every slot of a table lasts the same
     * @return the stretch of the week that slot occupies
     */
    public static WeeklyInterval of(Weekday weekday, LocalTime hourtime, LocalTime duration) {
        int start = weekday.ordinalFromMonday() * MINUTES_PER_DAY + hourtime.getHour() * 60 + hourtime.getMinute();
        int minutes = duration.getHour() * 60 + duration.getMinute();
        return new WeeklyInterval(start, minutes);
    }

    /**
     * Whether this stretch and another one share any instant of the week.
     *
     * @param other the interval to compare against
     * @return true when they overlap. False when they merely touch - the intervals are half-open, so
     *         one ending exactly where the other begins is not a clash (#178)
     */
    public boolean overlaps(WeeklyInterval other) {
        for (int[] mine : segments()) {
            for (int[] theirs : other.segments()) {
                if (mine[0] < theirs[1] && theirs[0] < mine[1]) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The interval cut into pieces that do not run off the end of the week, so overlapping two of
     * them is a plain comparison of numbers instead of a case analysis over who wraps.
     *
     * @return one segment when the interval fits in the week from where it starts, two when it wraps
     *         past Sunday midnight, and the whole week when it is long enough to cover itself
     */
    private List<int[]> segments() {
        if (durationMinutes >= MINUTES_PER_WEEK) {
            return List.of(new int[] {0, MINUTES_PER_WEEK});
        }
        int end = startMinute + durationMinutes;
        if (end <= MINUTES_PER_WEEK) {
            return List.of(new int[] {startMinute, end});
        }
        return List.of(new int[] {startMinute, MINUTES_PER_WEEK}, new int[] {0, end - MINUTES_PER_WEEK});
    }
}
