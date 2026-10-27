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
     * another one they are in (ADR-08 §2).
     *
     * <p>It is a warning and never an eviction. R4 sends it when accepting somebody makes their
     * other pending applications clash, and {@code TableScheduleService} sends it when an agenda
     * moves under candidates who have applications to resolve. Players are not sent it: they hear
     * the agenda changed through {@link #TableScheduleChanged} and have nothing to withdraw. In both cases the system says what
     * happened and the person decides - the same principle as ADR-15 §2.
     */
    ScheduleConflict,

    /**
     * To the players of a table (not its candidates, who cannot see sessions): one of its sessions moved (ADR-08 §3).
     * Re-laying the calendar when a paused table resumes is announced by {@link #TableResumed}, not by
     * one of these per session.
     *
     * <p>A calendar that changes without saying so is a calendar nobody can plan around, which is why
     * this exists at all - the same reasoning as ADR-15 §5 for tasks.
     */
    SessionScheduled,

    /**
     * To the players of a table (not its candidates): one of its sessions was called off.
     *
     * <p>The table gets the session back at the end (ADR-08 §3), so this is news about one evening and not
     * about the run being shorter.
     */
    SessionCanceled,

    /**
     * To everybody a task is addressed to: the table is asking them for something (ADR-15 §5).
     *
     * <p>It exists because a task nobody heard about cannot be answered - which is the whole of ADR-15 §5.
     * It is sent once, when the task is published; correcting it later says nothing, because a
     * request fixed three times ringing three times is how people learn to ignore the bell.
     *
     * <p>Like every other notification in this list it asks and never threatens: not answering blocks
     * nothing and evicts nobody (ADR-15 §2).
     */
    TaskPublished,

    /**
     * Somebody was made a master of a table (ADR-06 §7, §4, ADR-18 §3).
     *
     * <p>The one notification whose recipient did nothing to trigger it: an admin creates a table
     * and hands it over, so the person finds out they run it only if the bell says so. Every other
     * type here follows something the recipient did or something on a table they already knew about.
     */
    MasterAssigned,

    /**
     * The admin approved a master's draft and the table is open (ADR-07 §1, ADR-18 §3).
     *
     * <p>The end of a wait the master cannot see the other side of: the table sat in review and only
     * the bell says it came out. An admin who corrected the draft on the way reaches the masters as
     * {@link #TableEditedByAdmin}, which says what changed.
     */
    TableApproved,

    /**
     * The admin sent the draft back for changes (ADR-07 §1, ADR-18 §3).
     *
     * <p>It carries no reason of its own: the justification is already mandatory on the transition and
     * lives in the status history, which is where the master reads it whole. Duplicating it into the
     * notification would be a second copy free to disagree with the first.
     */
    TableChangesRequested,

    /**
     * To every master of the table: an admin rewrote it - its text, catalogs, agenda or capacity
     * (ADR-06 §8). The parameters list the fields the admin actually changed ({@code changes}); an
     * edit that changed nothing sends nothing.
     *
     * <p>The rule behind this one and the four that follow is the same: whenever an admin changes
     * something that is not theirs, the people it belongs to are told (ADR-06 §8). The table keeps
     * working without them reading it, which is exactly why they would otherwise not notice - nothing
     * on their screen says the description they wrote is no longer the one people read.
     */
    TableEditedByAdmin,

    /**
     * To a master of the table: an admin paused it, and the master is not the admin (ADR-07 §2,
     * ADR-06 §8). It covers a direct pause and the approval of a pause another master asked for;
     * the master who asked hears it as {@link #ApprovalRequestApproved} instead. The reason is not
     * copied in: it is mandatory on the transition and the status history is where it is read whole
     * (ADR-23 §3).
     */
    TablePausedByAdmin,

    /**
     * To a master of the table: an admin resumed it (ADR-07 §2, ADR-06 §8). The players hear it as
     * {@link #TableResumed}.
     */
    TableResumedByAdmin,

    /**
     * To a master of the table: an admin cancelled it (ADR-07 §1, ADR-06 §8). The reason lives in the status
     * history, as for a pause. The players hear it as {@link #TableCanceled}.
     */
    TableCanceledByAdmin,

    /**
     * To everybody joined to the table - its live masters and its accepted players - except whoever
     * paused it: the table was paused (ADR-07 §2). A master receives {@link #TablePausedByAdmin}
     * instead when the one who paused it was an admin who does not run the table. The reason lives in
     * the status history.
     */
    TablePaused,

    /**
     * To everybody joined to the table except whoever resumed it: the table is back in play and its
     * pending sessions were re-laid (ADR-07 §2, ADR-08 §3). A master receives
     * {@link #TableResumedByAdmin} instead when an admin who does not run the table resumed it.
     */
    TableResumed,

    /**
     * To everybody joined to the table except whoever cancelled it: the table was cancelled (ADR-07 §1).
     * A master receives {@link #TableCanceledByAdmin} instead when an admin who does not run the table
     * cancelled it. The reason lives in the status history.
     */
    TableCanceled,

    /**
     * To everybody joined to the table except its Primary, who finished it: the run is over
     * (ADR-07 §7). Only the Primary finishes a table, so there is no by-admin variant.
     */
    TableFinished,

    /**
     * To everybody joined to the table except whoever changed it: its weekly agenda changed - a slot
     * was added, removed or lengthened (ADR-08 §2). It tells people the hours moved without naming
     * them; the table's screen shows the new week. An admin's edit tells the masters through
     * {@link #TableEditedByAdmin} instead, and only the players receive this one.
     */
    TableScheduleChanged,

    /**
     * To every master the table had: an admin removed it (ADR-03 §7, ADR-06 §8).
     *
     * <p>It links nowhere on purpose. A removed table is invisible on every read path (ADR-03 §7), so a
     * link would open a 404; the name in the message is the whole of what is left to tell.
     */
    TableDeletedByAdmin,

    /**
     * To whoever asked: an admin approved their request (ADR-16 §1).
     *
     * <p><b>The request itself notifies nobody</b> (ADR-16 §5, arquitectura §4.5: «los ítems de trabajo de
     * admin no se duplican como notificaciones»). The shared queue already shows what is waiting; a
     * notification per request would be the copy ADR-16 §5 exists to avoid. What needs a bell is the
     * <em>resolution</em>, and only for the person who asked: they are the one who cannot see the
     * other side of the wait.
     *
     * <p>It links to the person's own profile, not to {@code /admin/requests} - the requester is not
     * an admin and cannot open that screen.
     */
    ApprovalRequestApproved,

    /**
     * To whoever asked: an admin turned their request down (ADR-16 §1).
     *
     * <p>Separate from {@link #ApprovalRequestApproved} for the reason {@code TableApproved} and
     * {@code TableChangesRequested} are separate: being told yes and being told no are different
     * news, and one icon cannot carry both.
     *
     * <p>The reason is <b>not</b> copied in. It is already mandatory on the resolution and stored on
     * the request, which is where the person reads it in full; a copy here would be a second one free
     * to drift from the first (ADR-23 §3).
     */
    ApprovalRequestRejected
}
