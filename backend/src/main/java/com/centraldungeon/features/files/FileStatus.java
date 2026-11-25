package com.centraldungeon.features.files;

/**
 * Whether a file still counts.
 *
 * <p><b>Marking is the only kind of delete there is for now</b>. Nothing here frees a byte: the row is
 * flagged, every read skips it, and the blob stays on disk until the platform owner runs the physical
 * purge from the administration menu, which is still to be built.
 *
 * <p>Two things reach {@link #Deleted}: the owner deleting their own file, and the retention job
 * marking one that has gone unused for long enough.
 */
public enum FileStatus {

    /** Live: it can be read, downloaded, attached to a table and offered in the reuse history. */
    Current,

    /** Marked gone. Skipped by every read; the bytes are still there and the physical purge decides their fate. */
    Deleted
}
