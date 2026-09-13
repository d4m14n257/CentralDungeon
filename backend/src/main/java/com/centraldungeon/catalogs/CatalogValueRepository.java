package com.centraldungeon.catalogs;

import java.util.Collection;
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

    /**
     * The canonical entries of several groups at once, in one status.
     *
     * <p>Half of what {@link AbstractCatalogService#resolveGroupIdsByName} needs: given the roots a
     * search landed on, this brings back the roots themselves. The other half is
     * {@link #findByStatusAndCanonicalIdIn}, and together they are the whole group - depth is always
     * 1 (#59), so there is no third query.
     *
     * @param status  the status to keep. Always {@code Accepted} today: a value nobody accepted does
     *                not filter (#57)
     * @param ids     the canonical entries to bring back
     * @return the matching rows, never null and possibly empty
     */
    List<E> findByStatusAndIdIn(CatalogStatus status, Collection<String> ids);

    /**
     * The aliases of several groups at once, in one status.
     *
     * @param status       the status to keep
     * @param canonicalIds the canonical entries whose aliases are wanted
     * @return the matching rows, never null and possibly empty
     */
    List<E> findByStatusAndCanonicalIdIn(CatalogStatus status, Collection<String> canonicalIds);
}
