package com.centraldungeon.catalogs.dto;

import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Taking a value out of circulation (#81). Logical, never physical: every link that points at it
 * keeps its row, so restoring it puts everything back with no migration.
 *
 * @param newCanonicalId only needed when the value being disabled is a canonical entry that still
 *                       has live aliases. Under a flat {@code canonical_id}, disabling the canonical
 *                       and changing the canonical are the same operation (#59), and the successor
 *                       is the admin's choice, never an arbitrary "first alias" (#55). It has to be
 *                       a live member of the group being changed. Leave it null for anything else
 */
public record DisableCatalogValueRequest(@Nullable @Size(max = 64) String newCanonicalId) {
}
