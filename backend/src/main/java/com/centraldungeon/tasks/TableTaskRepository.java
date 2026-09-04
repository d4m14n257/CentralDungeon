package com.centraldungeon.tasks;

import java.time.LocalDateTime;
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

    /**
     * Tasks whose deadline went by while they were still open, across several tables at once - the
     * second probe of the master dashboard (#136).
     *
     * <p>Missing a deadline neither blocks nor expels anybody (#70): it becomes something the
     * master can see and act on, which is the whole of what the rule asks for.
     *
     * @param gameTableIds the tables somebody runs
     * @param status       the status that means still open, always {@link TaskStatus#Open}
     * @param before       the cutoff, in UTC (#22) - in practice now
     * @return the overdue tasks, the longest overdue first
     */
    List<TableTask> findByGameTable_IdInAndStatusAndDueAtBeforeOrderByDueAtAsc(
            Collection<String> gameTableIds, TaskStatus status, LocalDateTime before);
}
