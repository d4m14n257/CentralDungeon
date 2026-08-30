package com.centraldungeon.tables.dto;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record GameTableDetailResponse(
        String id,
        String name,
        @Nullable String description,
        @Nullable String requirements,
        @Nullable String tableTypeName,
        String status,
        @Nullable Integer maxPlayers,
        int playerCount,
        @Nullable LocalDateTime startDate,
        @Nullable LocalTime duration,
        @Nullable Integer totalSessions,
        List<MasterSummaryResponse> masters,
        LocalDateTime createdAt) {
}
