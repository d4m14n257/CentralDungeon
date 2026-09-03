package com.centraldungeon.tables;

/**
 * Whether a {@link Master} row is live. Separate from {@link MasterType}, which says <em>what kind</em>
 * of master the person is - this one only says whether the row still counts.
 *
 * <p>A master row is never physically dropped: it is the record of who ran a table, and a finished
 * table has to keep saying so.
 */
public enum MasterRowStatus {

    /** Live: this person runs the table, and every membership check counts them. */
    Created,

    /** Logically deleted, only ever by the cascade of a deleted table (#25, #175). */
    Deleted
}
