package com.centraldungeon.catalogs;

/**
 * How many live links point at one catalog value. Internal projection of the bridge repositories,
 * not a DTO: it never crosses HTTP - {@code AdminCatalogValueResponse} carries the number.
 *
 * <p>It exists so a page of twenty values costs one grouped query instead of twenty counts.
 *
 * @param valueId the catalog value the count belongs to
 * @param uses    how many tables link to it with a live link. A value nothing points at never
 *                appears in the result, so a missing entry means zero
 */
public record CatalogUsageCount(String valueId, long uses) {
}
