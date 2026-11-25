package com.centraldungeon.features.approvals.dto;

import com.centraldungeon.features.approvals.ApprovalRequestType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * The body of {@code POST /requests}: what somebody is asking for, and why.
 *
 * <p><b>There is no {@code entityId} here, on purpose.</b> The three request types this endpoint accepts are all
 * about the person asking, and that person comes from the token and never from the body.
 * Accepting an id would be accepting a request made on somebody else's
 * behalf - and the reference has no foreign key to catch it.
 *
 * @param type          which request this is. A name outside the three is a 400 from Jackson, which
 *                      is correct: it is not a request type
 * @param justification why. Required at this end and at the other one: a request an admin
 *                      cannot make sense of is one they cannot answer. The cap is explicit because
 *                      the column is a {@code LONGTEXT} - without it a 4 MB body goes in without a
 *                      word of complaint
 * @param tableName     the name of the table to open. Required for a {@code TableOpen}, where approving
 *                      the request creates a draft with this name; ignored for every other type
 */
public record SubmitApprovalRequestRequest(
        @NotNull ApprovalRequestType type,
        @NotBlank @Size(max = 4000) String justification,
        @Nullable @Size(max = 128) String tableName) {
}
