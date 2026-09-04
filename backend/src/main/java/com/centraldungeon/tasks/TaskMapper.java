package com.centraldungeon.tasks;

import com.centraldungeon.tasks.dto.ApplicableTaskResponse;
import com.centraldungeon.tasks.dto.SubmittedFileResponse;
import com.centraldungeon.tasks.dto.TaskRecipientResponse;
import com.centraldungeon.tasks.dto.TaskResponse;
import com.centraldungeon.tasks.dto.TaskSubmissionResponse;
import com.centraldungeon.users.User;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Turns tasks, answers and recipients into the shapes that cross HTTP. Wired as a @Bean in
 * common/config/MapperConfig.java, like every other mapper - see that class for why.
 *
 * <p>Mostly {@code default} methods, the same way {@code GameTableMapper} writes its harder ones:
 * almost every field here is computed rather than copied - a nullable session resolved to its
 * sequence number, an enum to its name, counts the service had to query for - and a wall of
 * {@code @Mapping(expression = ...)} would say the same thing in a language that is harder to read.
 *
 * <p>The rich text arrives as a parameter and is not read off the entity: it is sanitized on the way
 * out as well as on the way in (#62), and passing the cleaned string keeps a read from turning into a
 * write on a managed entity - the same reason {@code GameTableMapper.toDetail} takes its three.
 */
@Mapper
public interface TaskMapper {

    /**
     * Somebody a task was addressed to, for the roster of who has not answered.
     *
     * @param user the person
     * @return their id and the name to show
     */
    @Mapping(target = "userId", source = "id")
    @Mapping(target = "userName", source = "discordUsername")
    TaskRecipientResponse toRecipient(User user);

    /**
     * A task as the people running the table see it.
     *
     * @param task            the task
     * @param description     its detail, already sanitized (#62)
     * @param submissionCount how many answers it has - answers accumulate, so this can exceed the
     *                        number of people (#76)
     * @param respondentCount how many different people answered
     * @param recipientCount  how many were asked, resolved from the audience at read time
     * @return the task as the Peticiones tab shows it
     */
    default TaskResponse toResponse(
            TableTask task, @Nullable String description, int submissionCount, int respondentCount, int recipientCount) {
        TableSessionRef session = TableSessionRef.of(task);
        User target = task.getTargetUser();
        return new TaskResponse(
                task.getId(),
                task.getGameTable().getId(),
                session.id(),
                session.sequenceNumber(),
                task.getAudience().name(),
                target == null ? null : target.getId(),
                target == null ? null : target.getDiscordUsername(),
                task.getTitle(),
                description,
                task.isAcceptsText(),
                task.isAcceptsFiles(),
                task.isMandatory(),
                task.getDueAt(),
                task.getStatus().name(),
                submissionCount,
                respondentCount,
                recipientCount,
                task.getCreatedAt());
    }

    /**
     * A task as the person being asked sees it.
     *
     * @param task              the task
     * @param description       its detail, already sanitized (#62)
     * @param canSubmit         whether this reader may answer right now - in the audience, and the
     *                          task still open. It is what lets the button explain itself
     * @param mySubmissionCount how many times this reader already answered (#76)
     * @return the task as {@code /tables/:id} and {@code /my/tables/:id} show it
     */
    default ApplicableTaskResponse toApplicable(
            TableTask task, @Nullable String description, boolean canSubmit, int mySubmissionCount) {
        TableSessionRef session = TableSessionRef.of(task);
        return new ApplicableTaskResponse(
                task.getId(),
                task.getAudience().name(),
                session.id(),
                session.sequenceNumber(),
                task.getTitle(),
                description,
                task.isAcceptsText(),
                task.isAcceptsFiles(),
                task.isMandatory(),
                task.getDueAt(),
                canSubmit,
                mySubmissionCount,
                task.getCreatedAt());
    }

    /**
     * One answer, with the files handed in alongside it.
     *
     * @param submission the answer
     * @param content    its written half, already sanitized (#62), or null when it was files only
     * @param files      the files handed in with it, resolved by the service
     * @return the answer as whoever may read it sees it
     */
    default TaskSubmissionResponse toSubmissionResponse(
            TaskSubmission submission, @Nullable String content, List<SubmittedFileResponse> files) {
        return new TaskSubmissionResponse(
                submission.getId(),
                submission.getTask().getId(),
                submission.getUser().getId(),
                submission.getUser().getDiscordUsername(),
                content,
                files,
                submission.getSubmittedAt());
    }

    /**
     * The session a task points at, flattened to the two values a response carries.
     *
     * <p>It exists so the null case is written once instead of four times: an untied task is the
     * common one (#63), and repeating {@code task.getTableSession() == null ? null : ...} per field is
     * how one of them eventually gets it wrong.
     *
     * @param id             the session's identifier, or null when the task is not tied to one
     * @param sequenceNumber which session of the run it is, from 1. Null together with the id
     */
    record TableSessionRef(@Nullable String id, @Nullable Integer sequenceNumber) {

        /**
         * Reads the session off a task.
         *
         * @param task the task
         * @return its session flattened, or a pair of nulls when it is not tied to one
         */
        static TableSessionRef of(TableTask task) {
            return task.getTableSession() == null
                    ? new TableSessionRef(null, null)
                    : new TableSessionRef(task.getTableSession().getId(), task.getTableSession().getSequenceNumber());
        }
    }
}
