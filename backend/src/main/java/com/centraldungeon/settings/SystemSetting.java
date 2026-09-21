package com.centraldungeon.settings;

import com.centraldungeon.users.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * One setting somebody changed. Table {@code system_settings} (#141).
 *
 * <p><b>A row means "overridden", and its absence means "still the shipped default".</b> The
 * catalogue is {@link SettingKey}, in code; this table holds only the keys a human moved. That is
 * what makes a brand-new database and a database somebody wiped behave the same way, and it is why
 * nothing seeds this table - a seed would turn every default into a value that has to be migrated
 * the day it changes.
 *
 * <p>No {@link com.centraldungeon.common.model.BaseEntity}: the primary key is the setting's own
 * name and not a generated id, because a setting is named, not instanced. There can only ever be
 * one row per key and the database is what says so.
 *
 * <p>{@code updated_by} and {@code updated_at} answer "who has it like this and since when" on the
 * screen itself. They are the <em>current</em> state and not the history - the history is
 * {@link SystemSettingChange}, and #141's «cada cambio se audita» is that table's job. This one only
 * ever remembers the last change, which is all the listing needs.
 */
@Entity
@Table(name = "system_settings")
public class SystemSetting {

    /** Which setting this row overrides. The primary key: one row per key, enforced by the database. */
    @Id
    @Column(name = "setting_key", length = 64)
    private String settingKey;

    /** The override, as text. How to read it back is {@link SettingKey#valueType()}. */
    @Column(nullable = false, length = 512)
    private String value;

    /**
     * How the text is read back, copied from the key.
     *
     * <p>Denormalized on purpose, and the column is V1's: it is what lets somebody reading the table
     * with a SQL client know what they are looking at without the enum in front of them. The enum
     * stays the authority - nothing reads this column back.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 16)
    private SettingValueType valueType;

    /** Which group the screen files it under, copied from the key for the same reason as {@link #valueType}. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SettingCategory category;

    /** Who changed it last - always the actor from the token (#121). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    /** When it was last changed. */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Required by JPA. */
    protected SystemSetting() {
    }

    /**
     * Overrides a setting for the first time.
     *
     * @param key       which setting is being overridden
     * @param value     the new value, already validated against the key's range
     * @param updatedBy who is changing it, always the actor from the token (#121)
     */
    public SystemSetting(SettingKey key, String value, User updatedBy) {
        this.settingKey = key.wireName();
        this.valueType = key.valueType();
        this.category = key.category();
        this.value = value;
        this.updatedBy = updatedBy;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Returns which setting this row overrides.
     *
     * @return the key, as {@link SettingKey#wireName()} spells it
     */
    public String getSettingKey() {
        return settingKey;
    }

    /**
     * Returns the override.
     *
     * @return the stored value, as text
     */
    public String getValue() {
        return value;
    }

    /**
     * Returns how the stored text is read back.
     *
     * @return the value type, copied from the key when the row was written
     */
    public SettingValueType getValueType() {
        return valueType;
    }

    /**
     * Returns which group the screen files this setting under.
     *
     * @return the category, copied from the key when the row was written
     */
    public SettingCategory getCategory() {
        return category;
    }

    /**
     * Returns who changed the setting last.
     *
     * @return the actor, lazily loaded
     */
    public User getUpdatedBy() {
        return updatedBy;
    }

    /**
     * Returns when the setting was last changed.
     *
     * @return the timestamp of the last change
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Replaces the override.
     *
     * <p>The only mutator, and it moves all three of what changed, who changed it and when together:
     * a value updated without its actor would leave the listing saying somebody else has it like
     * this.
     *
     * @param value     the new value, already validated against the key's range
     * @param updatedBy who is changing it, always the actor from the token (#121)
     */
    public void update(String value, User updatedBy) {
        this.value = value;
        this.updatedBy = updatedBy;
        this.updatedAt = LocalDateTime.now();
    }
}
