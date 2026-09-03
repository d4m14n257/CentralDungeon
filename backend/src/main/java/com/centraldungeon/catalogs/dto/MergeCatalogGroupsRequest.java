package com.centraldungeon.catalogs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Merging two synonym groups into one - "DANDD" and "D&amp;D 5e" turn out to be the same thing.
 *
 * <p>It is an operation of its own and not a PATCH of {@code canonicalId}, because it is part of the
 * product (#55): the source's whole group moves, aliases included, and nothing about that is
 * expressible as setting one field.
 *
 * @param sourceCanonicalId the group that stops being a group. It becomes an alias of the target,
 *                          and so does every alias it held. Has to be a canonical entry itself
 * @param targetCanonicalId the group that survives and keeps its name. Has to be a canonical entry
 *                          too, and a different one - merging a group into itself is rejected
 */
public record MergeCatalogGroupsRequest(
        @NotBlank @Size(max = 64) String sourceCanonicalId, @NotBlank @Size(max = 64) String targetCanonicalId) {
}
