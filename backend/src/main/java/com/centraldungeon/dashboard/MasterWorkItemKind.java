package com.centraldungeon.dashboard;

/**
 * What kind of work a table is waiting on. The vocabulary of the master's tray (#136).
 *
 * <p>It is a code and not a sentence on purpose (#197): the backend says <em>what</em> is waiting
 * and hands over the numbers, and the frontend writes the phrase in the reader's language.
 *
 * <p>Every value here is something the master can act on today. That is the line #136 draws
 * between a tray and a dashboard of metrics: "twelve candidates across three tables" changes
 * nothing about what to do next, "this table has somebody waiting since Tuesday" does.
 */
public enum MasterWorkItemKind {

    /** People applied and nobody answered them yet. Answered oldest first, which is the rule (#28). */
    CandidatesWaiting,

    /**
     * A task's deadline went by and some of the people it was addressed to did not hand anything in.
     *
     * <p>It is not the system judging the answers, which #76 forbids, and it does not expel anybody,
     * which #70 forbids: it is the failure being <em>visible to the master</em>, which is what #70
     * asks for. There is no "reviewed" marker anywhere in the model, so "submissions I have not
     * looked at" is not a thing that could be reported here.
     */
    OverdueTaskMissing,

    /**
     * A session's date went by and it is still open. Marking a session held is an explicit act
     * (#195), so time passing closes nothing on its own.
     */
    SessionToRecord,

    /** An admin sent the draft back with a reason, and it stays where it is until the master edits it. */
    ChangesRequested,

    /** The table is open, its start date went by, and nobody declared play begun. */
    ReadyToStart
}
