package com.centraldungeon.files;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Reads and writes the {@code file_categories} bridge table (#233). */
public interface FileCategoryLinkRepository extends JpaRepository<FileCategoryLink, FileCategoryLinkId> {

    /**
     * Every cajón each of the given files belongs to, in one query for a whole page.
     *
     * <p>One round trip and never one per row, the same rule {@code countUsesByFileIds} follows: a
     * page of twenty files in somebody's library costs one query, not twenty.
     *
     * <p>Named parameters only, never positional (#124).
     *
     * @param fileIds the files to resolve cajones for
     * @return one row per membership. A file in no cajón at all is simply absent
     */
    @Query("""
            select link from FileCategoryLink link
            where link.id.fileId in :fileIds
            """)
    List<FileCategoryLink> findByFileIds(@Param("fileIds") Collection<String> fileIds);

    /**
     * The ids of the files in a cajón, narrowed to what a given query already selected.
     *
     * @param fileIds  the candidate files
     * @param category the cajón to keep
     * @return the ids that belong to it
     */
    @Query("""
            select link.id.fileId from FileCategoryLink link
            where link.id.fileId in :fileIds
              and link.id.category = :category
            """)
    List<String> findFileIdsInCategory(
            @Param("fileIds") Collection<String> fileIds, @Param("category") FileCategory category);
}
