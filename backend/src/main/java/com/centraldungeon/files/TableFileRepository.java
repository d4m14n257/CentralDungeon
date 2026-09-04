package com.centraldungeon.files;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Reads and writes the {@code table_files} bridge table. */
public interface TableFileRepository extends JpaRepository<TableFile, TableFileId> {

    /**
     * A table's live attachments, which is what the master's Archivos tab shows.
     *
     * @param gameTableId the table
     * @param status      the status to include
     * @return its attachments, in no particular order - the service sorts them
     */
    List<TableFile> findById_GameTableIdAndStatus(String gameTableId, TableFileStatus status);

    /**
     * Every link a table ever had, live or detached.
     *
     * <p>The detached ones are part of the answer: the pair is the primary key, so re-attaching a
     * file that was removed has to revive its row rather than insert a key that already exists - the
     * same trap the agenda documents in {@code TableScheduleStatus}.
     *
     * @param gameTableId the table
     * @return all of its file links, whatever their status
     */
    List<TableFile> findById_GameTableId(String gameTableId);

    /**
     * Which tables currently hold a given file.
     *
     * <p>This is what answers "may this person download this" without asking about roles (#17, #121):
     * a file is reachable through the tables it is attached to, and whether the reader belongs to any
     * of them is a membership question.
     *
     * @param fileId the file
     * @param status the status to include
     * @return its live links, one per table still showing it
     */
    List<TableFile> findById_FileIdAndStatus(String fileId, TableFileStatus status);

    /**
     * Counts, in one grouped query, how many tables hold each of the given files - so a page of
     * twenty files on /admin/files costs one round trip instead of twenty.
     *
     * <p>Named parameters only, never positional (#124).
     *
     * @param fileIds the files to count. A file nothing points at is absent from the result rather
     *                than reported as zero
     * @return one row per file that has at least one live link
     */
    @Query("""
            select new com.centraldungeon.files.FileUsageCount(link.id.fileId, count(link))
            from TableFile link
            where link.id.fileId in :fileIds
              and link.status = com.centraldungeon.files.TableFileStatus.Current
            group by link.id.fileId
            """)
    List<FileUsageCount> countUsesByFileIds(@Param("fileIds") Collection<String> fileIds);
}
