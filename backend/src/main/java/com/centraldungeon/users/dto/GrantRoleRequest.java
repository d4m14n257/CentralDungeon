package com.centraldungeon.users.dto;

import com.centraldungeon.users.PlatformRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * The body of {@code POST /admin/users/{id}/grant-role}.
 *
 * <p>Who may grant which role is <b>not</b> decided here: a request that names Owner is well formed
 * whoever sends it, and an admin sending it gets a 403 and not a 400 - it is a question of who you
 * are, not of what you sent (fase-3-admin-owner.md 3).
 *
 * @param role          the role to grant. A name outside the four is a 400 from Jackson, which is
 *                      correct: it is not a role
 * @param justification why. Required, like every other change an admin makes to somebody else's
 *                      account: the history is only worth keeping if the reason is in it (#169)
 */
public record GrantRoleRequest(@NotNull PlatformRole role, @NotBlank String justification) {
}
