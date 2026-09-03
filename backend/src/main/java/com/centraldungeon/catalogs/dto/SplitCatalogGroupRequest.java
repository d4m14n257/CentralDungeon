package com.centraldungeon.catalogs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Taking one alias out of its group: it stops being equivalent to the rest and becomes a canonical
 * entry of its own. The undo of a merge that turned out wrong (#55).
 *
 * @param memberId the alias that leaves. It has to be an alias - a canonical entry has no group to
 *                 leave, and asking for that is answered with a 409
 */
public record SplitCatalogGroupRequest(@NotBlank @Size(max = 64) String memberId) {
}
