package com.centraldungeon.features.catalogs;

import org.jspecify.annotations.Nullable;

/**
 * What a catalog group says about itself beyond its name: a category and a short definition. Only
 * tags carry one today; for systems and platforms it is always {@link #NONE}.
 *
 * <p>Not a DTO: it is how a catalog service hands the definition of a value - its own when it heads
 * its group, its canonical entry's when it is an alias - to the code that builds the responses.
 *
 * @param category    the group's category, or null when it has none
 * @param description the group's definition in plain text, or null when it has none
 */
public record CatalogDefinition(@Nullable TagCategory category, @Nullable String description) {

    /** No category and no definition. */
    public static final CatalogDefinition NONE = new CatalogDefinition(null, null);
}
