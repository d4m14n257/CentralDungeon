package com.centraldungeon.catalogs;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

/**
 * Composite key of {@link TableTag}: one table can carry a tag once. Same pattern as
 * {@code MasterId}.
 *
 * @param gameTableId the table the link belongs to. A plain id, not an association - see
 *                    {@link TableCatalogLink} for why
 * @param tagId       the tag the table was labelled with, exactly as its master picked it (#56)
 */
@Embeddable
public record TableTagId(
        @Column(name = "game_table_id", length = 64) String gameTableId,
        @Column(name = "tag_id", length = 64) String tagId)
        implements Serializable {
}
