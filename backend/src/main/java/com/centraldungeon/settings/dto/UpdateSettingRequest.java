package com.centraldungeon.settings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Changing one setting: the new value and why (#141).
 *
 * <p><b>The range is not here.</b> Bean Validation can say "a whole number" and cannot say "between
 * 1 and 1440 for this key and between 1 and 25 for that one", because the bound belongs to the key
 * and the key is in the URL. So the range is checked in {@code SettingsService}, which is also where
 * it has to be for the rule to hold for every caller and not only for the ones that come through
 * this record.
 *
 * @param value         the new value. An {@code Integer} and not an {@code int} so that a missing
 *                      field is answered as "value is required" rather than silently becoming 0 -
 *                      which for three of these settings is a number with a meaning
 * @param justification why the setting is being changed. Required, like every other administrative
 *                      change in F3: a platform-wide adjustment leaves no visible event, so the
 *                      reason is the only thing that makes it reviewable afterwards
 */
public record UpdateSettingRequest(
        @NotNull Integer value,
        @NotBlank @Size(max = 2000) String justification) {
}
