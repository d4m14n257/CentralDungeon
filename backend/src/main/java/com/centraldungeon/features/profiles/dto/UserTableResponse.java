package com.centraldungeon.features.profiles.dto;

import java.time.LocalDateTime;

/**
 * One table a person is linked to, as an admin reads it from that person's record.
 *
 * <p>One row per table: nobody can run and play the same one, so the relation is a single
 * value rather than a list.
 *
 * @param tableId        the table, to open it from /admin/tables/:id
 * @param tableName      its name
 * @param tableStatus    where it stands, as the {@code GameTableStatus} name
 * @param relation       what the person is to it: {@code Primary} or {@code Secondary} when they run
 *                       it; {@code Player}, {@code Candidate}, {@code Rejected} or {@code Blocked}
 *                       (vetoed) when they applied - the frontend words each one
 * @param tableCreatedAt when the table was created, which is what the list is ordered by, newest first
 */
public record UserTableResponse(
        String tableId, String tableName, String tableStatus, String relation, LocalDateTime tableCreatedAt) {
}
