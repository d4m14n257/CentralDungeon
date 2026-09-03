package com.centraldungeon.catalogs;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Reads and writes the {@code table_systems} bridge table. */
public interface TableSystemRepository extends JpaRepository<TableSystem, TableSystemId> {

    /**
     * Counts, in one grouped query, how many tables use each of the given systems - so a page of
     * twenty values on /admin/catalogs costs one round trip instead of twenty.
     *
     * <p>Named parameters only, never positional (#124).
     *
     * @param valueIds the systems to count. A system nothing points at is absent from the result
     *                 rather than reported as zero
     * @return one row per system that has at least one live link
     */
    @Query("""
            select new com.centraldungeon.catalogs.CatalogUsageCount(link.id.systemId, count(link))
            from TableSystem link
            where link.id.systemId in :valueIds
              and link.status = com.centraldungeon.catalogs.TableCatalogLinkStatus.Used
            group by link.id.systemId
            """)
    List<CatalogUsageCount> countUsesByValueIds(@Param("valueIds") Collection<String> valueIds);
}
