package com.centraldungeon.tasks;

/**
 * Whether a file is still attached to the request that asks for it.
 *
 * <p>Two values and a soft delete, like {@code SubmissionFileStatus}: taking the blank off a request
 * is a decision worth keeping a record of, and it never touches the file (#25, #79).
 */
public enum TaskFileStatus {

    /** Still attached: whoever the request reaches can download it. */
    Current,

    /** Taken off the request. The file itself is untouched, and its cajón stands (#233). */
    Detached
}
