package com.centraldungeon.settings;

/**
 * What kind of value a setting holds, written to {@code system_settings.value_type}.
 *
 * <p>The column stores everything as text - that is the whole point of a key-value table, and the
 * reason #141 chose one over a column per setting: adding a setting must never be an {@code ALTER
 * TABLE}, which is #10's reasoning about {@code ENUM} applied again. This enum is what says how to
 * read the text back.
 *
 * <p><b>One constant, and V1's column comment names five.</b> The other four -
 * {@code Decimal}, {@code Duration}, {@code Text}, {@code Boolean} - are what the schema anticipated,
 * not what F3.5 ships: every value the phase moves out of the code is a whole number, and three of
 * them are whole numbers <em>because</em> the unit is part of the key. A parser for a type no
 * setting uses is code nothing exercises (#255). Adding one is this enum plus one branch in
 * {@link SettingsService}.
 *
 * <p>Why the units live in the key ({@code files.max_file_size_mb}, not {@code files.max_file_size}
 * with a {@code DataSize}): the screen is an admin typing a number into a box. {@code PT15M} is a
 * correct {@code Duration} and an unusable form field, and the day somebody changes what the unit
 * means the key changes with it instead of silently reinterpreting the stored value.
 */
public enum SettingValueType {

    /** A whole number, parsed with {@link Integer#parseInt}. Bounded per key by {@link SettingKey}. */
    Integer
}
