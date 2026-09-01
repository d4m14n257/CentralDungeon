package com.centraldungeon.tables;

/**
 * The full 9-state machine (modelo-datos.md #27, #32, #72). GameTableService enforces which
 * transitions are legal; every other pair returns 409. PauseRequested exists here but no endpoint
 * produces it yet - it is only reachable once approval_requests lands (E2 sub-rebanada 2) and a
 * master can ask for a pause instead of an admin pausing directly.
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
    Canceled
}
