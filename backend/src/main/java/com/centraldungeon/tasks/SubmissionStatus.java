package com.centraldungeon.tasks;

/**
 * Where one answer to a task stands. <b>Two values, not four</b> (#76).
 *
 * <p>The design of #63 originally had {@code Accepted} and {@code Rejected} as well. They were
 * dropped on purpose: judging whether a submitted character sheet is the right one takes a criterion
 * the system does not have and should not pretend to have, so there is no approval flow and the
 * master reviews by looking. What is left is the mechanical half - has this been handed in or not.
 */
public enum SubmissionStatus {

    /**
     * Created but not handed in.
     *
     * <p>Nothing in the application writes this today: submitting is one call and it lands as
     * {@link #Submitted}. It exists because the column's default is {@code 'Pending'} and because a
     * saved draft is the obvious next thing somebody will ask for - reading it has to already work
     * when that happens.
     */
    Pending,

    /** Handed in, with its {@code submitted_at} stamped. What the master's roster counts. */
    Submitted
}
