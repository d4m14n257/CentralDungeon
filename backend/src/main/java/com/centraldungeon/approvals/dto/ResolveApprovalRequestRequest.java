package com.centraldungeon.approvals.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The body of {@code POST /admin/requests/{id}/approve} and of {@code .../reject}. One record for
 * both, because the two carry exactly the same thing: a reason.
 *
 * <p><b>Mandatory in both directions</b> (#42). Approving without a note loses the only trace of why
 * somebody was given something, and rejecting without one is half the mechanism - the person is told
 * no and learns nothing they can act on.
 *
 * @param resolutionNote why the request was approved or rejected. Capped for the same reason the
 *                       justification is: the column is a {@code LONGTEXT}
 */
public record ResolveApprovalRequestRequest(@NotBlank @Size(max = 4000) String resolutionNote) {
}
