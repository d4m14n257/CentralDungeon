package com.centraldungeon.files;

/**
 * How many tables a file is attached to. Internal projection of {@link TableFileRepository}, not a
 * DTO: it never crosses HTTP - {@code AdminFileResponse} carries the number.
 *
 * <p>It exists so a page of twenty files on /admin/files costs one grouped query instead of twenty
 * counts, the same shape as {@code CatalogUsageCount}.
 *
 * <p>It is also what makes #79 visible: a file used by three tables is one row with three uses, not
 * three files, and a screen that shows the number is a screen where "linking is not copying" stops
 * being a claim in a document.
 *
 * @param fileId the file the count belongs to
 * @param uses   how many tables hold a live link to it. A file nothing points at never appears in
 *               the result, so a missing entry means zero
 */
public record FileUsageCount(String fileId, long uses) {
}
