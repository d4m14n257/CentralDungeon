package com.centraldungeon.features.approvals.dto;

import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * One request as a row - of the admin listing and of "my requests" alike.
 *
 * <p>It carries the justification, which a summary would not normally do. That is the point of the
 * screen: an admin decides whether to open a request by reading why it was made, and a row that
 * hides the reason forces a click per request to find the one worth answering.
 *
 * <p>It does <b>not</b> carry {@code entityType} / {@code entityId}. Those are machinery of the
 * polymorphic reference; {@link ApprovalRequestDetailResponse} publishes them because the
 * detail is where somebody goes to understand what a request points at. What the row does carry is the
 * one thing it needs to link out: the table, when the request is about one.
 *
 * @param id                the request
 * @param type              which request it is, as a string like every other enum on the wire
 * @param status            {@code Pending}, {@code Approved} or {@code Rejected}
 * @param requestedById     the requester's id, only so the screen can link their name to their page.
 *                          What a screen <em>shows</em> is {@code requestedByName}
 * @param requestedByName   the requester's display name, already resolved: a screen shows a person
 * @param justification     why they asked
 * @param claimedByName     the admin who reserved this item. <b>Null until an admin reserves it</b> -
 *                          only the shared queue writes the column. It is
 *                          published so the field is part of the shape from the start
 * @param gameTableId       the table the request is about, or null when it is about none. A pause
 *                          points at its table directly and a veto through the application it is
 *                          about; the other types concern a person, not a table. Resolved by the
 *                          service for the whole page, so the screen can link the table
 * @param subjectName       for a veto request, the display name of the person it is about - an admin
 *                          deciding it has to read whom it concerns, not only who asked. Null for
 *                          every other type, whose subject is the requester
 * @param proposedTableName for a {@code TableOpen}, the name proposed for the table; null for every
 *                          other type
 * @param createdAt         when it was asked, in UTC
 */
public record ApprovalRequestSummaryResponse(
        String id,
        String type,
        String status,
        String requestedById,
        String requestedByName,
        String justification,
        @Nullable String claimedByName,
        @Nullable String gameTableId,
        @Nullable String subjectName,
        @Nullable String proposedTableName,
        LocalDateTime createdAt) {
}
