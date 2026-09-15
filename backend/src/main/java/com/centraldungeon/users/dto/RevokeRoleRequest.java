package com.centraldungeon.users.dto;

import com.centraldungeon.users.PlatformRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * The body of {@code POST /admin/users/{id}/revoke-role}.
 *
 * <p>A POST with a body and not a DELETE: the reason is mandatory, and a DELETE that only works
 * with a body is not an honest DELETE.
 *
 * @param role          the role to take away
 * @param justification why. Required (#169)
 */
public record RevokeRoleRequest(@NotNull PlatformRole role, @NotBlank String justification) {
}
