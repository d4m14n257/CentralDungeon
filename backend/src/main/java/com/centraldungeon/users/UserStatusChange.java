package com.centraldungeon.users;

import com.centraldungeon.common.model.IdGenerator;
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
 * One change to an account's status: who moved it, from where to where, and why. Table
 * {@code user_status_changes}.
 *
 * <p>A block is irreversible from the blocked person's side - they cannot log in to ask about it
 * (#84) - so the reason is the only thing that makes it answerable afterwards. That is why
 * {@code justification} is NOT NULL here.
 *
 * <p>No {@link com.centraldungeon.common.model.BaseEntity} and not one setter: same reasoning as
 * {@code TableStatusChange}, the row it is copied from.
 */
@Entity
@Table(name = "user_status_changes")
public class UserStatusChange {

    /** UUID v7, assigned on persist - the same scheme as BaseEntity, which this one cannot extend. */
    @Id
    @Column(length = 64)
    private @Nullable String id;

    /** Whose account changed. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Where the account was before the change. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, length = 32)
    private UserStatus fromStatus;

    /** Where it went. */
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 32)
    private UserStatus toStatus;

    /** Who made the change - always the actor from the token (#121). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by", nullable = false)
    private User changedBy;

    /** Why. Required: a block nobody explained is a block nobody can undo fairly (#84). */
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String justification;

    /** When it happened. Stamped on persist; it is what orders the history. */
    @Column(name = "created_at", nullable = false)
    private @Nullable LocalDateTime createdAt;

    /** Required by JPA. */
    protected UserStatusChange() {
    }

    /**
     * Records one change of account status.
     *
     * @param user          whose account changed
     * @param fromStatus    where it was
     * @param toStatus      where it went
     * @param changedBy     who made the change, always the actor from the token (#121)
     * @param justification why, never blank
     */
    public UserStatusChange(User user, UserStatus fromStatus, UserStatus toStatus, User changedBy, String justification) {
        this.user = user;
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
     * Returns whose account changed.
     *
     * @return the person, lazily loaded
     */
    public User getUser() {
        return user;
    }

    /**
     * Returns where the account was before the change.
     *
     * @return the previous status, never null
     */
    public UserStatus getFromStatus() {
        return fromStatus;
    }

    /**
     * Returns where the account went.
     *
     * @return the resulting status, never null
     */
    public UserStatus getToStatus() {
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
     * @return the justification, never null and never blank
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
