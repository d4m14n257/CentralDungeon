package com.centraldungeon.files.dto;

/**
 * A file the platform published, as anyone choosing one sees it (#64).
 *
 * <p>This is what makes #79 usable: a master attaching the community's default character sheet picks
 * it from this list and links it, rather than downloading it and uploading their own copy. So the
 * record carries what a person needs to recognise the right document and nothing else - no owner, no
 * usage count, no status. Those are {@code AdminFileResponse}'s.
 *
 * @param id             the file's identifier, which is what the link request takes
 * @param name           the original filename
 * @param mimeType       the declared MIME type
 * @param sizeBytes      the size as it was uploaded, before compression (#75)
 * @param publicAudience who the file is for - masters, players or everybody (#64), as a string
 */
public record PublicFileResponse(String id, String name, String mimeType, long sizeBytes, String publicAudience) {
}
