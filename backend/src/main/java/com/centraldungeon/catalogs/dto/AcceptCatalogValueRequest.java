package com.centraldungeon.catalogs.dto;

import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Accepting a proposed value and classifying it in the same step, which is what #55 describes: the
 * admin decides whether it is a new canonical entry or an alias of a group that already exists.
 *
 * @param canonicalId the group to join, or null to accept it as a canonical entry of its own. The
 *                    target must itself be canonical and accepted - depth is always 1 (#59), which
 *                    is what makes cycles impossible rather than merely unlikely
 */
public record AcceptCatalogValueRequest(@Nullable @Size(max = 64) String canonicalId) {
}
