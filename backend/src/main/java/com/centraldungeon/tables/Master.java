package com.centraldungeon.tables;

import com.centraldungeon.users.User;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "masters")
public class Master {

    @EmbeddedId
    private MasterId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("gameTableId")
    @JoinColumn(name = "game_table_id")
    private GameTable gameTable;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "master_type", nullable = false, length = 32)
    private MasterType masterType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MasterRowStatus status = MasterRowStatus.Created;

    @Column(name = "created_at", nullable = false)
    private @Nullable LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private @Nullable LocalDateTime deletedAt;

    protected Master() {
    }

    public Master(GameTable gameTable, User user, MasterType masterType) {
        this.gameTable = gameTable;
        this.user = user;
        this.masterType = masterType;
        this.id = new MasterId(gameTable.getId(), user.getId());
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public GameTable getGameTable() {
        return gameTable;
    }

    public User getUser() {
        return user;
    }

    public MasterType getMasterType() {
        return masterType;
    }

    public void setMasterType(MasterType masterType) {
        this.masterType = masterType;
    }

    public MasterRowStatus getStatus() {
        return status;
    }
}
