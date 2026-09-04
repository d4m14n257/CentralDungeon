package com.centraldungeon.tasks;

/**
 * How many answers one task has, and from how many different people.
 *
 * <p>An internal projection: it is the result of a grouped query and <b>never crosses HTTP</b>
 * (arquitectura.md 2.3) - the master's row shows {@code submittedCount} out of the roster, and this
 * is where the first half of that comes from. Same shape and same reason as {@code AttendanceCount}
 * and {@code FileUsageCount}.
 *
 * <p>The two numbers are not the same and both are needed: answers accumulate (#76), so one player
 * handing in three versions is three rows and one person. What "has this been handed in" means is
 * the second number.
 *
 * @param taskId          the task being counted
 * @param submissions     how many answer rows it has
 * @param distinctPeople  how many different people handed one in
 */
public record TaskSubmissionCount(String taskId, long submissions, long distinctPeople) {
}
