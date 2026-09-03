package com.centraldungeon.catalogs.dto;

import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * One catalog value as /admin/catalogs sees it. Separate from {@code CatalogValueResponse} and not
 * an extension of it: this one carries what an admin needs to decide - which group it is in and how
 * much it is being used - and none of that belongs in the response a player receives.
 *
 * @param id            the value's identifier, the one the six admin operations address it by
 * @param name          the value as it is written and displayed
 * @param status        the value's lifecycle state as a string ({@code Created}, {@code Accepted},
 *                      {@code Rejected}, {@code Disabled}); it is what the review screen filters and
 *                      sorts by
 * @param canonicalId   the group this value belongs to, or null when this row <em>is</em> its
 *                      group's canonical entry (#59)
 * @param canonicalName the canonical entry's name, so the table can show "DANDD -> D&amp;D 5e"
 *                      without a second request per row. Null exactly when {@code canonicalId} is
 * @param uses          how many tables currently link to <em>this</em> value, not to its group -
 *                      disabling a value is decided on what it holds by itself (#81)
 * @param createdAt     when the value was proposed, in UTC. It is what orders a review queue: the
 *                      oldest proposal is the one that has been waiting longest
 */
public record AdminCatalogValueResponse(
        String id,
        String name,
        String status,
        @Nullable String canonicalId,
        @Nullable String canonicalName,
        long uses,
        LocalDateTime createdAt) {
}
