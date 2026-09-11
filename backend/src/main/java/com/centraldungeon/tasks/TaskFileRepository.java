package com.centraldungeon.tasks;

import com.centraldungeon.files.FileUsage;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Reads and writes the {@code task_files} bridge table - the master's half of a request (#63). */
public interface TaskFileRepository extends JpaRepository<TaskFile, TaskFileId> {

    /**
     * The live attachments of a set of requests, in one round trip - a board with ten requests is one
     * query and not ten.
     *
     * @param taskIds the requests
     * @param status  the status to include
     * @return their file links, in no particular order; the service groups them by request
     */
    List<TaskFile> findById_TaskIdInAndStatus(Collection<String> taskIds, TaskFileStatus status);

    /**
     * Every link a request ever had, live or taken off.
     *
     * <p>The ones taken off are part of the answer: the pair is the primary key, so re-attaching a
     * file that was removed has to revive its row rather than insert a key that already exists - the
     * same trap {@code TableFileStatus} documents.
     *
     * @param taskId the request
     * @return all of its file links, whatever their status
     */
    List<TaskFile> findById_TaskId(String taskId);

    /**
     * Which tables can reach a file through a request that attaches it.
     *
     * <p>The sixth way a file becomes readable, and the reason it has to exist: a master attaches a
     * blank form to a request, the request reaches its players, and those players have to be able to
     * open the form. Without this they would see the row on the request and get a 404 opening it -
     * the exact mismatch #206 had to fix once for the files a table shares and #211 for submissions.
     *
     * <p>Named parameters only, never positional (#124).
     *
     * @param fileId the file somebody is trying to open
     * @return the ids of the tables whose requests attach it. Empty when no request does
     */
    @Query("""
            select task.gameTable.id
            from TaskFile link, TableTask task
            where link.id.fileId = :fileId
              and link.status = com.centraldungeon.tasks.TaskFileStatus.Current
              and task.id = link.id.taskId
            """)
    List<String> findTableIdsByRequestedFileId(@Param("fileId") String fileId);

    /**
     * The same reachability for a whole page of files, carrying the table's name - the third source
     * of the uses somebody sees on their own library (#232).
     *
     * <p>It answers with {@link FileUsage}, a type of the {@code files} feature, for the reason
     * {@code SubmissionFileRepository} gives: the read rule already crosses this way, and a second
     * projection here would only be converted into the first.
     *
     * @param fileIds the files to resolve uses for
     * @return one row per live attachment, absent for a file no request attaches
     */
    @Query("""
            select new com.centraldungeon.files.FileUsage(
                link.id.fileId,
                com.centraldungeon.files.FileCategory.MasterRequest,
                task.gameTable.id,
                task.gameTable.name)
            from TaskFile link, TableTask task
            where link.id.fileId in :fileIds
              and link.status = com.centraldungeon.tasks.TaskFileStatus.Current
              and task.id = link.id.taskId
            """)
    List<FileUsage> findUsagesByFileIds(@Param("fileIds") Collection<String> fileIds);

    /**
     * The blanks of a whole board, joined to their files, in one query.
     *
     * <p>The join is the point: the link rows alone carry ids, and a screen needs names and sizes, so
     * resolving those separately would be a second round trip per request.
     *
     * <p>Named parameters only, never positional (#124).
     *
     * @param taskIds the requests
     * @return one row per live blank, in no particular order; the service groups them by request
     */
    @Query("""
            select new com.centraldungeon.tasks.TaskFileRow(
                link.id.taskId, file.id, file.name, file.mimeType, file.sizeBytes)
            from TaskFile link, com.centraldungeon.files.StoredFile file
            where link.id.taskId in :taskIds
              and link.status = com.centraldungeon.tasks.TaskFileStatus.Current
              and file.id = link.id.fileId
              and file.status = com.centraldungeon.files.FileStatus.Current
            """)
    List<TaskFileRow> findBlanksByTaskIds(@Param("taskIds") Collection<String> taskIds);
}
