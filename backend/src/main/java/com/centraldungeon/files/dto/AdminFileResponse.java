package com.centraldungeon.files.dto;

import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * A file as /admin/files shows it: everything {@link FileResponse} has, plus who owns it, how many
 * tables use it and whether it is still live.
 *
 * <p>A separate record rather than three nullable fields on {@link FileResponse}, because those
 * three are exactly what nobody but an admin should see (arquitectura.md 2.3): the owner's name on
 * somebody else's upload, the usage count, and the fact that a file was marked gone.
 *
 * @param id             the file's identifier
 * @param name           the original filename
 * @param mimeType       the declared MIME type
 * @param sizeBytes      the size as it was uploaded, before compression (#75)
 * @param fileType       which of the three lifecycles it has (#68), as a string
 * @param publicAudience who a published file is for (#64). Null on anything that is not
 *                       {@code Public}
 * @param ownerId        who uploaded it. Publishing a file changes what it is for, never whose it is
 * @param ownerName      how to name them on screen - their Discord username, which everybody has
 * @param uses           how many tables hold a live link to it. <b>This is where #79 stops being a
 *                       claim</b>: one file used by three tables reads as one row with three uses,
 *                       which is what makes "linking is not copying" visible rather than asserted
 * @param status         live or marked gone, as a string. Marking never freed a byte (#25, #66)
 * @param lastUsedAt     when it was last used, in UTC. Null when it never was, which the purge reads
 *                       as its creation date (#75)
 * @param createdAt      when it was uploaded, in UTC
 */
public record AdminFileResponse(
        String id,
        String name,
        String mimeType,
        long sizeBytes,
        String fileType,
        @Nullable String publicAudience,
        String ownerId,
        String ownerName,
        long uses,
        String status,
        @Nullable LocalDateTime lastUsedAt,
        LocalDateTime createdAt) {
}
