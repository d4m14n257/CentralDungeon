package com.centraldungeon.registrations;

/**
 * How many people are accepted at one table.
 *
 * <p>An internal projection: the result of a grouped query that <b>never crosses HTTP</b>
 * (arquitectura.md §2.3) - the admin listing folds it into {@code AdminTableSummaryResponse}. Same
 * shape and same reason as {@link PendingCandidateCount}, {@code TaskSubmissionCount} and
 * {@code CatalogUsageCount}: one query for the whole page instead of one {@code count} per row, which
 * is the N+1 the F3.3 contract §3.4 came to close.
 *
 * @param gameTableId the table the count belongs to
 * @param players     how many registrations are in {@code Player}. A table nobody plays at is absent
 *                    from the result rather than reported as zero, so the caller defaults it
 */
public record TablePlayerCount(String gameTableId, long players) {
}
