package com.centraldungeon.features.catalogs;

/**
 * Reads and writes the {@code tags} table. Everything it can do comes from
 * {@link CatalogValueRepository}: a tag is a catalog value and nothing more.
 */
public interface TagRepository extends CatalogValueRepository<Tag> {
}
