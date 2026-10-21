package com.centraldungeon.tables.dto;

/**
 * One of a table's masters, as it appears nested inside a table response.
 *
 * <p>A summary and not the full user: the public view of a table has no business carrying a
 * person's Discord id or account status (arquitectura §2.3). No karma until F5 computes one (ADR-10 §5).
 *
 * @param userId     the person's identifier, enough to open their profile
 * @param name       their display name - the name they set, or their Discord username when they
 *                   have not set one
 * @param masterType Primary or Secondary, as a string. The interface renders these as "master" and
 *                   "co-master" and never shows these words (ADR-06 §3)
 */
public record MasterSummaryResponse(String userId, String name, String masterType) {
}
