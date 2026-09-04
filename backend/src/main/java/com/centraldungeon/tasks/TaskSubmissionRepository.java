package com.centraldungeon.tasks;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Reads and writes the {@code task_submissions} table. */
public interface TaskSubmissionRepository extends JpaRepository<TaskSubmission, String> {

    /**
     * Every live answer to a task, oldest first.
     *
     * <p>Oldest first and not newest first on purpose: answers accumulate (#76), so the list is a
     * history and a history reads forwards. The last row is the latest version.
     *
     * @param taskId the task
     * @return its answers, oldest first
     */
    List<TaskSubmission> findByTask_IdAndDeletedAtIsNullOrderByCreatedAtAsc(String taskId);

    /**
     * One person's own answers to a task - what they see when they look at what they handed in.
     *
     * @param taskId the task
     * @param userId the submitter, always the actor of the token (#121)
     * @return their answers, oldest first
     */
    List<TaskSubmission> findByTask_IdAndUser_IdAndDeletedAtIsNullOrderByCreatedAtAsc(String taskId, String userId);

    /**
     * Counts, in one grouped query, the answers of every given task - so a board of fifteen tasks
     * costs one round trip instead of fifteen.
     *
     * <p>Named parameters only, never positional (#124).
     *
     * @param taskIds the tasks to count. A task nobody answered is absent from the result rather than
     *                reported as zero
     * @return one row per task that has at least one live answer
     */
    @Query("""
            select new com.centraldungeon.tasks.TaskSubmissionCount(
                       submission.task.id, count(submission), count(distinct submission.user.id))
            from TaskSubmission submission
            where submission.task.id in :taskIds
              and submission.deletedAt is null
            group by submission.task.id
            """)
    List<TaskSubmissionCount> countByTaskIds(@Param("taskIds") Collection<String> taskIds);
}
