package com.centraldungeon.tasks;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Reads and writes the {@code table_tasks} table. */
public interface TableTaskRepository extends JpaRepository<TableTask, String> {

    /**
     * Every task a table ever asked, minus the ones marked gone - the master's Peticiones tab.
     *
     * <p>A list and not a page: it is bounded by what one table's masters chose to ask and is read as
     * one board, the same criterion the calendar and the attachments use.
     *
     * @param gameTableId the table
     * @param status      the status to leave out, always {@link TaskStatus#Deleted} (#25)
     * @return its tasks, oldest first - the order they were published, which is the order somebody
     *         who published them remembers
     */
    List<TableTask> findByGameTable_IdAndStatusNotOrderByCreatedAtAsc(String gameTableId, TaskStatus status);

    /**
     * A table's tasks in the given audiences and status, for resolving what applies to one reader.
     *
     * <p>The {@link TaskAudience#Single} ones are <b>not</b> filtered by target here: the service
     * narrows them to the actor, because a repository that took an actor would be a repository
     * deciding an authorization question.
     *
     * @param gameTableId the table
     * @param audiences   the audiences to include
     * @param status      the status to include, always {@link TaskStatus#Open} for a reader
     * @return the matching tasks, oldest first
     */
    List<TableTask> findByGameTable_IdAndAudienceInAndStatusOrderByCreatedAtAsc(
            String gameTableId, Collection<TaskAudience> audiences, TaskStatus status);
}
