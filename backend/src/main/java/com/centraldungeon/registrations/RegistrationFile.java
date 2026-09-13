package com.centraldungeon.registrations;

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
 * A file attached to an application. Table {@code registration_files}.
 *
 * <p><b>It links, it does not copy</b> (#65, #79): the character sheet a player uploaded for a
 * previous table is attached again here, and the file keeps living in their own history untouched.
 * That reuse is the cost lever the whole file feature was built around.
 *
 * <p><b>It lives in {@code registrations/} and not in {@code files/}</b>, for the same reason
 * {@code SubmissionFile} lives in {@code tasks/}: a file on an application has no meaning apart from
 * that application - it is part of it. So the aggregate that owns the application owns the link.
 *
 * <p><b>Withdrawing an application does not touch these rows</b> (#247, deliberately): the row is
 * the record that a sheet was sent, and withdrawing does not undo that it was. What treats a
 * withdrawn application's files as no longer in use is the read side - see
 * {@link RegistrationFileRepository#findUsagesByFileIds} - never a cascade from here.
 *
 * <p>No id and no {@code updated_at}: it is a bridge row keyed by the pair it joins.
 */
@Entity
@Table(name = "registration_files")
public class RegistrationFile {

    /** The pair (application, file) this link joins. */
    @EmbeddedId
    private RegistrationFileId id;

    /** Whether the file is still part of the application. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RegistrationFileStatus status = RegistrationFileStatus.Current;

    /** When the file was attached. Stamped on persist; bridge rows have no {@code updated_at}. */
    @Column(name = "created_at", nullable = false)
    private @Nullable LocalDateTime createdAt;

    /** Soft delete (#25). The row itself is never dropped, and the bytes are never freed here (#66). */
    @Column(name = "deleted_at")
    private @Nullable LocalDateTime deletedAt;

    /** Required by JPA. */
    protected RegistrationFile() {
    }

    /**
     * Attaches a file to an application, live from the start.
     *
     * @param registrationId the application being submitted
     * @param fileId          the file being attached. It is not copied (#79)
     */
    public RegistrationFile(String registrationId, String fileId) {
        this.id = new RegistrationFileId(registrationId, fileId);
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
    public RegistrationFileId getId() {
        return id;
    }

    /**
     * Returns whether the file is still part of the application.
     *
     * @return the link's status, never null
     */
    public RegistrationFileStatus getStatus() {
        return status;
    }

    /**
     * Attaches or detaches the file. Detaching never touches the file itself (#79).
     *
     * @param status the new status
     */
    public void setStatus(RegistrationFileStatus status) {
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
     * Returns when the file was taken off the application.
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
