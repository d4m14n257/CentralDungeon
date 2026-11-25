package com.centraldungeon.features.tasks;

/**
 * Whether a file is still attached to the request that asks for it.
 *
 * <p>Two values and a soft delete: taking the blank off a request is a decision worth keeping a
 * record of, and it never touches the file.
 */
public enum TaskFileStatus {

    /** Still attached: whoever the request reaches can download it. */
    Current,

    /** Taken off the request. The file itself is untouched, and its file category stands. */
    Detached
}
