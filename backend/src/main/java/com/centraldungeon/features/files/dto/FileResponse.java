package com.centraldungeon.features.files.dto;

import java.time.LocalDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A file as its owner sees it - what an upload answers with and what the reuse history lists.
 *
 * <p>No owner field: every one of these belongs to whoever asked, because the owner is in the
 * {@code WHERE} and comes from the token, never from the URL. {@code AdminFileResponse} is the
 * one that names people, because that screen is about somebody else's files by definition.
 *
 * @param id             the file's identifier. Also what its content is stored under, though
 *                       nothing outside the backend needs to know that
 * @param name           the original filename. Metadata only - it never touched the filesystem
 * @param mimeType       the declared MIME type, already checked against the whitelist
 * @param sizeBytes      the size as it was uploaded, before compression. This is the number the
 *                       screen shows and the one the per-file cap applies to
 * @param fileType       whether the owner is keeping it ({@code Private}), it is tied to one context
 *                       ({@code SingleUse}) or the platform published it ({@code Public}) - the three
 *                       lifecycles, as a string
 * @param categories     the categories the file belongs to, as strings. Plural and cumulative:
 *                       a sheet uploaded on an application and later handed in to a request belongs
 *                       to both, and nothing ever takes a category away. <b>Empty on an upload and on a
 *                       single-file read</b>, like {@code usages}
 * @param usages         where the file is linked <em>right now</em>, named by the table each
 *                       link belongs to - what turns the reuse history from a list of filenames
 *                       into something its owner recognises. A different question from the categories:
 *                       detaching it from a table removes a use and leaves the category standing.
 *                       <b>Empty on an upload and on a single-file read</b>: only the owner's own
 *                       list pays for resolving them, because only there is the question asked
 * @param lastUsedAt     when it was last uploaded, attached or downloaded, in UTC. What the retention purge
 *                       reads, and what tells the owner whether a file is still in service
 * @param createdAt      when it was uploaded, in UTC. The frontend converts
 */
public record FileResponse(
        String id,
        String name,
        String mimeType,
        long sizeBytes,
        String fileType,
        List<String> categories,
        List<FileUsageResponse> usages,
        @Nullable LocalDateTime lastUsedAt,
        LocalDateTime createdAt) {
}
