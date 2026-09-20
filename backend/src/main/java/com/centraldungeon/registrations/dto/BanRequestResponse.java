package com.centraldungeon.registrations.dto;

import java.time.LocalDateTime;

/**
 * One pending veto request on a table, as the {@code Primary} who has to answer it reads it (#39).
 *
 * <p><b>A record of its own rather than {@code ApprovalRequestSummaryResponse}</b>, and the reason is
 * the one thing that DTO cannot say: <em>who</em> is going to be vetoed. It carries who asked and
 * why, which is right for the admin tray - there the entity is the requester - and leaves a
 * {@code Primary} with two open veto requests on the same table able to tell them apart only by the
 * wording of the reason. Naming the person is not a nicety on a screen whose whole purpose is
 * deciding about them.
 *
 * <p>The alternative was a nullable field on the shared summary, filled in for one type out of five.
 * That is the record with half its fields null that R3 forbids, and it would have made every other
 * reader of the summary carry a field that is null for them. Two questions, two shapes.
 *
 * @param requestId       the request, which is what approving or refusing addresses
 * @param registrationId  the application the veto is about - what the roster row is keyed by, so the
 *                        screen can line the two up
 * @param targetUserId    the person who would be vetoed
 * @param targetUserName  their display name. <b>The field this record exists for</b>: a decision
 *                        about a person is made by name, not by id
 * @param requestedByName the co-master who asked. They are the one the answer is written to (#42)
 * @param justification   why they are asking, verbatim. It is the whole of what the {@code Primary}
 *                        is deciding on
 * @param createdAt       when it was asked, in UTC. Oldest first, like every other queue
 */
public record BanRequestResponse(
        String requestId,
        String registrationId,
        String targetUserId,
        String targetUserName,
        String requestedByName,
        String justification,
        LocalDateTime createdAt) {
}
