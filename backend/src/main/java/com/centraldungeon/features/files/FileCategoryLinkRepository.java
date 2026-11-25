package com.centraldungeon.features.files;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Reads and writes the {@code file_categories} bridge table. */
public interface FileCategoryLinkRepository extends JpaRepository<FileCategoryLink, FileCategoryLinkId> {

    /**
     * Every category each of the given files belongs to, in one query for a whole page.
     *
     * <p>One round trip and never one per row, the same rule {@code TableFileRepository.findUsagesByFileIds} follows: a
     * page of twenty files in somebody's library costs one query, not twenty.
     *
     * <p>Named parameters only, never positional.
     *
     * @param fileIds the files to resolve categories for
     * @return one row per membership. A file in no category at all is simply absent
     */
    @Query("""
            select link from FileCategoryLink link
            where link.id.fileId in :fileIds
            """)
    List<FileCategoryLink> findByFileIds(@Param("fileIds") Collection<String> fileIds);

    /**
     * The ids of the files in a category, narrowed to what a given query already selected.
     *
     * @param fileIds  the candidate files
     * @param category the category to keep
     * @return the ids that belong to it
     */
    @Query("""
            select link.id.fileId from FileCategoryLink link
            where link.id.fileId in :fileIds
              and link.id.category = :category
            """)
    List<String> findFileIdsInCategory(
            @Param("fileIds") Collection<String> fileIds, @Param("category") FileCategory category);

    /**
     * Takes a file out of the given categories - the one write that removes a membership, and only for
     * the platform's library.
     *
     * <p>Memberships are add-only everywhere else: they record which flows a file was used in,
     * and that stays true. A file of the library is different: its category is what an admin declared it
     * is for, and correcting that while it is unpublished is correcting a declaration, not rewriting
     * history. Only the publishable categories are ever passed here, so a player-side membership the file
     * carries from before is never touched.
     *
     * <p>Named parameters only, never positional.
     *
     * @param fileId     the file
     * @param categories the categories to take it out of
     */
    @Modifying
    @Query("""
            delete from FileCategoryLink link
            where link.id.fileId = :fileId
              and link.id.category in :categories
            """)
    void deleteByFileIdAndCategories(
            @Param("fileId") String fileId, @Param("categories") Collection<FileCategory> categories);
}
