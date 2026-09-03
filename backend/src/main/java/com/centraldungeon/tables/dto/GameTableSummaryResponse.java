package com.centraldungeon.tables.dto;

import org.jspecify.annotations.Nullable;

/**
 * A table as a card in a listing shows it. The explorer's shape.
 *
 * @param id            the table's identifier
 * @param name          the table's title
 * @param status        where the table is in its lifecycle, as a string
 * @param tableTypeName how the table is run, resolved to its label. Null when none was chosen
 * @param maxPlayers    the player cap (#24), or null for no cap
 * @param playerCount   how many people are accepted right now, so the card can show "3/5"
 * @param primaryMaster who runs the table. Non-null here because every table this endpoint lists
 *                      already has a Primary - the Unassigned case has its own shape,
 *                      {@link AdminTableSummaryResponse}
 */
public record GameTableSummaryResponse(
        String id,
        String name,
        String status,
        @Nullable String tableTypeName,
        @Nullable Integer maxPlayers,
        int playerCount,
        MasterSummaryResponse primaryMaster) {
}
