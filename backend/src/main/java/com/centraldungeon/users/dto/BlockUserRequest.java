package com.centraldungeon.users.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * The body of {@code POST /admin/users/{id}/block}.
 *
 * <p>Separate from {@link UnblockUserRequest} even though they have the same shape, because request
 * and response are never shared and neither are two different requests (regla dura 3): the day one
 * of them grows a field, the other one must not.
 *
 * @param justification why the account is being blocked. Required: the blocked person cannot log in
 *                      to ask, so the reason is the only record of it (#84)
 */
public record BlockUserRequest(@NotBlank String justification) {
}
