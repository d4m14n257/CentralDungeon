package com.centraldungeon.features.approvals.dto;

import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * One request in full: what the detail shows, and what all three mutating endpoints answer with so
 * the screen does not have to re-fetch.
 *
 * <p>Unlike the summary it publishes {@code entityType} and {@code entityId}. They are the
 * polymorphic reference, and the detail is the one place worth being able to read it from:
 * once requests can be about a table or an application, "what is this about" stops being obvious
 * from the requester's name.
 *
 * @param id              the request
 * @param type            which request it is, as a string like every other enum on the wire
 * @param status          {@code Pending}, {@code Approved} or {@code Rejected}
 * @param entityType      what the request is about: {@code user} for the three person-centred types
 * @param entityId        the id of that thing. Not a foreign key - the service is what guarantees it
 *                        resolves
 * @param requestedByName the requester's display name, already resolved
 * @param justification   why they asked
 * @param claimedByName   the admin who reserved it. Null until an admin reserves it from the shared queue
 * @param resolvedByName  the admin who answered, or null while the request is Pending
 * @param resolutionNote  why it was approved or rejected, or null while Pending. Never blank once
 *                        set: the reason is mandatory at both ends
 * @param resolvedAt      when it was answered, or null while Pending
 * @param gameTableId     the table the request is about - a pause names it, a veto reaches it through
 *                        its application - or null when it is about none
 * @param subjectName     for a veto request, the display name of the person it is about; null for
 *                        every other type
 * @param proposedTableName for a {@code TableOpen}, the name proposed for the table - the name the
 *                        draft is created with when it is approved; null for every other type
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
        @Nullable String gameTableId,
        @Nullable String subjectName,
        @Nullable String proposedTableName,
        LocalDateTime createdAt) {
}
