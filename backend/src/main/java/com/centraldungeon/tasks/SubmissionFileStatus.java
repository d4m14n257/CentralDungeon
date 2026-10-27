package com.centraldungeon.tasks;

/**
 * Whether a file is part of a submission. A submitted file is never taken off, so the only value is
 * {@link #Current}; the column stays so a link's state can grow without a schema change.
 */
public enum SubmissionFileStatus {

    /** Attached to the submission. */
    Current
}
