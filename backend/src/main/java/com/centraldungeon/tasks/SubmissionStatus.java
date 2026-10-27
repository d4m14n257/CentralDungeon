package com.centraldungeon.tasks;

/**
 * Where one answer to a task stands. <b>One value</b> (ADR-15 §2).
 *
 * <p>The design of ADR-15 §1 originally had {@code Accepted} and {@code Rejected} as well. They were
 * dropped on purpose: judging whether a submitted character sheet is the right one takes a criterion
 * the system does not have and should not pretend to have, so there is no approval flow and the
 * master reviews by looking. Submitting is one call and it lands as {@link #Submitted}.
 */
public enum SubmissionStatus {

    /** Handed in, with its {@code submitted_at} stamped. What the master's roster counts. */
    Submitted
}
