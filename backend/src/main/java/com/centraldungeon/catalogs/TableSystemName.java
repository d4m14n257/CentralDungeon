package com.centraldungeon.catalogs;

/**
 * The name of one system a table is live-linked to. Internal projection of {@link TableSystemRepository},
 * not a DTO: it never crosses HTTP.
 *
 * <p>It exists so a page of tables costs one query for all of their systems instead of one per table.
 *
 * @param gameTableId the table the link belongs to
 * @param name        the system as the master picked it
 */
public record TableSystemName(String gameTableId, String name) {
}
