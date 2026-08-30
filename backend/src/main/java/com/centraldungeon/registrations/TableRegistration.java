package com.centraldungeon.registrations;

import com.centraldungeon.common.model.BaseEntity;
import com.centraldungeon.tables.GameTable;
import com.centraldungeon.users.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "table_registrations")
public class TableRegistration extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_table_id", nullable = false)
    private GameTable gameTable;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TableRegistrationStatus status = TableRegistrationStatus.Candidate;

    @Column(columnDefinition = "LONGTEXT")
    private @Nullable String description;

    protected TableRegistration() {
    }

    public TableRegistration(GameTable gameTable, User user, @Nullable String description) {
        this.gameTable = gameTable;
        this.user = user;
        this.description = description;
    }

    public GameTable getGameTable() {
        return gameTable;
    }

    public User getUser() {
        return user;
    }

    public TableRegistrationStatus getStatus() {
        return status;
    }

    public void setStatus(TableRegistrationStatus status) {
        this.status = status;
    }

    public @Nullable String getDescription() {
        return description;
    }
}
