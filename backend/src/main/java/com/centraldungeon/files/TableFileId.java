package com.centraldungeon.files;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

/**
 * Composite key of {@link TableFile}: a table attaches a given file once. Same pattern as
 * {@code TableSystemId} and {@code MasterId}.
 *
 * <p>Because the pair <b>is</b> the key, detaching and re-attaching the same file cannot insert a
 * second row - it revives the one that is already there. That is the trap {@code TableScheduleStatus}
 * documents for the agenda, and it applies here for the same reason.
 *
 * @param gameTableId the table the file is attached to. A plain id and not an association: files
 *                    never navigate to a table, only the other way round, and mapping it would make
 *                    this package depend on {@code tables/} for nothing. The database still enforces
 *                    the foreign key
 * @param fileId      the file being attached. Also a plain id, so the link can be counted and
 *                    filtered without loading either side
 */
@Embeddable
public record TableFileId(
        @Column(name = "game_table_id", length = 64) String gameTableId,
        @Column(name = "file_id", length = 64) String fileId)
        implements Serializable {
}
