package com.centraldungeon.features.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Onboarding and later profile edit: display name and ISO 3166-1 alpha-2 country, both required. */
public record UpdateUserRequest(
        @NotBlank @Size(max = 64) String name, @NotBlank @Pattern(regexp = "^[A-Z]{2}$") String country) {
}
