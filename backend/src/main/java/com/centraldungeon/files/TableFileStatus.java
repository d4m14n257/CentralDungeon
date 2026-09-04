package com.centraldungeon.files;

/**
 * Whether a file is still attached to a table.
 *
 * <p>Separate from {@link FileStatus} on purpose, because they answer different questions: this one
 * is about the link, that one about the file. Detaching a file from one table says nothing about the
 * file, which may well still be attached to three others (#79).
 *
 * <p>A link is never physically dropped. The primary key of {@code table_files} is the pair
 * {@code (game_table_id, file_id)}, so a master who detaches a map and puts it back a minute later
 * would collide with the row they just deleted - marking it makes the round trip work, exactly as
 * {@code TableScheduleStatus} does for the agenda.
 */
public enum TableFileStatus {

    /** Attached: the file is part of the table and every read counts it. */
    Current,

    /** Detached. Skipped by every read, and revived rather than re-inserted. */
    Deleted
}
