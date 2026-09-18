package com.centraldungeon.approvals.dto;

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
 * polymorphic reference (#78), and in F3.2 they always say "the person who asked" - a fact the row
 * already states by naming them. {@link ApprovalRequestDetailResponse} publishes them because the
 * detail is where somebody goes to understand what a request points at.
 *
 * @param id                the request
 * @param type              which request it is, as a string like every other enum on the wire
 * @param status            {@code Pending}, {@code Approved} or {@code Rejected}
 * @param requestedByName   the requester's display name, already resolved. Never an id: a screen
 *                          shows a person
 * @param justification     why they asked
 * @param claimedByName     the admin who reserved this item (#100). <b>Always null in F3.2</b> -
 *                          nothing writes the column until the shared queue lands in F3.3. It is
 *                          published now so the field does not appear later and change the shape
 * @param createdAt         when it was asked, in UTC
 */
public record ApprovalRequestSummaryResponse(
        String id,
        String type,
        String status,
        String requestedByName,
        String justification,
        @Nullable String claimedByName,
        LocalDateTime createdAt) {
}
