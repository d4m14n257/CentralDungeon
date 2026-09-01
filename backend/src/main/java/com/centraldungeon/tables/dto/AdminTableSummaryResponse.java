package com.centraldungeon.tables.dto;

import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * Distinct from GameTableSummaryResponse on purpose: /admin/tables can list a table still in
 * Unassigned, which has no Primary yet, so primaryMasterName has to be nullable here. Reusing the
 * public summary would force that nullability onto GameTableCard and the three screens that
 * already render it with a guaranteed Primary (regla dura 2/3 - own record for its own shape).
 */
public record AdminTableSummaryResponse(
        String id,
        String name,
        String status,
        @Nullable String tableTypeName,
        @Nullable Integer maxPlayers,
        int playerCount,
        @Nullable String primaryMasterName,
        LocalDateTime createdAt) {
}
