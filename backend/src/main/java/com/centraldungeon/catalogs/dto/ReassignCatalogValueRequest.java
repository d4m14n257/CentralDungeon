package com.centraldungeon.catalogs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Moving one alias from its group to another in a single step (#276) - what dragging it onto
 * another group does on the catalog canvas (#275).
 *
 * <p>An operation of its own and not a split followed by a merge: those are two requests, and a
 * failure between them leaves the alias stranded as a group of one.
 *
 * @param canonicalId the group the alias moves to. It has to be an accepted canonical entry, and not
 *                    the group the alias is already in - depth is always 1 (#59)
 */
public record ReassignCatalogValueRequest(@NotBlank @Size(max = 64) String canonicalId) {
}
