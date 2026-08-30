package com.centraldungeon.tables.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.jspecify.annotations.Nullable;

public record CreateGameTableRequest(
        @NotBlank @Size(max = 128) String name,
        @Nullable String description,
        @Nullable String requirements,
        @Nullable String tableTypeId,
        @Nullable LocalDateTime startDate,
        @Nullable LocalTime duration,
        @Positive @Nullable Integer totalSessions,
        @Positive @Nullable Integer maxPlayers) {
}
