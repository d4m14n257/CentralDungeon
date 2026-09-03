package com.centraldungeon.tables;

/**
 * The full 9-state machine (modelo-datos.md #27, #32, #72), plus Deleted. PauseRequested exists here
 * but no endpoint produces it yet - it is only reachable once approval_requests lands (E2
 * sub-rebanada 2) and a master can ask for a pause instead of an admin pausing directly.
 *
 * <p><b>Deleted is not a lifecycle state.</b> It is the soft-delete marker of #25, paired with
 * deleted_at, and only a table that was never public can reach it (decisiones.md #175): every
 * other ending is Canceled, which is history and stays visible. Nothing transitions out of Deleted,
 * and every read filters it out.
 */
public enum GameTableStatus {
    Unassigned,
    Preparation,
    ChangesRequested,
    Opened,
    InProgress,
    PauseRequested,
    Pause,
    Finished,
    Canceled,
    Deleted
}
