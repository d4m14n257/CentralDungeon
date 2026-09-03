package com.centraldungeon.catalogs;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** A free-form label a master puts on their table ("Principiantes", "One-shot"). Table {@code tags}. */
@Entity
@Table(name = "tags")
public class Tag extends CatalogValue {

    /** Required by JPA. */
    protected Tag() {
    }

    /**
     * Builds a tag that has not been reviewed yet: no group, status {@code Created} (#55).
     *
     * @param name the tag as its author wrote it
     */
    public Tag(String name) {
        super(name);
    }
}
