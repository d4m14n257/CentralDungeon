package com.centraldungeon.settings;

import com.centraldungeon.common.model.IdGenerator;
import com.centraldungeon.users.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * One change to one setting: what it was, what it became, who did it and why. Table
 * {@code system_setting_changes} (#141).
 *
 * <p><b>Why a table and not the two columns next door.</b> {@link SystemSetting} already carries
 * {@code updated_by} and {@code updated_at}, and they answer a different question: who has it like
 * this <em>now</em>. #141 asks that «cada cambio se audita», and a pair of columns that the next
 * edit overwrites cannot say a value was raised on Tuesday and put back on Wednesday - which is
 * exactly the pattern worth seeing. Same distinction {@code user_status_changes} makes against
 * {@code users.status}, and the row is copied from it.
 *
 * <p><b>The reason is required</b>, as it is in {@code user_role_changes} and
 * {@code user_status_changes} (F3.1). Two of these settings change what people can see and what they
 * may upload, with no notification and no visible event: without the reason, the only trace of a
 * platform-wide change is a number that used to be different.
 *
 * <p><b>{@code setting_key} has no foreign key</b>, and this is not #78's polymorphic reference
 * coming back. What it points at is a constant of {@link SettingKey}, checked at compile time and
 * resolved before anything is written - it cannot be deleted, renamed behind the code's back or left
 * dangling. The reason there is no constraint is the opposite one: {@code system_settings} holds
 * overrides only, so the row a change points at may legitimately not exist yet when the change is
 * recorded.
 *
 * <p>No {@link com.centraldungeon.common.model.BaseEntity} and not one setter: an audit row is
 * written once and never edited or deleted, the same as the two rows it is modelled on.
 */
@Entity
@Table(name = "system_setting_changes")
public class SystemSettingChange {

    /** UUID v7, assigned on persist - the same scheme as BaseEntity, which this one cannot extend. */
    @Id
    @Column(length = 64)
    private @Nullable String id;

    /** Which setting changed, as {@link SettingKey#wireName()} spells it. */
    @Column(name = "setting_key", nullable = false, length = 64)
    private String settingKey;

    /**
     * What the setting was before the change, or null when it had never been overridden.
     *
     * <p>Null is information and not a gap: it says the platform was running on the shipped default,
     * which is a different fact from "it was 15 and somebody typed 15 again".
     */
    @Column(name = "from_value", length = 512)
    private @Nullable String fromValue;

    /** What the setting became. */
    @Column(name = "to_value", nullable = false, length = 512)
    private String toValue;

    /** Who made the change - always the actor from the token (#121). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by", nullable = false)
    private User changedBy;

    /** Why. Required: a platform-wide change nobody explained is one nobody can review (#141). */
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String justification;

    /** When it happened. Stamped on persist; it is what orders the history. */
    @Column(name = "created_at", nullable = false)
    private @Nullable LocalDateTime createdAt;

    /** Required by JPA. */
    protected SystemSettingChange() {
    }

    /**
     * Records one change to a setting.
     *
     * @param key           which setting changed
     * @param fromValue     what it was, or null when it had never been overridden
     * @param toValue       what it became
     * @param changedBy     who made the change, always the actor from the token (#121)
     * @param justification why, never blank
     */
    public SystemSettingChange(
            SettingKey key, @Nullable String fromValue, String toValue, User changedBy, String justification) {
        this.settingKey = key.wireName();
        this.fromValue = fromValue;
        this.toValue = toValue;
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
     * Returns which setting changed.
     *
     * @return the key, as {@link SettingKey#wireName()} spells it
     */
    public String getSettingKey() {
        return settingKey;
    }

    /**
     * Returns what the setting was before the change.
     *
     * @return the previous value, or null when the platform was still on the shipped default
     */
    public @Nullable String getFromValue() {
        return fromValue;
    }

    /**
     * Returns what the setting became.
     *
     * @return the new value, never null
     */
    public String getToValue() {
        return toValue;
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
