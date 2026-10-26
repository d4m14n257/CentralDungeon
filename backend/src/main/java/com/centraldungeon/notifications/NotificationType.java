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
     * other pending applications clash, and {@code TableScheduleService} sends it when a master
     * moves an agenda under people who were already signed up. In both cases the system says what
     * happened and the person decides - the same principle as ADR-15 §2.
     */
    ScheduleConflict,

    /**
     * To the players of a table (not its candidates, who cannot see sessions): one of its sessions moved, or the whole pending calendar
     * was re-laid after a pause (ADR-08 §3).
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
     * the bell says it came out. It does not distinguish an untouched approval from one the admin
     * corrected on the way - {@link #TableApprovedWithChanges} is that one, because being told your
     * text was edited is different news from being told it passed.
     */
    TableApproved,

    /**
     * The admin corrected the draft themselves and approved it (ADR-18 §3).
     *
     * <p>Separate from {@link #TableApproved} on purpose: the table is open either way, but somebody
     * else's hand is now in what the master wrote, and finding that out by re-reading their own table
     * is worse than being told.
     */
    TableApprovedWithChanges,

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
     * (ADR-06 §8).
     *
     * <p>The rule behind this one and the four that follow is the same: whenever an admin changes
     * something that is not theirs, the people it belongs to are told (ADR-06 §8). The table keeps
     * working without them reading it, which is exactly why they would otherwise not notice - nothing
     * on their screen says the description they wrote is no longer the one people read.
     */
    TableEditedByAdmin,

    /**
     * To every master of the table: an admin paused it directly, without the master having asked
     * (ADR-07 §2, ADR-06 §8). A pause the master requested and an admin approved reaches them as
     * {@link #ApprovalRequestApproved} instead. The reason is not copied in: it is mandatory on the
     * transition and the status history is where it is read whole (ADR-23 §3).
     */
    TablePausedByAdmin,

    /**
     * To every master of the table: an admin resumed it (ADR-07 §2, ADR-06 §8). The players hear about it
     * separately, through {@link #SessionScheduled}, because what changes for them is the calendar.
     */
    TableResumedByAdmin,

    /**
     * To every master of the table: an admin cancelled it (ADR-07 §1, ADR-06 §8). The reason lives in the status
     * history, as for a pause.
     */
    TableCanceledByAdmin,

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
