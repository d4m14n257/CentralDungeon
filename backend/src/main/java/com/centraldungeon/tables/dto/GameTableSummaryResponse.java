package com.centraldungeon.tables.dto;

import org.jspecify.annotations.Nullable;

public record GameTableSummaryResponse(
        String id,
        String name,
        String status,
        @Nullable String tableTypeName,
        @Nullable Integer maxPlayers,
        int playerCount,
        MasterSummaryResponse primaryMaster) {
}
