package com.centraldungeon.registrations;

import com.centraldungeon.common.model.IdGenerator;
import com.centraldungeon.users.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * Why an application was turned down. Table {@code registration_rejections}.
 *
 * <p>A row of its own instead of a column on the registration, because a rejection has an author and
 * a time that the registration itself does not - and because a rejection with no reason attached is
 * the one outcome the applicant can learn nothing from.
 *
 * <p>No BaseEntity: this table has no created_at/updated_at/deleted_at - rejected_at is its own
 * timestamp, and a rejection is never edited or deleted.
 */
@Entity
@Table(name = "registration_rejections")
public class RegistrationRejection {

    /** UUID v7, assigned on persist - the same scheme as BaseEntity, which this one cannot extend. */
    @Id
    @Column(length = 64)
    private @Nullable String id;

    /** The application that was turned down. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registration_id", nullable = false)
    private TableRegistration registration;

    /** The reason. It reaches the applicant in their notification, so it is written for them. */
    @Column(columnDefinition = "LONGTEXT")
    private @Nullable String description;

    /** When it was rejected. Stamped on persist; this table's only timestamp. */
    @Column(name = "rejected_at", nullable = false)
    private @Nullable LocalDateTime rejectedAt;

    /** Who rejected it. Null means an automatic rejection because the table filled up (#34). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rejected_by")
    private @Nullable User rejectedBy;

    /** Required by JPA. */
    protected RegistrationRejection() {
    }

    /**
     * Records a rejection.
     *
     * @param registration the application being turned down
     * @param description  the reason, written for the applicant
     * @param rejectedBy   the master who rejected it, or null when the system did because the table
     *                     filled up (#34)
     */
    public RegistrationRejection(TableRegistration registration, String description, @Nullable User rejectedBy) {
        this.registration = registration;
        this.description = description;
        this.rejectedBy = rejectedBy;
    }

    /** Assigns the id and stamps {@code rejectedAt} on insert. Called by JPA, never by application code. */
    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = IdGenerator.newId();
        }
        rejectedAt = LocalDateTime.now();
    }

    /**
     * Returns the application that was turned down.
     *
     * @return the registration, lazily loaded
     */
    public TableRegistration getRegistration() {
        return registration;
    }

    /**
     * Returns the reason.
     *
     * @return the justification, or null on an old row that has none
     */
    public @Nullable String getDescription() {
        return description;
    }

    /**
     * Returns who rejected it.
     *
     * @return the master, or null when the system did because the table filled up (#34)
     */
    public @Nullable User getRejectedBy() {
        return rejectedBy;
    }
}
