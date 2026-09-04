package com.centraldungeon.files.dto;

import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * A file as its owner sees it - what an upload answers with and what the reuse history of #65 lists.
 *
 * <p>No owner field: every one of these belongs to whoever asked, because the owner is in the
 * {@code WHERE} and comes from the token, never from the URL (#121). {@code AdminFileResponse} is the
 * one that names people, because that screen is about somebody else's files by definition.
 *
 * @param id             the file's identifier. Also what its content is stored under (#80), though
 *                       nothing outside the backend needs to know that
 * @param name           the original filename. Metadata only - it never touched the filesystem (#80)
 * @param mimeType       the declared MIME type, already checked against the whitelist
 * @param sizeBytes      the size as it was uploaded, before compression (#75). This is the number the
 *                       screen shows and the one the per-file cap applies to
 * @param fileType       whether the owner is keeping it ({@code Private}), it is tied to one context
 *                       ({@code SingleUse}) or the platform published it ({@code Public}) - the three
 *                       lifecycles of #68, as a string (arquitectura.md 2.3)
 * @param publicAudience who a published file is for (#64). Null on anything that is not
 *                       {@code Public}
 * @param lastUsedAt     when it was last uploaded, attached or downloaded, in UTC. What the purge of
 *                       #75 reads, and what tells the owner whether a file is still in service
 * @param createdAt      when it was uploaded, in UTC. The frontend converts (#22, #111)
 */
public record FileResponse(
        String id,
        String name,
        String mimeType,
        long sizeBytes,
        String fileType,
        @Nullable String publicAudience,
        @Nullable LocalDateTime lastUsedAt,
        LocalDateTime createdAt) {
}
