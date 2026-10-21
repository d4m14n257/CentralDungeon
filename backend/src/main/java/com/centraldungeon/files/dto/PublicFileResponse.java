package com.centraldungeon.files.dto;

import java.util.List;

/**
 * A file the platform published, as anyone choosing one sees it (ADR-13 §2, ADR-14 §4).
 *
 * <p>This is what makes ADR-14 §1 usable: a master attaching the community's default character sheet picks
 * it from this list and links it, rather than downloading it and uploading their own copy. So the
 * record carries what a person needs to recognise the right document and nothing else - no owner, no
 * usage count, no status. Those are {@code AdminFileResponse}'s.
 *
 * @param id         the file's identifier, which is what the link request takes
 * @param name       the original filename
 * @param mimeType   the declared MIME type
 * @param sizeBytes  the size as it was uploaded, before compression (ADR-13 §3)
 * @param categories the cajones it is offered in (ADR-14 §4), as strings. Plural because the same blank
 *                   serves more than one flow - the sheet a table recruits with is the sheet it asks
 *                   for again mid-season
 */
public record PublicFileResponse(
        String id, String name, String mimeType, long sizeBytes, List<String> categories) {
}
