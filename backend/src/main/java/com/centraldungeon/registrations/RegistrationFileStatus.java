package com.centraldungeon.registrations;

/**
 * Whether a file is still part of an application. Same two values, and the same reason, as
 * {@code TableFileStatus}: the pair is the primary key, so detaching marks the row instead of
 * dropping it and re-attaching the same file revives it rather than colliding.
 */
public enum RegistrationFileStatus {

    /** Still attached to the application. */
    Current,

    /** Taken off. The file itself is untouched (#79), and the bytes are never freed here (#25, #66). */
    Removed
}
