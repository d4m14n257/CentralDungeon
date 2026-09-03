package com.centraldungeon.notifications;

import com.centraldungeon.common.model.IdGenerator;
import com.centraldungeon.users.User;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
 * Something that happened and that one person needs to know about. Table {@code notifications}.
 *
 * <p>A row, not a push: the WebSocket that makes them arrive live is F5. Until then the bell polls,
 * and when the socket does land it carries an invalidation signal rather than the data - the row
 * here stays the single source of truth (arquitectura.md 3.3).
 *
 * <p><b>A row stores what happened, not the sentence describing it</b> (#197). It is written once
 * and read for months, and the reader can change the application's language in between, so the type
 * plus {@link #getParams()} is what travels and the frontend renders the sentence. {@code title} and
 * {@code message} survive only for the rows written before that change.
 *
 * <p>No BaseEntity: this table has no {@code updated_at}. A notification is written once and only
 * ever flips to read.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    /** UUID v7, assigned on persist - the same scheme as BaseEntity, which this one cannot extend. */
    @Id
    @Column(length = 64)
    private @Nullable String id;

    /** The recipient. One row per person: a notification is never shared between two inboxes. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Which event this is. The frontend maps it to an icon and to where a click should go. */
    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 32)
    private NotificationType notificationType;

    /**
     * The frozen headline of a row written before #197, in the language of the day. Null on every
     * row written since: the bell renders the title from the type and the parameters instead.
     */
    @Column(length = 128)
    private @Nullable String title;

    /** The frozen detail of a row written before #197. Null on every row written since. */
    @Column(length = 1024)
    private @Nullable String message;

    /**
     * The names the rendered sentence needs (#197). Null only on rows written before that change,
     * which is exactly when the frozen {@link #title} and {@link #message} are used instead.
     */
    @Convert(converter = NotificationParamsConverter.class)
    @Column(length = 1024)
    private @Nullable NotificationParams params;

    /** What this is about ("game_table"), so a click knows where to go. Null for a dead end. */
    @Column(name = "related_entity_type", length = 32)
    private @Nullable String relatedEntityType;

    /** The id of that thing. Always null or non-null together with the type. */
    @Column(name = "related_entity_id", length = 64)
    private @Nullable String relatedEntityId;

    /** Whether the recipient has seen it. What the bell's unread count is built on. */
    @Enumerated(EnumType.STRING)
    @Column(name = "read_status", nullable = false, length = 32)
    private ReadStatus readStatus = ReadStatus.Unread;

    /** When it was emitted. Stamped on persist; it orders the inbox, newest first. */
    @Column(name = "created_at", nullable = false)
    private @Nullable LocalDateTime createdAt;

    /** When it was read, or null while it is unread. */
    @Column(name = "read_at")
    private @Nullable LocalDateTime readAt;

    /** Required by JPA. */
    protected Notification() {
    }

    /**
     * Emits a notification, unread.
     *
     * <p>No text: the sentence is rendered by whoever reads it, from the type and these parameters,
     * in the language they chose (#197).
     *
     * @param user              the recipient
     * @param notificationType  which event this is. It decides which sentence gets rendered
     * @param params            the names that sentence needs filled in
     * @param relatedEntityType what this is about ("game_table"), or null for a dead end
     * @param relatedEntityId   the id of that thing. Passed together with the type or not at all
     */
    public Notification(
            User user,
            NotificationType notificationType,
            NotificationParams params,
            @Nullable String relatedEntityType,
            @Nullable String relatedEntityId) {
        this.user = user;
        this.notificationType = notificationType;
        this.params = params;
        this.relatedEntityType = relatedEntityType;
        this.relatedEntityId = relatedEntityId;
    }

    /** Assigns the id and stamps {@code createdAt} on insert. Called by JPA, never by application code. */
    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = IdGenerator.newId();
        }
        createdAt = LocalDateTime.now();
    }

    /** Marks the notification as seen, stamping when. Idempotent in effect: reading twice is harmless. */
    public void markRead() {
        this.readStatus = ReadStatus.Read;
        this.readAt = LocalDateTime.now();
    }

    /**
     * Returns the notification's id.
     *
     * @return the id
     * @throws IllegalStateException if called before the row was persisted
     */
    public String getId() {
        if (id == null) {
            throw new IllegalStateException("Notification id is not assigned yet - it is set on persist");
        }
        return id;
    }

    /**
     * Returns the recipient.
     *
     * @return the user, lazily loaded. Compared against the actor before any read (#121)
     */
    public User getUser() {
        return user;
    }

    /**
     * Returns which event this is.
     *
     * @return the type, never null
     */
    public NotificationType getNotificationType() {
        return notificationType;
    }

    /**
     * Returns the frozen headline of a row written before #197.
     *
     * @return the stored title, or null on any row written since - those render their sentence from
     *         the type and the parameters instead
     */
    public @Nullable String getTitle() {
        return title;
    }

    /**
     * Returns the frozen detail of a row written before #197.
     *
     * @return the stored message, or null
     */
    public @Nullable String getMessage() {
        return message;
    }

    /**
     * Returns the names the rendered sentence needs (#197).
     *
     * @return the parameters, or null on a row written before the change - which is when the frozen
     *         {@link #getTitle()} and {@link #getMessage()} are what gets shown
     */
    public @Nullable NotificationParams getParams() {
        return params;
    }

    /**
     * Returns what the notification is about.
     *
     * @return the entity type, or null when it points nowhere
     */
    public @Nullable String getRelatedEntityType() {
        return relatedEntityType;
    }

    /**
     * Returns the id of what the notification is about.
     *
     * @return the entity id, or null when it points nowhere
     */
    public @Nullable String getRelatedEntityId() {
        return relatedEntityId;
    }

    /**
     * Returns whether the recipient has seen it.
     *
     * @return the read status, never null
     */
    public ReadStatus getReadStatus() {
        return readStatus;
    }

    /**
     * Returns when the notification was emitted.
     *
     * @return the timestamp, or null before the row is persisted
     */
    public @Nullable LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
