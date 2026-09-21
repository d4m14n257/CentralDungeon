package com.centraldungeon.settings.dto;

import com.centraldungeon.settings.SettingCategory;
import com.centraldungeon.settings.SettingKey;
import com.centraldungeon.settings.SettingValueType;
import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * One row of {@code /admin/settings}: what the setting is worth now, what it ships as, and how far
 * it may be moved (#141).
 *
 * <p><b>The default travels with the current value</b>, which is what the screen was asked for -
 * «con el valor actual y el por defecto a la vista». Without it, an admin looking at {@code 15} has
 * no way to tell whether somebody set it there or whether it has always been that, and no way to put
 * it back without finding the number in the source.
 *
 * <p><b>No label and no description</b>, deliberately (#197): the backend writes nothing a person
 * reads. The screen builds both from {@code key}, in the reader's language, out of
 * {@code src/locales/<idioma>/admin.json} - which is also why {@code key} is the stable string
 * {@link SettingKey#wireName()} publishes and not an index.
 *
 * @param key           which setting this is. Serializes to its wire name through {@code @JsonValue}
 * @param category      which group the screen files it under
 * @param valueType     how the value is read. Every setting is an {@code Integer} today
 * @param value         what the platform uses right now - the override if there is one, the default
 *                      otherwise
 * @param defaultValue  what it ships as, so "put it back" never needs the source code
 * @param minValue      the smallest value the API accepts, inclusive
 * @param maxValue      the largest value the API accepts, inclusive
 * @param overridden    whether a human set this, as opposed to it still being the shipped default.
 *                      Not derivable from comparing the two numbers: somebody may set a setting to
 *                      exactly its default, and that is a decision somebody made
 * @param retroactive   whether changing it re-answers questions already answered (#44, #97). The
 *                      screen turns it into a warning shown before the value is saved
 * @param updatedByName who set it last, already resolved to a display name, or null when nobody has
 * @param updatedAt     when it was last set, or null when nobody has
 */
public record SystemSettingResponse(
        SettingKey key,
        SettingCategory category,
        SettingValueType valueType,
        int value,
        int defaultValue,
        int minValue,
        int maxValue,
        boolean overridden,
        boolean retroactive,
        @Nullable String updatedByName,
        @Nullable LocalDateTime updatedAt) {
}
