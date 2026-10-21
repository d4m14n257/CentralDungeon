package com.centraldungeon.catalogs;

/**
 * State of one link between a table and a catalog value. The baseline defaults the three bridge
 * tables to {@code Used} (arquitectura §4.4).
 *
 * <p>A link is never deleted, only unlinked: the row is what says the master once chose that alias
 * (ADR-12 §4), and disabling the catalog value it points at must not break it (ADR-12 §5).
 */
public enum TableCatalogLinkStatus {

    /** The table is currently tagged with this value. */
    Used,

    /** The master took it off the table. Kept as a row, skipped by every read. */
    Removed
}
