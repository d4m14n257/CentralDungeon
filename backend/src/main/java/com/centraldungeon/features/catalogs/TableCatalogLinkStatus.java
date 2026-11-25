package com.centraldungeon.features.catalogs;

/**
 * State of one link between a table and a catalog value. The baseline defaults the three bridge
 * tables to {@code Used}.
 *
 * <p>A link is never deleted, only unlinked: the row is what says the master once chose that alias, and
 * disabling the catalog value it points at must not break it.
 */
public enum TableCatalogLinkStatus {

    /** The table is currently tagged with this value. */
    Used,

    /** The master took it off the table. Kept as a row, skipped by every read. */
    Removed
}
