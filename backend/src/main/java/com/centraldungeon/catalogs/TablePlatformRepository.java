package com.centraldungeon.catalogs;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Reads and writes the {@code table_platforms} bridge table. */
public interface TablePlatformRepository extends JpaRepository<TablePlatform, TablePlatformId> {

    /**
     * Counts, in one grouped query, how many tables use each of the given platforms - so a page of
     * twenty values on /admin/catalogs costs one round trip instead of twenty.
     *
     * <p>Named parameters only, never positional (#124).
     *
     * @param valueIds the platforms to count. A platform nothing points at is absent from the result
     *                 rather than reported as zero
     * @return one row per platform that has at least one live link
     */
    @Query("""
            select new com.centraldungeon.catalogs.CatalogUsageCount(link.id.platformId, count(link))
            from TablePlatform link
            where link.id.platformId in :valueIds
              and link.status = com.centraldungeon.catalogs.TableCatalogLinkStatus.Used
            group by link.id.platformId
            """)
    List<CatalogUsageCount> countUsesByValueIds(@Param("valueIds") Collection<String> valueIds);
}
