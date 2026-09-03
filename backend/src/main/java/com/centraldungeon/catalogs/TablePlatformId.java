package com.centraldungeon.catalogs;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

/**
 * Composite key of {@link TablePlatform}: one table can list a platform once. Same pattern as
 * {@code MasterId}.
 *
 * @param gameTableId the table the link belongs to. A plain id, not an association - see
 *                    {@link TableCatalogLink} for why
 * @param platformId  the platform the table is played on, exactly as its master picked it (#56)
 */
@Embeddable
public record TablePlatformId(
        @Column(name = "game_table_id", length = 64) String gameTableId,
        @Column(name = "platform_id", length = 64) String platformId)
        implements Serializable {
}
