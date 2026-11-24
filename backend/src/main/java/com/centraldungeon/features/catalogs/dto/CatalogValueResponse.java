package com.centraldungeon.features.catalogs.dto;

import com.centraldungeon.features.catalogs.TagCategory;
import org.jspecify.annotations.Nullable;

/**
 * One catalog value as everyone outside /admin/catalogs sees it: the combobox of the wizard, and
 * later the explorer's filters.
 *
 * <p>No {@code canonicalId} on purpose. Which synonym group a value belongs to is an admin's
 * concern; the equivalence works in the search, not in the presentation (ADR-12 §4), so nothing on this
 * side of the API has a reason to know it.
 *
 * @param id     the value's identifier, used to link it to a table and to fetch it back
 * @param name   the value as it is written and displayed - the alias its author chose, never
 *               rewritten to the group's canonical entry (ADR-12 §4)
 * @param status the value's lifecycle state as a string ({@code Created}, {@code Accepted},
 *               {@code Rejected}, {@code Disabled}). It travels because the master who just
 *               proposed a value has to be told it is pending: a table tagged with something the
 *               other players cannot see yet has to say so (ADR-12 §3)
 * @param category    the category of the value's group, read from its canonical entry when the
 *                    value is an alias. Only tags have one: always null for systems and platforms,
 *                    and null for a tag group nobody has classified
 * @param description the definition of the value's group in plain text, read from its canonical
 *                    entry when the value is an alias. Only tags have one: always null for systems
 *                    and platforms, and null for a tag group nobody has defined
 */
public record CatalogValueResponse(
        String id, String name, String status, @Nullable TagCategory category, @Nullable String description) {
}
