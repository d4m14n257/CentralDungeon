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
 * A file handed in with an answer. Table {@code submission_files}.
 *
 * <p><b>It links, it does not copy</b> (#65, #79): the character sheet a player uploaded months ago
 * is attached again here, and the file keeps living in their own history untouched. That reuse is the
 * cost lever the whole file feature was built around.
 *
 * <p><b>It lives in {@code tasks/} and not in {@code files/}</b>, which is where {@code TableFile}
 * sits. The difference is which side owns the link: a table and a file are two independent things
 * that get associated, while a file on a submission has no meaning apart from the submission - it is
 * part of that answer. So the aggregate that owns it keeps it.
 *
 * <p>No id and no {@code updated_at}: it is a bridge row keyed by the pair it joins.
 */
@Entity
@Table(name = "submission_files")
public class SubmissionFile {

    /** The pair (submission, file) this link joins. */
    @EmbeddedId
    private SubmissionFileId id;

    /** Whether the file is still part of the answer. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SubmissionFileStatus status = SubmissionFileStatus.Current;

    /** When the file was handed in. Stamped on persist; bridge rows have no {@code updated_at}. */
    @Column(name = "created_at", nullable = false)
    private @Nullable LocalDateTime createdAt;

    /** Soft delete (#25). The row itself is never dropped, and the bytes are never freed here (#66). */
    @Column(name = "deleted_at")
    private @Nullable LocalDateTime deletedAt;

    /** Required by JPA. */
    protected SubmissionFile() {
    }

    /**
     * Attaches a file to an answer, live from the start.
     *
     * @param submissionId the answer being handed in
     * @param fileId       the file being attached. It is not copied (#79)
     */
    public SubmissionFile(String submissionId, String fileId) {
        this.id = new SubmissionFileId(submissionId, fileId);
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
    public SubmissionFileId getId() {
        return id;
    }

    /**
     * Returns whether the file is still part of the answer.
     *
     * @return the link's status, never null
     */
    public SubmissionFileStatus getStatus() {
        return status;
    }

    /**
     * Attaches or detaches the file. Detaching never touches the file itself (#79).
     *
     * @param status the new status
     */
    public void setStatus(SubmissionFileStatus status) {
        this.status = status;
    }

    /**
     * Returns when the file was handed in.
     *
     * @return the creation timestamp, never null on a persisted row
     */
    public @Nullable LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Returns when the file was taken off the answer.
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
