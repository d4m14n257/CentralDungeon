package com.centraldungeon.approvals.dto;

import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * One request in full: what the detail shows, and what all three mutating endpoints answer with so
 * the screen does not have to re-fetch.
 *
 * <p>Unlike the summary it publishes {@code entityType} and {@code entityId}. They are the
 * polymorphic reference of #78, and the detail is the one place worth being able to read it from:
 * when F3.4 adds requests about a table or an application, "what is this about" stops being obvious
 * from the requester's name.
 *
 * @param id              the request
 * @param type            which request it is, as a string like every other enum on the wire
 * @param status          {@code Pending}, {@code Approved} or {@code Rejected}
 * @param entityType      what the request is about: {@code user} for all three types of F3.2
 * @param entityId        the id of that thing. Not a foreign key - the service is what guarantees it
 *                        resolves (#78, #126)
 * @param requestedByName the requester's display name, already resolved
 * @param justification   why they asked
 * @param claimedByName   the admin who reserved it (#100). Always null in F3.2; the queue is F3.3
 * @param resolvedByName  the admin who answered, or null while the request is Pending
 * @param resolutionNote  why it was approved or rejected, or null while Pending. Never blank once
 *                        set: the reason is mandatory at both ends (#42)
 * @param resolvedAt      when it was answered, or null while Pending
 * @param createdAt       when it was asked, in UTC
 */
public record ApprovalRequestDetailResponse(
        String id,
        String type,
        String status,
        String entityType,
        String entityId,
        String requestedByName,
        String justification,
        @Nullable String claimedByName,
        @Nullable String resolvedByName,
        @Nullable String resolutionNote,
        @Nullable LocalDateTime resolvedAt,
        LocalDateTime createdAt) {
}
