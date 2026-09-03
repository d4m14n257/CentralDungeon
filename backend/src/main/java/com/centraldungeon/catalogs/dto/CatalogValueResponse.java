package com.centraldungeon.catalogs.dto;

/**
 * One catalog value as everyone outside /admin/catalogs sees it: the combobox of the wizard, and
 * later the explorer's filters.
 *
 * <p>No {@code canonicalId} on purpose. Which synonym group a value belongs to is an admin's
 * concern; the equivalence works in the search, not in the presentation (#58), so nothing on this
 * side of the API has a reason to know it.
 *
 * @param id     the value's identifier, used to link it to a table and to fetch it back
 * @param name   the value as it is written and displayed - the alias its author chose, never
 *               rewritten to the group's canonical entry (#58)
 * @param status the value's lifecycle state as a string ({@code Created}, {@code Accepted},
 *               {@code Rejected}, {@code Disabled}). It travels because the master who just
 *               proposed a value has to be told it is pending: a table tagged with something the
 *               other players cannot see yet has to say so (#57)
 */
public record CatalogValueResponse(String id, String name, String status) {
}
