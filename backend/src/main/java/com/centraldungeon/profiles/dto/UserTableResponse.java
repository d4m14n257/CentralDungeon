package com.centraldungeon.profiles.dto;

import java.time.LocalDateTime;

/**
 * One table a person is linked to, as an admin reads it from that person's record (ADR-06 §8).
 *
 * <p>One row per table: nobody can run and play the same one (ADR-06 §5), so the relation is a single
 * value rather than a list.
 *
 * @param tableId        the table, to open it from /admin/tables/:id
 * @param tableName      its name
 * @param tableStatus    where it stands, as the {@code GameTableStatus} name
 * @param relation       what the person is to it: {@code Primary} or {@code Secondary} when they run
 *                       it; {@code Player}, {@code Candidate}, {@code Rejected} or {@code Blocked}
 *                       (vetoed, ADR-09 §4) when they applied - the frontend words each one (ADR-23 §3)
 * @param tableCreatedAt when the table was created, which is what the list is ordered by, newest first
 */
public record UserTableResponse(
        String tableId, String tableName, String tableStatus, String relation, LocalDateTime tableCreatedAt) {
}
