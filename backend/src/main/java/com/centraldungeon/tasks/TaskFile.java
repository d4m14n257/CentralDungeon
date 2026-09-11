package com.centraldungeon.tasks;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * A file attached to a request by the master who wrote it. Table {@code task_files}.
 *
 * <p><b>The half of a request the model never had.</b> {@code table_tasks.accepts_files} said whether
 * an <em>answer</em> could carry files; nothing said the asking could. So a master could write "send
 * me your sheet on this form" and had no way to attach the form - the blank the whole request is
 * about. Everyone had to describe it in prose and hope.
 *
 * <p>It links and never copies (#65, #79), which is what lets the community's published form be
 * attached by every master who wants it instead of re-uploaded once per table. It is also what fills
 * the {@link com.centraldungeon.files.FileCategory#MasterRequest} cajón with anything other than what
 * an admin published (#233).
 *
 * <p>It lives in {@code tasks/} rather than in {@code files/}, for the reason {@code SubmissionFile}
 * gives: a file on a request has no meaning apart from the request, so the aggregate that owns the
 * request owns the link. {@code TableFile} sits on the other side because a table and a file are two
 * independent things that get associated.
 *
 * <p>No id and no {@code updated_at}: a bridge row keyed by the pair it joins.
 */
@Entity
@Table(name = "task_files")
public class TaskFile {

    /** The pair (request, file) this link joins. */
    @EmbeddedId
    private TaskFileId id;

    /** Whether the file is still part of the request. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskFileStatus status = TaskFileStatus.Current;

    /** When the file was attached. Stamped on persist; bridge rows have no {@code updated_at}. */
    @Column(name = "created_at", nullable = false)
    private @Nullable LocalDateTime createdAt;

    /** Soft delete (#25). The row is never dropped and the bytes are never freed here (#66). */
    @Column(name = "deleted_at")
    private @Nullable LocalDateTime deletedAt;

    /** Required by JPA. */
    protected TaskFile() {
    }

    /**
     * Attaches a file to a request, live from the start.
     *
     * @param taskId the request being written
     * @param fileId the file being attached. It is not copied (#79)
     */
    public TaskFile(String taskId, String fileId) {
        this.id = new TaskFileId(taskId, fileId);
    }

    /** Stamps {@code createdAt} on insert. Called by JPA, never by application code. */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * Returns the pair this link joins.
     *
     * @return the composite key, never null on a persisted row
     */
    public TaskFileId getId() {
        return id;
    }

    /**
     * Returns whether the file is still part of the request.
     *
     * @return the link's status, never null
     */
    public TaskFileStatus getStatus() {
        return status;
    }

    /**
     * Attaches or takes off the file. Taking it off never touches the file itself (#79).
     *
     * @param status the new status
     */
    public void setStatus(TaskFileStatus status) {
        this.status = status;
    }

    /**
     * Returns when the file was attached.
     *
     * @return the creation timestamp, never null on a persisted row
     */
    public @Nullable LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Returns when the file was taken off the request.
     *
     * @return the timestamp of the logical delete, or null while it is attached
     */
    public @Nullable LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    /**
     * Stamps or clears the logical delete of the link.
     *
     * @param deletedAt when it was taken off, or null to bring it back
     */
    public void setDeletedAt(@Nullable LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}
