package com.centraldungeon.tables;

/**
 * E1 only implements the reduced slice of the full 9-state machine (modelo-datos.md #27, #32, #72):
 * Preparation to Opened to InProgress. Unassigned/ChangesRequested/PauseRequested/Pause/Finished/
 * Canceled all depend on the admin approval workflow (approval_requests), which is out of E1's
 * scope by plan-desarrollo.md's own design - they are added when that etapa lands, not before.
 */
public enum GameTableStatus {
    Preparation,
    Opened,
    InProgress
}
