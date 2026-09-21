package com.centraldungeon.settings.dto;

import com.centraldungeon.settings.SettingKey;
import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * One entry of a setting's history: what it went from, what it went to, who moved it and why (#141).
 *
 * <p>It exists for the same reason {@code UserAdminChangeResponse} does: without a read, the audit
 * table would be write-only and born orphaned, which is the failure fase-3-admin-owner.md §7 names.
 *
 * @param id            the entry's identifier
 * @param key           which setting changed
 * @param fromValue     what it was, or null when the platform was still on the shipped default -
 *                      which is a different fact from "it was already this number"
 * @param toValue       what it became
 * @param changedByName who did it, already resolved to a display name: the screen shows a person and
 *                      not an id, like every other history in the application
 * @param justification why. Never null: the column requires it
 * @param createdAt     when it happened, in UTC
 */
public record SystemSettingChangeResponse(
        String id,
        SettingKey key,
        @Nullable String fromValue,
        String toValue,
        String changedByName,
        String justification,
        LocalDateTime createdAt) {
}
