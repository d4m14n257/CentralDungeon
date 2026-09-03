package com.centraldungeon.catalogs;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Where the table is played ("Discord", "Roll20", "Presencial"). Table {@code platforms}. */
@Entity
@Table(name = "platforms")
public class Platform extends CatalogValue {

    /** Required by JPA. */
    protected Platform() {
    }

    /**
     * Builds a platform that has not been reviewed yet: no group, status {@code Created} (#55).
     *
     * @param name the platform as its author wrote it
     */
    public Platform(String name) {
        super(name);
    }
}
