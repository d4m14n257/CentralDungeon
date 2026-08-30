package com.centraldungeon.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Onboarding (modelo-datos.md #134): display name and ISO 3166-1 alpha-2 country, both required to complete it. */
public record UpdateUserRequest(
        @NotBlank @Size(max = 64) String name, @NotBlank @Pattern(regexp = "^[A-Z]{2}$") String country) {
}
