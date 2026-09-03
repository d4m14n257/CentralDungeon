package com.centraldungeon.tables;

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
import java.time.LocalTime;
import org.jspecify.annotations.Nullable;

/**
 * E1 maps only the columns this etapa's flow needs. permitted, claimed_by/claimed_at (admin
 * queue) and closed_at (closure window) exist in the baseline schema but stay unmapped until
 * the etapas that use them land - no migration needed to add them later (modelo-datos.md 4).
 */
@Entity
@Table(name = "game_tables")
public class GameTable extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_type_id")
    private @Nullable TableType tableType;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(columnDefinition = "LONGTEXT")
    private @Nullable String description;

    @Column(columnDefinition = "LONGTEXT")
    private @Nullable String requirements;

    @Column(name = "start_date")
    private @Nullable LocalDateTime startDate;

    @Column
    private @Nullable LocalTime duration;

    @Column(name = "total_sessions")
    private @Nullable Integer totalSessions;

    @Column(name = "max_players")
    private @Nullable Integer maxPlayers;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private GameTableStatus status = GameTableStatus.Preparation;

    /** Soft delete (#25): the column already existed in the baseline; nothing maps it until now. */
    @Column(name = "deleted_at")
    private @Nullable LocalDateTime deletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    protected GameTable() {
    }

    public GameTable(String name, User createdBy) {
        this.name = name;
        this.createdBy = createdBy;
    }

    public @Nullable TableType getTableType() {
        return tableType;
    }

    public void setTableType(@Nullable TableType tableType) {
        this.tableType = tableType;
    }

    public String getName() {
        return name;
    }

    public @Nullable String getDescription() {
        return description;
    }

    public void setDescription(@Nullable String description) {
        this.description = description;
    }

    public @Nullable String getRequirements() {
        return requirements;
    }

    public void setRequirements(@Nullable String requirements) {
        this.requirements = requirements;
    }

    public @Nullable LocalDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(@Nullable LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public @Nullable LocalTime getDuration() {
        return duration;
    }

    public void setDuration(@Nullable LocalTime duration) {
        this.duration = duration;
    }

    public @Nullable Integer getTotalSessions() {
        return totalSessions;
    }

    public void setTotalSessions(@Nullable Integer totalSessions) {
        this.totalSessions = totalSessions;
    }

    public @Nullable Integer getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(@Nullable Integer maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public GameTableStatus getStatus() {
        return status;
    }

    public @Nullable LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(@Nullable LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public void setStatus(GameTableStatus status) {
        this.status = status;
    }

    public User getCreatedBy() {
        return createdBy;
    }
}
