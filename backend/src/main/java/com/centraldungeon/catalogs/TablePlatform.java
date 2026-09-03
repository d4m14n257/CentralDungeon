package com.centraldungeon.catalogs;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Link between a table and a platform it is played on. Table {@code table_platforms}. */
@Entity
@Table(name = "table_platforms")
public class TablePlatform extends TableCatalogLink {

    /** The pair (table, platform) this link joins. */
    @EmbeddedId
    private TablePlatformId id;

    /** Required by JPA. */
    protected TablePlatform() {
    }

    /**
     * Links a table to a platform, live from the start.
     *
     * @param gameTableId the table being described
     * @param platformId  the platform it is played on, as the master picked it (#56)
     */
    public TablePlatform(String gameTableId, String platformId) {
        this.id = new TablePlatformId(gameTableId, platformId);
    }

    /**
     * Returns the pair this link joins.
     *
     * @return the composite key, never null on a persisted row
     */
    public TablePlatformId getId() {
        return id;
    }
}
