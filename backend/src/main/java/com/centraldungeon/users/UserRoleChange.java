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
 * One role given or taken away: to whom, which role, by whom, and why. Table
 * {@code user_role_changes}.
 *
 * <p>{@code users_roles} says what someone holds <em>now</em>; this says how they got there. The two
 * are not the same question, and the second one is the one an admin has to answer afterwards - "who
 * made this person an admin, and what reason did they give".
 *
 * <p>No {@link com.centraldungeon.common.model.BaseEntity} and not one setter, for the same reason
 * as {@code TableStatusChange}: a historical row is never edited and never deleted, so it has no
 * {@code updated_at} and no {@code deleted_at} to carry.
 *
 * <p>The exclusion of #169 writes <b>two</b> rows, not one: granting Admin to an owner is a grant of
 * Admin and a revocation of Owner, and burying the second one inside the first would lose exactly
 * the event somebody would go looking for.
 */
@Entity
@Table(name = "user_role_changes")
public class UserRoleChange {

    /** UUID v7, assigned on persist - the same scheme as BaseEntity, which this one cannot extend. */
    @Id
    @Column(length = 64)
    private @Nullable String id;

    /** Whose roles changed. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Which role moved. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    /** Whether it was given or taken away. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserRoleChangeAction action;

    /** Who made the change - always the actor from the token (#121). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by", nullable = false)
    private User changedBy;

    /**
     * Why. Required, unlike {@code table_status_changes}: there is no role change that does not
     * grant or deny something to a person, so there is none that can go without a reason (#169).
     */
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String justification;

    /** When it happened. Stamped on persist; it is what orders the history. */
    @Column(name = "created_at", nullable = false)
    private @Nullable LocalDateTime createdAt;

    /** Required by JPA. */
    protected UserRoleChange() {
    }

    /**
     * Records one role change.
     *
     * @param user          whose roles changed
     * @param role          which role moved
     * @param action        whether it was given or taken away
     * @param changedBy     who made the change, always the actor from the token (#121)
     * @param justification why, never blank
     */
    public UserRoleChange(User user, Role role, UserRoleChangeAction action, User changedBy, String justification) {
        this.user = user;
        this.role = role;
        this.action = action;
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
     * Returns whose roles changed.
     *
     * @return the person, lazily loaded
     */
    public User getUser() {
        return user;
    }

    /**
     * Returns which role moved.
     *
     * @return the role, lazily loaded
     */
    public Role getRole() {
        return role;
    }

    /**
     * Returns whether the role was given or taken away.
     *
     * @return the action, never null
     */
    public UserRoleChangeAction getAction() {
        return action;
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
