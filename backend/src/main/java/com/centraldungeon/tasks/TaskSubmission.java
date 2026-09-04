package com.centraldungeon.tasks;

import com.centraldungeon.common.model.BaseEntity;
import com.centraldungeon.users.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * One answer somebody handed in to a task. Table {@code task_submissions}.
 *
 * <p><b>Answers accumulate; they never replace each other</b> (#76). Handing in a second version
 * inserts a second row, and the first one stays exactly where it was. There is deliberately no
 * {@code update}: deciding which of three character sheets is "the" one is a judgement the system
 * cannot make, and overwriting would be the system making it silently. The master reads them in
 * order and knows which is the latest.
 *
 * <p>For the same reason there is no review: no {@code reviewed_by}, no accept, no reject (#76).
 * Whether an answer is any good is between the people involved.
 *
 * <p>The files travel in {@link SubmissionFile}, linked and never copied (#65, #79) - the character
 * sheet somebody already uploaded is attached again, not stored again.
 */
@Entity
@Table(name = "task_submissions")
public class TaskSubmission extends BaseEntity {

    /** The task being answered. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private TableTask task;

    /** Who handed it in. Always the actor of the token, never an id from a URL (#121). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** The written answer, as sanitized rich text (#62). Null when the answer was files only. */
    @Column(columnDefinition = "LONGTEXT")
    private @Nullable String content;

    /** Handed in, or merely created. Two values only, after #76 removed the review flow. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SubmissionStatus status = SubmissionStatus.Submitted;

    /** When it was handed in, in UTC (#22). Null only on a row that was never submitted. */
    @Column(name = "submitted_at")
    private @Nullable LocalDateTime submittedAt;

    /** Soft delete (#25). Null for a live row. */
    @Column(name = "deleted_at")
    private @Nullable LocalDateTime deletedAt;

    /** Required by JPA. */
    protected TaskSubmission() {
    }

    /**
     * Hands in an answer, stamped as submitted at the moment it is built.
     *
     * @param task    the task being answered
     * @param user    who is answering - the actor of the token (#121)
     * @param content the written answer, already sanitized (#62), or null when only files were sent
     */
    public TaskSubmission(TableTask task, User user, @Nullable String content) {
        this.task = task;
        this.user = user;
        this.content = content;
        this.status = SubmissionStatus.Submitted;
        this.submittedAt = LocalDateTime.now();
    }

    /**
     * Returns the task being answered.
     *
     * @return the task, never null on a persisted row
     */
    public TableTask getTask() {
        return task;
    }

    /**
     * Returns who handed it in.
     *
     * @return the submitter, never null on a persisted row
     */
    public User getUser() {
        return user;
    }

    /**
     * Returns the written answer.
     *
     * @return the sanitized rich text (#62), or null when the answer was files only
     */
    public @Nullable String getContent() {
        return content;
    }

    /**
     * Returns whether this answer was handed in.
     *
     * @return the status, never null
     */
    public SubmissionStatus getStatus() {
        return status;
    }

    /**
     * Returns when it was handed in.
     *
     * @return the instant in UTC (#22), or null on a row that was never submitted
     */
    public @Nullable LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    /**
     * Returns when the answer was marked gone.
     *
     * @return the timestamp of the logical delete, or null while it is live
     */
    public @Nullable LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    /**
     * Stamps or clears the logical delete (#25).
     *
     * @param deletedAt when it was marked gone, or null to bring it back
     */
    public void setDeletedAt(@Nullable LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}
