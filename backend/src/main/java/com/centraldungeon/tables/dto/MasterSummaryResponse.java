package com.centraldungeon.tables.dto;

/**
 * One of a table's masters, as it appears nested inside a table response.
 *
 * <p>A summary and not the full user: the public view of a table has no business carrying a
 * person's Discord id or account status (arquitectura.md 2.3).
 *
 * @param userId     the person's identifier, enough to open their profile
 * @param name       their display name - the name they set, or their Discord username when they
 *                   have not set one
 * @param karma      their karma at the time of the read (#97)
 * @param masterType Primary or Secondary, as a string. The interface renders these as "master" and
 *                   "co-master" and never shows these words (#166)
 */
public record MasterSummaryResponse(String userId, String name, int karma, String masterType) {
}
