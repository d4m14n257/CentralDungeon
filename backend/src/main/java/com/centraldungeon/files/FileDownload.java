package com.centraldungeon.files;

/**
 * A file on its way out: the bytes plus what the browser needs to save them.
 *
 * <p>Internal carrier, not a DTO - it never gets serialized as JSON. It exists so {@code FileService}
 * can hand the controller everything a download needs in one value, instead of the controller reading
 * the entity for the headers and the storage for the content and having to keep the two in step.
 *
 * @param name     the original filename, for the {@code Content-Disposition} header. This is the one
 *                 place the name the user typed is used at all - and it goes into a header, never
 *                 into a path (#80)
 * @param mimeType the declared MIME type, for {@code Content-Type}
 * @param content  the bytes as they were uploaded. Storage compressed them on the way in and
 *                 expanded them again on the way out (#75); nothing above that layer notices
 */
public record FileDownload(String name, String mimeType, byte[] content) {
}
