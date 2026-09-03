package com.centraldungeon.catalogs;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

/**
 * Composite key of {@link TableSystem}: one table can use a system once. Same pattern as
 * {@code MasterId}.
 *
 * @param gameTableId the table the link belongs to. A plain id, not an association - see
 *                    {@link TableCatalogLink} for why
 * @param systemId    the system the table was tagged with, exactly as its master picked it (#56)
 */
@Embeddable
public record TableSystemId(
        @Column(name = "game_table_id", length = 64) String gameTableId,
        @Column(name = "system_id", length = 64) String systemId)
        implements Serializable {
}
