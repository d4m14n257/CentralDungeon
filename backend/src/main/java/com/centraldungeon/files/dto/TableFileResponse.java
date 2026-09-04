package com.centraldungeon.files.dto;

import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * A file attached to a table, as the people running it see it in the Archivos tab.
 *
 * <p>It carries the file <em>and</em> the link, because the two say different things and the screen
 * needs both: {@code isPrivate} is about this attachment, {@code fileType} about the file itself
 * (#64, #68, #79). A master looking at the tab has to be able to tell "this is mine and only my
 * co-masters see it" from "this is the platform's default sheet, which I did not upload".
 *
 * @param fileId        the file's identifier
 * @param name          the original filename
 * @param mimeType      the declared MIME type
 * @param sizeBytes     the size as it was uploaded, before compression (#75)
 * @param fileType      which lifecycle the <b>file</b> has (#68), as a string. {@code Public} means
 *                      an admin published it and this table is only borrowing it (#79)
 * @param tableFileType what the file is doing on the table - prepared beforehand, or produced at a
 *                      session - as a string
 * @param isPrivate     whether only the people running <b>this</b> table see it. About the link, not
 *                      the file: the same file can be shared on one table and private on another
 * @param isOwnedByMe   whether the actor uploaded it. What tells the screen it may offer to rename or
 *                      delete the file itself, rather than only detach it from this table (#79)
 * @param attachedAt    when it was attached to this table, in UTC
 */
public record TableFileResponse(
        String fileId,
        String name,
        String mimeType,
        long sizeBytes,
        String fileType,
        String tableFileType,
        boolean isPrivate,
        boolean isOwnedByMe,
        @Nullable LocalDateTime attachedAt) {
}
