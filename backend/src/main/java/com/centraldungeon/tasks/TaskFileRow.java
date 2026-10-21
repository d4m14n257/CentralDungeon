package com.centraldungeon.tasks;

/**
 * One blank attached to one request, already joined to the file it points at. Internal projection:
 * it never crosses HTTP - {@code TaskFileResponse} is what does.
 *
 * <p>It exists so a board of ten requests costs one query instead of ten plus ten: the link table
 * alone would give ids, and turning those into names would be a second round trip per request.
 *
 * @param taskId    the request the blank is attached to, which is what the service groups by
 * @param fileId    the file
 * @param name      the original filename. Metadata only (ADR-13 §6)
 * @param mimeType  the declared MIME type
 * @param sizeBytes the size as it was uploaded, before compression (ADR-13 §3)
 */
public record TaskFileRow(String taskId, String fileId, String name, String mimeType, long sizeBytes) {
}
