package com.centraldungeon.files;

/**
 * What a file is doing on a table: material prepared beforehand, or something produced at a session.
 *
 * <p>Two values and not three. The legacy mixed a {@code Deleted} value into this same column,
 * making one field carry both "what kind of attachment is this" and "is it still attached" - the
 * exact confusion M3 flagged and M21.9 confirmed. Here the second question is {@code status}'s, and
 * this enum only answers the first.
 */
public enum TableFileType {

    /** Material the master put together before playing: maps, handouts, house rules, the sheet. */
    Preparation,

    /** Something that came out of a session: notes, a recap, a map as it ended up. */
    Session
}
