package com.centraldungeon.files.dto;

/**
 * A table's file as a candidate or a player sees it, read-only.
 *
 * <p>Travels inside {@code GameTableDetailResponse}, not through an endpoint of its own - the same
 * decision F1.3 took for the calendar. That read <b>already</b> decides who may see the table, down
 * to answering 404 to somebody vetoed (#29), so the files inherit that one answer instead of
 * repeating the check somewhere it could drift out of step with it.
 *
 * <p>Deliberately smaller than {@link TableFileResponse}: no {@code isPrivate} - a private
 * attachment is simply absent from this list, never listed and hidden - and no owner, because who
 * uploaded the map is not something a player needs in order to open it.
 *
 * @param fileId        the file's identifier, which is what the download endpoint takes
 * @param name          the original filename, which is what the reader recognises it by
 * @param mimeType      the declared MIME type, so the screen can show the right icon
 * @param sizeBytes     the size as it was uploaded, before compression (#75). Shown because somebody
 *                      on a phone deserves to know what a tap is about to cost them
 * @param tableFileType what the file is doing on the table - prepared beforehand, or produced at a
 *                      session - as a string (arquitectura.md 2.3)
 */
public record SharedFileResponse(String fileId, String name, String mimeType, long sizeBytes, String tableFileType) {
}
