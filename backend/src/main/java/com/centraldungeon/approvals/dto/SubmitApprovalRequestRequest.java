package com.centraldungeon.approvals.dto;

import com.centraldungeon.approvals.ApprovalRequestType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The body of {@code POST /requests}: what somebody is asking for, and why.
 *
 * <p><b>There is no {@code entityId} here, on purpose.</b> The three request types of F3.2 are all
 * about the person asking, and that person comes from the token and never from the body
 * (arquitectura.md 2.6). Accepting an id would be accepting a request made on somebody else's
 * behalf - and the reference has no foreign key to catch it (#78).
 *
 * @param type          which request this is. A name outside the three is a 400 from Jackson, which
 *                      is correct: it is not a request type
 * @param justification why. Required at this end and at the other one (#42): a request an admin
 *                      cannot make sense of is one they cannot answer. The cap is explicit because
 *                      the column is a {@code LONGTEXT} - without it a 4 MB body goes in without a
 *                      word of complaint
 */
public record SubmitApprovalRequestRequest(
        @NotNull ApprovalRequestType type,
        @NotBlank @Size(max = 4000) String justification) {
}
