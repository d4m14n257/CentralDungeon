package com.centraldungeon.files;

/**
 * Whether a file still counts.
 *
 * <p><b>Marking is the only kind of delete F1 has</b> (#25). Nothing here frees a byte: the row is
 * flagged, every read skips it, and the blob stays on disk until the platform owner runs the physical
 * purge from the administration menu, which is F5 (#66).
 *
 * <p>Two things reach {@link #Deleted}: the owner deleting their own file, and the retention job of
 * #75 marking one that has gone unused for long enough.
 */
public enum FileStatus {

    /** Live: it can be read, downloaded, attached to a table and offered in the reuse history (#65). */
    Current,

    /** Marked gone. Skipped by every read; the bytes are still there and F5 decides their fate (#66). */
    Deleted
}
