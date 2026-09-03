package com.centraldungeon.catalogs;

/**
 * Reads and writes the {@code systems} table. Everything it can do comes from
 * {@link CatalogValueRepository}: a game system is a catalog value and nothing more.
 */
public interface SystemRepository extends CatalogValueRepository<GameSystem> {
}
