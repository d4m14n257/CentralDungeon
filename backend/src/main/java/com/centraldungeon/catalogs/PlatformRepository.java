package com.centraldungeon.catalogs;

/**
 * Reads and writes the {@code platforms} table. Everything it can do comes from
 * {@link CatalogValueRepository}: a platform is a catalog value and nothing more.
 */
public interface PlatformRepository extends CatalogValueRepository<Platform> {
}
