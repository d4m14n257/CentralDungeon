package com.centraldungeon.tables;

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
 * One step of a table's lifecycle: who moved it, from where to where, and why. Table
 * {@code table_status_changes}.
 *
 * <p>It is what makes a refusal or a cancellation answerable afterwards - "the admin asked for
 * changes" is only useful next to the reason they gave.
 *
 * <p>No {@link com.centraldungeon.common.model.BaseEntity}: this table has no {@code updated_at} and
 * no {@code deleted_at}, because a historical row is never edited and never deleted. That is also
 * why every getter below is a read and there is not one setter.
 */
@Entity
@Table(name = "table_status_changes")
public class TableStatusChange {

    /** UUID v7, assigned on persist - the same scheme as BaseEntity, which this one cannot extend. */
    @Id
    @Column(length = 64)
    private @Nullable String id;

    /** The table this step belongs to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_table_id", nullable = false)
    private GameTable gameTable;

    /** Where the table was before the change. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, length = 32)
    private GameTableStatus fromStatus;

    /** Where it went. */
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 32)
    private GameTableStatus toStatus;

    /** Who made the change - a master, an admin, or the owner. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by", nullable = false)
    private User changedBy;

    /**
     * Why. Required by the transitions that deny something (cancel, request changes) and absent from
     * the ones that simply move forward.
     */
    @Column(columnDefinition = "LONGTEXT")
    private @Nullable String justification;

    /** When the change happened. Stamped on persist; it is what orders the history. */
    @Column(name = "created_at", nullable = false)
    private @Nullable LocalDateTime createdAt;

    /** Required by JPA. */
    protected TableStatusChange() {
    }

    /**
     * Records one step of the lifecycle.
     *
     * @param gameTable     the table that moved
     * @param fromStatus    where it was
     * @param toStatus      where it went
     * @param changedBy     who moved it, always the actor from the token (#121)
     * @param justification why, or null for a transition that does not deny anything
     */
    public TableStatusChange(
            GameTable gameTable, GameTableStatus fromStatus, GameTableStatus toStatus, User changedBy, @Nullable String justification) {
        this.gameTable = gameTable;
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
     * Returns where the table was before the change.
     *
     * @return the previous status, never null
     */
    public GameTableStatus getFromStatus() {
        return fromStatus;
    }

    /**
     * Returns where the table went.
     *
     * @return the resulting status, never null
     */
    public GameTableStatus getToStatus() {
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
     * @return the justification, or null for a transition that did not need one
     */
    public @Nullable String getJustification() {
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
