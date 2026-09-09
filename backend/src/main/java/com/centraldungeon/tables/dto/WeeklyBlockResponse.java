package com.centraldungeon.tables.dto;

/**
 * One occupied stretch of the week, as it travels (#227).
 *
 * <p>Minutes from Monday 00:00 <b>UTC</b>, not a weekday and a time: the reader's week does not
 * start where UTC's does, and a Tuesday-night table in America is Wednesday in UTC (#22). Sending
 * the pair would make the frontend undo a conversion the backend had already half-made. A single
 * offset is unambiguous, and turning it into the reader's own day and hour is one subtraction.
 *
 * <p>It can run past the end of the week: a Sunday-night slot continues into Monday, the same way
 * {@code WeeklyInterval} wraps. Whoever draws it has to wrap too.
 *
 * @param startMinute     when it starts, in minutes from Monday 00:00 UTC
 * @param durationMinutes how long it lasts. Always positive: a slot with no length occupies nothing
 */
public record WeeklyBlockResponse(int startMinute, int durationMinutes) {
}
