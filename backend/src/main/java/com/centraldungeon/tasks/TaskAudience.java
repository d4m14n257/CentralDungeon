package com.centraldungeon.tasks;

/**
 * Who a task is addressed to (#63). The three moments a table asks for something, in one vocabulary.
 *
 * <p>The audience is what turns a task into a list of people: publishing resolves it into the
 * recipients who get notified (#77), reading resolves it into who may answer, and the master's roster
 * resolves it into who is still missing. Nothing else in the feature decides that on its own.
 */
public enum TaskAudience {

    /**
     * Everybody thinking about applying, and everybody who already did.
     *
     * <p>Deliberately readable by <b>anyone who can see the table</b> and not only by its current
     * candidates: what a table is going to ask of you is part of deciding whether to apply at all.
     * Same reasoning as #206 for the files a table shares.
     */
    Candidates,

    /** The people already accepted into the table. What gets asked once somebody is in. */
    Players,

    /**
     * One named person, in {@code targetUserId}.
     *
     * <p>This is the "hand each player their own material" of #76, and the only audience where
     * {@code target_user_id} is filled in - the service refuses the combination in either direction.
     */
    Single
}
