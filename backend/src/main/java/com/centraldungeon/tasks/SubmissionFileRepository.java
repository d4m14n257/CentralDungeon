package com.centraldungeon.tasks;

import com.centraldungeon.files.FileUsage;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Reads and writes the {@code submission_files} bridge table. */
public interface SubmissionFileRepository extends JpaRepository<SubmissionFile, SubmissionFileId> {

    /**
     * The live attachments of a set of answers, in one round trip - a task with ten answers is one
     * query and not ten.
     *
     * @param submissionIds the answers
     * @param status        the status to include
     * @return their file links, in no particular order; the service groups them by answer
     */
    List<SubmissionFile> findById_SubmissionIdInAndStatus(
            Collection<String> submissionIds, SubmissionFileStatus status);

    /**
     * Which tables can reach a file through an answer handed in to one of their tasks.
     *
     * <p>This exists for one caller: the read rule in {@code FileService}. A master has to be able to
     * open what their own players handed in, and without this they would see the row in the answer
     * and get a 404 opening it - exactly the mismatch #206 had to fix for the files a table shares.
     *
     * <p>It answers with table ids and not with a yes/no, so the membership question stays where it
     * belongs: a repository resolves reachability, a service decides authorization (#17, #121, #135).
     *
     * <p>Named parameters only, never positional (#124).
     *
     * @param fileId the file somebody is trying to open
     * @return the ids of the tables whose tasks it was handed in to. Empty when it was never
     *         submitted anywhere
     */
    @Query("""
            select task.gameTable.id
            from SubmissionFile link, TaskSubmission submission, TableTask task
            where link.id.fileId = :fileId
              and link.status = com.centraldungeon.tasks.SubmissionFileStatus.Current
              and submission.id = link.id.submissionId
              and submission.deletedAt is null
              and task.id = submission.task.id
            """)
    List<String> findTableIdsBySubmittedFileId(@Param("fileId") String fileId);

    /**
     * The same reachability as {@link #findTableIdsBySubmittedFileId}, but for a whole page of files
     * and carrying the table's name - the second source of the uses an owner sees on their own list
     * (#232).
     *
     * <p>It answers with {@link FileUsage}, a type of the {@code files} feature, which is the one
     * import that direction. The alternative is a second projection here that {@code FileService}
     * would immediately convert into the first, and the read rule already crosses this way for the
     * same reason (#206): whether a submitted file is reachable is a question about submissions, and
     * splitting it across two features is what let the two answers drift apart once already.
     *
     * <p>Named parameters only, never positional (#124).
     *
     * @param fileIds the files to resolve uses for
     * @return one row per live attachment on a live answer, absent for a file never submitted
     */
    @Query("""
            select new com.centraldungeon.files.FileUsage(
                link.id.fileId,
                com.centraldungeon.files.FileCategory.PlayerSubmission,
                task.gameTable.id,
                task.gameTable.name)
            from SubmissionFile link, TaskSubmission submission, TableTask task
            where link.id.fileId in :fileIds
              and link.status = com.centraldungeon.tasks.SubmissionFileStatus.Current
              and submission.id = link.id.submissionId
              and submission.deletedAt is null
              and task.id = submission.task.id
            """)
    List<FileUsage> findUsagesByFileIds(@Param("fileIds") Collection<String> fileIds);
}
