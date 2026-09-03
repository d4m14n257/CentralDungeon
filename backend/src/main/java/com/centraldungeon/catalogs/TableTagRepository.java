package com.centraldungeon.catalogs;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Reads and writes the {@code table_tags} bridge table. */
public interface TableTagRepository extends JpaRepository<TableTag, TableTagId> {

    /**
     * Every link a table ever had to a tag, live or removed - what replacing a table's tags
     * starts from.
     *
     * <p>The removed ones are part of the answer: a link is marked and never dropped, so a master who
     * takes a tag off their table and puts it back is reviving a row rather than inserting a key
     * that still exists.
     *
     * @param gameTableId the table
     * @return all of its tag links, whatever their status
     */
    List<TableTag> findById_GameTableId(String gameTableId);

    /**
     * Counts, in one grouped query, how many tables use each of the given tags - so a page of twenty
     * values on /admin/catalogs costs one round trip instead of twenty.
     *
     * <p>Named parameters only, never positional (#124).
     *
     * @param valueIds the tags to count. A tag nothing points at is absent from the result rather
     *                 than reported as zero
     * @return one row per tag that has at least one live link
     */
    @Query("""
            select new com.centraldungeon.catalogs.CatalogUsageCount(link.id.tagId, count(link))
            from TableTag link
            where link.id.tagId in :valueIds
              and link.status = com.centraldungeon.catalogs.TableCatalogLinkStatus.Used
            group by link.id.tagId
            """)
    List<CatalogUsageCount> countUsesByValueIds(@Param("valueIds") Collection<String> valueIds);
}
