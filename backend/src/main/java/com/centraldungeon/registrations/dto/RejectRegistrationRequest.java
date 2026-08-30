package com.centraldungeon.registrations.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectRegistrationRequest(@NotBlank String justification) {
}
