package com.centraldungeon.tasks.dto;

/**
 * A blank the master attached to their request - the form the ask is about (ADR-15 §1, ADR-14 §4).
 *
 * <p>The same shape as {@link SubmittedFileResponse} and deliberately not the same record: they are
 * the two halves of one exchange and travel in opposite directions. One is what the master is asking
 * with, the other what a player answered with, and a screen that confused them would offer the wrong
 * download to the wrong person.
 *
 * @param fileId    the file, which is what the download endpoint takes
 * @param name      the original filename. Metadata only (ADR-13 §6)
 * @param mimeType  the declared MIME type
 * @param sizeBytes the size as it was uploaded, before compression (ADR-13 §3)
 */
public record TaskFileResponse(String fileId, String name, String mimeType, long sizeBytes) {
}
