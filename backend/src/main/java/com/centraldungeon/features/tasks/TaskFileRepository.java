package com.centraldungeon.features.tasks;

import com.centraldungeon.features.files.FileUsage;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Reads and writes the {@code task_files} bridge table - the master's half of a request (ADR-15 §1). */
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
     * The live attachments of every request a table ever wrote - what deleting the table detaches.
     *
     * <p>Named parameters only, never positional.
     *
     * @param gameTableId the table
     * @return its requests' file links that are still attached
     */
    @Query("""
            select link
            from TaskFile link, TableTask task
            where task.id = link.id.taskId
              and task.gameTable.id = :gameTableId
              and link.status = com.centraldungeon.features.tasks.TaskFileStatus.Current
            """)
    List<TaskFile> findLiveByGameTableId(@Param("gameTableId") String gameTableId);

    /**
     * The requests that attach a file as their blank.
     *
     * <p>The way a request's blank becomes readable, and the reason it has to exist: a master attaches a
     * blank form to a request, the request reaches the people it is addressed to, and those people
     * have to be able to open the form. Without this they would see the row on the request and get a
     * 404 opening it. The requests and not only their tables, because who a request reaches depends on
     * its audience and its target, not just on belonging to the table.
     *
     * <p>Named parameters only, never positional.
     *
     * @param fileId the file somebody is trying to open
     * @return the requests whose live blanks include it. Empty when no request does
     */
    @Query("""
            select task
            from TaskFile link, TableTask task
            where link.id.fileId = :fileId
              and link.status = com.centraldungeon.features.tasks.TaskFileStatus.Current
              and task.id = link.id.taskId
            """)
    List<TableTask> findTasksByRequestedFileId(@Param("fileId") String fileId);

    /**
     * The same reachability for a whole page of files, carrying the table's name - the third source
     * of the uses somebody sees on their own library (ADR-14 §3).
     *
     * <p>It answers with {@link FileUsage}, a type of the {@code files} feature, for the reason
     * {@code SubmissionFileRepository} gives: the read rule already crosses this way, and a second
     * projection here would only be converted into the first.
     *
     * @param fileIds the files to resolve uses for
     * @return one row per live attachment, absent for a file no request attaches
     */
    @Query("""
            select new com.centraldungeon.features.files.FileUsage(
                link.id.fileId,
                com.centraldungeon.features.files.FileCategory.MasterRequest,
                task.gameTable.id,
                task.gameTable.name)
            from TaskFile link, TableTask task
            where link.id.fileId in :fileIds
              and link.status = com.centraldungeon.features.tasks.TaskFileStatus.Current
              and task.id = link.id.taskId
            """)
    List<FileUsage> findUsagesByFileIds(@Param("fileIds") Collection<String> fileIds);

    /**
     * The blanks of a whole board, joined to their files, in one query.
     *
     * <p>The join is the point: the link rows alone carry ids, and a screen needs names and sizes, so
     * resolving those separately would be a second round trip per request.
     *
     * <p>Named parameters only, never positional (ADR-03 §2).
     *
     * @param taskIds the requests
     * @return one row per live blank, in no particular order; the service groups them by request
     */
    @Query("""
            select new com.centraldungeon.features.tasks.TaskFileRow(
                link.id.taskId, file.id, file.name, file.mimeType, file.sizeBytes)
            from TaskFile link, com.centraldungeon.features.files.StoredFile file
            where link.id.taskId in :taskIds
              and link.status = com.centraldungeon.features.tasks.TaskFileStatus.Current
              and file.id = link.id.fileId
              and file.status = com.centraldungeon.features.files.FileStatus.Current
            """)
    List<TaskFileRow> findBlanksByTaskIds(@Param("taskIds") Collection<String> taskIds);
}
