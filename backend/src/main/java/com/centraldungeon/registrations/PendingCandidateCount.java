package com.centraldungeon.registrations;

import java.time.LocalDateTime;

/**
 * How many people are waiting for an answer on one table, and since when.
 *
 * <p>An internal projection: the result of a grouped query that <b>never crosses HTTP</b>
 * (arquitectura.md 2.3) - the master dashboard turns it into a work item. Same shape and same
 * reason as {@code TaskSubmissionCount} and {@code CatalogUsageCount}: one query for every table
 * somebody runs, instead of one count per table.
 *
 * @param gameTableId the table the count belongs to
 * @param pending     how many applications are still in {@code Candidate}. A table nobody applied
 *                    to is absent from the result rather than reported as zero
 * @param oldest      when the longest-waiting of them applied. It is what orders the dashboard:
 *                    urgency is time waited, not volume (#136)
 */
public record PendingCandidateCount(String gameTableId, long pending, LocalDateTime oldest) {
}
