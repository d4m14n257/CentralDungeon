package com.centraldungeon.notifications;

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

/** No BaseEntity: notifications has no updated_at column. Read, not push, in E1 - the socket arrives in E6. */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @Column(length = 64)
    private @Nullable String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 32)
    private NotificationType notificationType;

    @Column(nullable = false, length = 128)
    private String title;

    @Column(length = 1024)
    private @Nullable String message;

    @Column(name = "related_entity_type", length = 32)
    private @Nullable String relatedEntityType;

    @Column(name = "related_entity_id", length = 64)
    private @Nullable String relatedEntityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "read_status", nullable = false, length = 32)
    private ReadStatus readStatus = ReadStatus.Unread;

    @Column(name = "created_at", nullable = false)
    private @Nullable LocalDateTime createdAt;

    @Column(name = "read_at")
    private @Nullable LocalDateTime readAt;

    protected Notification() {
    }

    public Notification(
            User user,
            NotificationType notificationType,
            String title,
            @Nullable String message,
            @Nullable String relatedEntityType,
            @Nullable String relatedEntityId) {
        this.user = user;
        this.notificationType = notificationType;
        this.title = title;
        this.message = message;
        this.relatedEntityType = relatedEntityType;
        this.relatedEntityId = relatedEntityId;
    }

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = IdGenerator.newId();
        }
        createdAt = LocalDateTime.now();
    }

    public void markRead() {
        this.readStatus = ReadStatus.Read;
        this.readAt = LocalDateTime.now();
    }

    public String getId() {
        if (id == null) {
            throw new IllegalStateException("Notification id is not assigned yet - it is set on persist");
        }
        return id;
    }

    public User getUser() {
        return user;
    }

    public NotificationType getNotificationType() {
        return notificationType;
    }

    public String getTitle() {
        return title;
    }

    public @Nullable String getMessage() {
        return message;
    }

    public @Nullable String getRelatedEntityType() {
        return relatedEntityType;
    }

    public @Nullable String getRelatedEntityId() {
        return relatedEntityId;
    }

    public ReadStatus getReadStatus() {
        return readStatus;
    }

    public @Nullable LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
