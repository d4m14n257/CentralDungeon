package com.centraldungeon.tasks.dto;

/**
 * A file handed in with an answer, as whoever may read that answer sees it.
 *
 * <p>Its own record rather than {@code SharedFileResponse}, which is the shape a table's attachments
 * use: that one carries {@code tableFileType} - prepared beforehand, or produced at a session - and
 * a submitted file has no such thing. A field that is meaningless in half its uses is how a shared
 * DTO starts drifting (arquitectura.md 2.3).
 *
 * @param fileId    the file's identifier, which is what the download endpoint takes
 * @param name      the original filename, which is what the reader recognises it by (#80)
 * @param mimeType  the declared MIME type, so the screen can show the right icon
 * @param sizeBytes the size as it was uploaded, before compression (#75). Shown because somebody on a
 *                  phone deserves to know what a tap is about to cost them
 */
public record SubmittedFileResponse(String fileId, String name, String mimeType, long sizeBytes) {
}
