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

/** No BaseEntity: table_status_changes has no updated_at/deleted_at - it is an immutable historical row. */
@Entity
@Table(name = "table_status_changes")
public class TableStatusChange {

    @Id
    @Column(length = 64)
    private @Nullable String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_table_id", nullable = false)
    private GameTable gameTable;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, length = 32)
    private GameTableStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 32)
    private GameTableStatus toStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by", nullable = false)
    private User changedBy;

    @Column(columnDefinition = "LONGTEXT")
    private @Nullable String justification;

    @Column(name = "created_at", nullable = false)
    private @Nullable LocalDateTime createdAt;

    protected TableStatusChange() {
    }

    public TableStatusChange(
            GameTable gameTable, GameTableStatus fromStatus, GameTableStatus toStatus, User changedBy, @Nullable String justification) {
        this.gameTable = gameTable;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedBy = changedBy;
        this.justification = justification;
    }

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = IdGenerator.newId();
        }
        createdAt = LocalDateTime.now();
    }

    public String getId() {
        if (id == null) {
            throw new IllegalStateException("Entity id is not assigned yet - it is set on persist");
        }
        return id;
    }

    public GameTableStatus getFromStatus() {
        return fromStatus;
    }

    public GameTableStatus getToStatus() {
        return toStatus;
    }

    public User getChangedBy() {
        return changedBy;
    }

    public @Nullable String getJustification() {
        return justification;
    }

    public LocalDateTime getCreatedAt() {
        if (createdAt == null) {
            throw new IllegalStateException("Entity createdAt is not assigned yet - it is set on persist");
        }
        return createdAt;
    }
}
