package com.centraldungeon.catalogs;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Link between a table and the game system its master picked. Table {@code table_systems}. */
@Entity
@Table(name = "table_systems")
public class TableSystem extends TableCatalogLink {

    /** The pair (table, system) this link joins. */
    @EmbeddedId
    private TableSystemId id;

    /** Required by JPA. */
    protected TableSystem() {
    }

    /**
     * Links a table to a system, live from the start.
     *
     * @param gameTableId the table being tagged
     * @param systemId    the system it is tagged with, as the master picked it (#56)
     */
    public TableSystem(String gameTableId, String systemId) {
        this.id = new TableSystemId(gameTableId, systemId);
    }

    /**
     * Returns the pair this link joins.
     *
     * @return the composite key, never null on a persisted row
     */
    public TableSystemId getId() {
        return id;
    }
}
