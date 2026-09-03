package com.centraldungeon.catalogs;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * What the three catalog repositories share. {@code @NoRepositoryBean}: this one is never
 * instantiated, only extended by {@link SystemRepository}, {@link TagRepository} and
 * {@link PlatformRepository}.
 *
 * <p>{@code JpaSpecificationExecutor} is here because /admin/catalogs combines a free-text query
 * with a status filter, and the shape is only known at runtime (arquitectura.md 2.2).
 *
 * @param <E> the catalog entity this repository reads - {@code GameSystem}, {@code Tag} or
 *            {@code Platform}
 */
@NoRepositoryBean
public interface CatalogValueRepository<E extends CatalogValue> extends JpaRepository<E, String>, JpaSpecificationExecutor<E> {

    /**
     * Finds a value by name, ignoring case.
     *
     * <p>The uniqueness the baseline declares is case sensitive at the column level, but two people
     * proposing "one-shot" and "One-Shot" mean the same value, so the check here is not.
     *
     * @param name the name to look for, in any case
     * @return the value with that name, or empty when the name is free
     */
    Optional<E> findByNameIgnoreCase(String name);

    /**
     * A whole synonym group in one query: the canonical entry (matched by its own id) plus every
     * alias pointing at it. Depth is always 1 (#59), so this is the complete group - there is no
     * second level to recurse into.
     *
     * @param id          the canonical entry's id
     * @param canonicalId the same id again, matched against the aliases' {@code canonical_id}
     * @return every member of the group, whatever its status. Never null, possibly a single row
     */
    List<E> findByIdOrCanonicalId(String id, String canonicalId);

    /**
     * The aliases of one group, without its canonical entry.
     *
     * @param canonicalId the canonical entry's id
     * @return the aliases pointing at it, whatever their status. Never null, possibly empty
     */
    List<E> findByCanonicalId(String canonicalId);
}
