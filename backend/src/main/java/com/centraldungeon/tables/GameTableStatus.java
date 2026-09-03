package com.centraldungeon.tables;

/**
 * The full 9-state machine (modelo-datos.md #27, #32, #72), plus Deleted. PauseRequested exists here
 * but no endpoint produces it yet - it is only reachable once approval_requests lands (F3,
 * plan-desarrollo.md) and a master can ask for a pause instead of an admin pausing directly.
 *
 * <p><b>Deleted is not a lifecycle state.</b> It is the soft-delete marker of #25, paired with
 * deleted_at, and only a table that was never public can reach it (decisiones.md #175): every
 * other ending is Canceled, which is history and stays visible. Nothing transitions out of Deleted,
 * and every read filters it out.
 */
public enum GameTableStatus {

    /** Created by an admin who is not going to run it; waiting for masters to be assigned (#72). */
    Unassigned,

    /** The master's draft. Visible only to them, until they submit it for review (#27). */
    Preparation,

    /** An admin sent it back with a reason. The master edits and resubmits. */
    ChangesRequested,

    /** Approved and public: it shows in the explorer and accepts applications. */
    Opened,

    /** Being played. Sessions were materialized when it opened (#26, #33). */
    InProgress,

    /** The master asked for a pause and an admin has not answered yet. Unreachable until F3. */
    PauseRequested,

    /** Paused: the agenda is frozen and pending sessions stop showing (#32, #33). */
    Pause,

    /** Played to its end. {@code closed_at} is stamped here (#180). */
    Finished,

    /** Ended early, with a reason on the record. {@code closed_at} is stamped here too (#180). */
    Canceled,

    /** Soft-delete marker, not a lifecycle state - see the class note above. */
    Deleted
}
