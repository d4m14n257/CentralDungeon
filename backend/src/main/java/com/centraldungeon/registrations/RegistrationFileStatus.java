package com.centraldungeon.registrations;

/**
 * Whether a file is part of an application. A file is never taken off an application on its own, so
 * the only value is {@link #Current}; the column stays so a link's state can grow without a schema change.
 */
public enum RegistrationFileStatus {

    /** Attached to the application. */
    Current
}
