package com.centraldungeon.common.model;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @Column(length = 64)
    private @Nullable String id;

    @Column(name = "created_at", nullable = false)
    private @Nullable LocalDateTime createdAt;

    @Column(name = "updated_at")
    private @Nullable LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = IdGenerator.newId();
        }
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public String getId() {
        if (id == null) {
            throw new IllegalStateException("Entity id is not assigned yet - it is set on persist");
        }
        return id;
    }

    public LocalDateTime getCreatedAt() {
        if (createdAt == null) {
            throw new IllegalStateException("Entity createdAt is not assigned yet - it is set on persist");
        }
        return createdAt;
    }

    public @Nullable LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
