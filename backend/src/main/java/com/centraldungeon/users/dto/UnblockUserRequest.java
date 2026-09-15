package com.centraldungeon.users.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * The body of {@code POST /admin/users/{id}/unblock}.
 *
 * @param justification why the account is being let back in. Required, so the history reads as a
 *                      conversation and not as a block followed by an unexplained reversal (#84)
 */
public record UnblockUserRequest(@NotBlank String justification) {
}
