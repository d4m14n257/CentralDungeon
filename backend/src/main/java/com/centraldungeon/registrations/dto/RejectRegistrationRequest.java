package com.centraldungeon.registrations.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Turning down an application.
 *
 * @param justification why. Required: a rejection without a reason is the one thing the applicant
 *                      cannot learn anything from, and it is what the notification carries to them
 */
public record RejectRegistrationRequest(@NotBlank String justification) {
}
