package com.centraldungeon.notifications;

/** E1 minimal set - more types arrive with the features that emit them. */
public enum NotificationType {

    /** To the applicant: a master accepted them into a table. */
    RegistrationAccepted,

    /** To the applicant: their application was turned down, with the master's reason. */
    RegistrationRejected,

    /** To every master of the table, Primary and Secondary alike: someone applied. */
    NewCandidate,

    /**
     * To somebody whose commitments now overlap: the table named in the notification clashes with
     * another one they are in (#178).
     *
     * <p>It is a warning and never an eviction. R4 sends it when accepting somebody makes their
     * other pending applications clash, and {@code TableScheduleService} sends it when a master
     * moves an agenda under people who were already signed up. In both cases the system says what
     * happened and the person decides - the same principle as #70.
     */
    ScheduleConflict,

    /**
     * To the people signed up to a table: one of its sessions moved, or the whole pending calendar
     * was re-laid after a pause (#33).
     *
     * <p>A calendar that changes without saying so is a calendar nobody can plan around, which is why
     * this exists at all - the same reasoning as #77 for tasks.
     */
    SessionScheduled,

    /**
     * To the people signed up to a table: one of its sessions was called off.
     *
     * <p>The table gets the session back at the end (#194), so this is news about one evening and not
     * about the run being shorter.
     */
    SessionCanceled,

    /**
     * To everybody a task is addressed to: the table is asking them for something (#77).
     *
     * <p>It exists because a task nobody heard about cannot be answered - which is the whole of #77.
     * It is sent once, when the task is published; correcting it later says nothing, because a
     * request fixed three times ringing three times is how people learn to ignore the bell.
     *
     * <p>Like every other notification in this list it asks and never threatens: not answering blocks
     * nothing and evicts nobody (#70).
     */
    TaskPublished
}
