package com.centraldungeon.tables.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangeTableStatusRequest(@NotBlank String justification) {
}
