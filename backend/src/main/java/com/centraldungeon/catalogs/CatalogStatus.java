package com.centraldungeon.catalogs;

/**
 * Lifecycle of one catalog value. The baseline defaults the column to {@code Created} for the three
 * catalog tables (modelo-datos.md 4); the rest of the vocabulary is defined here.
 *
 * <p>Only {@link #Accepted} values are offered to players, shown to them or usable as a filter
 * (decisiones.md #57, #81). The other three are invisible outside /admin/catalogs, each for its own
 * reason: {@code Created} has not been reviewed yet, {@code Rejected} never will be, and
 * {@code Disabled} was taken out of circulation without breaking a single link (#81).
 */
public enum CatalogStatus {

    /** Proposed by a master or an admin, waiting for an admin to accept and classify it (#55). */
    Created,

    /** Reviewed and in circulation: it shows, it filters, and it can be picked from the combobox. */
    Accepted,

    /** An admin decided it should not exist. It never shows and never filters (#57). */
    Rejected,

    /**
     * Logically deleted (#81). Links in table_systems / table_tags / table_platforms stay exactly as
     * they were, so restoring it puts everything back with no migration.
     */
    Disabled
}
