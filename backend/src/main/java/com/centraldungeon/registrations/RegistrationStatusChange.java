package com.centraldungeon.registrations;

import com.centraldungeon.common.model.IdGenerator;
import com.centraldungeon.users.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * One step of an application's life that somebody decided: who moved it, from where to where, and
 * why. Table {@code registration_status_changes}.
 *
 * <p><b>It is the veto's memory</b> (#39). The veto itself is a state of {@code table_registrations}
 * and needs no table of its own (#29) - what needs one is the <em>change</em>, because a column
 * holding "why this person is vetoed" cannot say that the veto was lifted, by whom, or how many
 * times the same master has done it. #39 asks for exactly that to be visible.
 *
 * <p>It is also what makes lifting a veto possible at all: {@link #getFromStatus()} is where the
 * person was before, so unblocking puts them back there rather than guessing.
 *
 * <p>No {@link com.centraldungeon.common.model.BaseEntity}, and the same reasons
 * {@code TableStatusChange} gives: this table has no {@code updated_at} and no {@code deleted_at},
 * because a historical row is never edited and never deleted. Hence no setters.
 *
 * <p>One difference with {@code TableStatusChange}, and it is the one {@code V11} already made for
 * the two user trails: {@code justification} is <b>not</b> nullable. Every transition that lands
 * here denies or restores something to a person, so there is none that can honestly go without a
 * reason.
 */
@Entity
@Table(name = "registration_status_changes")
public class RegistrationStatusChange {

    /** UUID v7, assigned on persist - the same scheme as BaseEntity, which this one cannot extend. */
    @Id
    @Column(length = 64)
    private @Nullable String id;

    /** The application this step belongs to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registration_id", nullable = false)
    private TableRegistration registration;

    /** Where the application was before the change - and where lifting a veto puts the person back. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, length = 32)
    private TableRegistrationStatus fromStatus;

    /** Where it went. */
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 32)
    private TableRegistrationStatus toStatus;

    /** Who made the change: the table's {@code Primary} (#39). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by", nullable = false)
    private User changedBy;

    /** Why. Never blank - see the class note. */
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String justification;

    /** When the change happened. Stamped on persist; it is what orders the trail. */
    @Column(name = "created_at", nullable = false)
    private @Nullable LocalDateTime createdAt;

    /** Required by JPA. */
    protected RegistrationStatusChange() {
    }

    /**
     * Records one decided step of an application.
     *
     * @param registration  the application that moved
     * @param fromStatus    where it was
     * @param toStatus      where it went
     * @param changedBy     who moved it, always the actor from the token (#121)
     * @param justification why, never blank (#39)
     */
    public RegistrationStatusChange(
            TableRegistration registration,
            TableRegistrationStatus fromStatus,
            TableRegistrationStatus toStatus,
            User changedBy,
            String justification) {
        this.registration = registration;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedBy = changedBy;
        this.justification = justification;
    }

    /** Assigns the id and stamps {@code createdAt} on insert. Called by JPA, never by application code. */
    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = IdGenerator.newId();
        }
        createdAt = LocalDateTime.now();
    }

    /**
     * Returns the row's id.
     *
     * @return the id
     * @throws IllegalStateException if called before the row was persisted
     */
    public String getId() {
        if (id == null) {
            throw new IllegalStateException("Entity id is not assigned yet - it is set on persist");
        }
        return id;
    }

    /**
     * Returns the application this step belongs to.
     *
     * @return the application, lazily loaded
     */
    public TableRegistration getRegistration() {
        return registration;
    }

    /**
     * Returns where the application was before the change.
     *
     * @return the previous status, never null
     */
    public TableRegistrationStatus getFromStatus() {
        return fromStatus;
    }

    /**
     * Returns where the application went.
     *
     * @return the resulting status, never null
     */
    public TableRegistrationStatus getToStatus() {
        return toStatus;
    }

    /**
     * Returns who made the change.
     *
     * @return the actor, lazily loaded
     */
    public User getChangedBy() {
        return changedBy;
    }

    /**
     * Returns why the change was made.
     *
     * @return the justification, never blank
     */
    public String getJustification() {
        return justification;
    }

    /**
     * Returns when the change happened.
     *
     * @return the timestamp
     * @throws IllegalStateException if called before the row was persisted
     */
    public LocalDateTime getCreatedAt() {
        if (createdAt == null) {
            throw new IllegalStateException("Entity createdAt is not assigned yet - it is set on persist");
        }
        return createdAt;
    }
}
