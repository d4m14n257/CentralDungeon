package com.centraldungeon.catalogs;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A game system ("D&amp;D 5e", "Pathfinder 2e"). Table {@code systems}.
 *
 * <p>Named GameSystem and not System on purpose: a class called {@code System} in this package
 * would shadow {@code java.lang.System} for every file next to it. Same reason {@code GameTable} is
 * not {@code Table} - that one would collide with {@code jakarta.persistence.Table}.
 */
@Entity
@Table(name = "systems")
public class GameSystem extends CatalogValue {

    /** Required by JPA. */
    protected GameSystem() {
    }

    /**
     * Builds a system that has not been reviewed yet: no group, status {@code Created} (#55).
     *
     * @param name the system as its author wrote it
     */
    public GameSystem(String name) {
        super(name);
    }
}
