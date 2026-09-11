package com.centraldungeon.tasks.dto;

/**
 * A blank the master attached to their request - the form the ask is about (#63, #233).
 *
 * <p>The same shape as {@link SubmittedFileResponse} and deliberately not the same record: they are
 * the two halves of one exchange and travel in opposite directions. One is what the master is asking
 * with, the other what a player answered with, and a screen that confused them would offer the wrong
 * download to the wrong person.
 *
 * @param fileId    the file, which is what the download endpoint takes
 * @param name      the original filename. Metadata only (#80)
 * @param mimeType  the declared MIME type
 * @param sizeBytes the size as it was uploaded, before compression (#75)
 */
public record TaskFileResponse(String fileId, String name, String mimeType, long sizeBytes) {
}
