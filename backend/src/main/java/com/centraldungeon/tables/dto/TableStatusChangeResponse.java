package com.centraldungeon.tables.dto;

import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * One step of a table's lifecycle, as the status tab lists it.
 *
 * @param id            the entry's identifier
 * @param fromStatus    where the table was, as a string
 * @param toStatus      where it went, as a string
 * @param changedByName who made the change, already resolved to a display name - the screen shows a
 *                      person, not an id
 * @param justification why, or null for a transition that did not need a reason
 * @param createdAt     when it happened, in UTC
 */
public record TableStatusChangeResponse(
        String id, String fromStatus, String toStatus, String changedByName, @Nullable String justification, LocalDateTime createdAt) {
}
