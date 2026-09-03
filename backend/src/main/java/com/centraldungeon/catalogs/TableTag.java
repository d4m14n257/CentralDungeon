package com.centraldungeon.catalogs;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Link between a table and a tag. Table {@code table_tags}.
 *
 * <p>It stores the alias the master chose, never the group's canonical entry (#56): merging two
 * synonyms later makes this table findable by both terms without rewriting a single row.
 */
@Entity
@Table(name = "table_tags")
public class TableTag extends TableCatalogLink {

    /** The pair (table, tag) this link joins. */
    @EmbeddedId
    private TableTagId id;

    /** Required by JPA. */
    protected TableTag() {
    }

    /**
     * Links a table to a tag, live from the start.
     *
     * @param gameTableId the table being labelled
     * @param tagId       the tag it is labelled with, as the master wrote it (#56)
     */
    public TableTag(String gameTableId, String tagId) {
        this.id = new TableTagId(gameTableId, tagId);
    }

    /**
     * Returns the pair this link joins.
     *
     * @return the composite key, never null on a persisted row
     */
    public TableTagId getId() {
        return id;
    }
}
