package com.centraldungeon.tables.dto;

import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * Distinct from GameTableSummaryResponse on purpose: /admin/tables can list a table still in
 * Unassigned, which has no Primary yet, so primaryMasterName has to be nullable here. Reusing the
 * public summary would force that nullability onto GameTableCard and the three screens that
 * already render it with a guaranteed Primary (regla dura 2/3 - own record for its own shape).
 *
 * @param id                the table's identifier
 * @param name              the table's title
 * @param status            where the table is in its lifecycle, as a string
 * @param tableTypeName     how the table is run, resolved to its label. Null when none was chosen
 * @param maxPlayers        the player cap (#24), or null for no cap
 * @param playerCount       how many people are accepted right now
 * @param primaryMasterName who runs the table, as a display name. <b>Null for an Unassigned table</b>,
 *                          which is the whole reason this record exists
 * @param createdAt         when the table was created, in UTC. It orders the admin's queue: the
 *                          oldest is the one that has been waiting longest
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
