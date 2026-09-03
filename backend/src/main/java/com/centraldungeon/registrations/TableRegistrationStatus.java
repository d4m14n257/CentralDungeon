package com.centraldungeon.registrations;

/**
 * E1 subset (plan-desarrollo.md): Blocked (veto, #39) is out of scope until that rule is built.
 *
 * <p>Deleted is the soft-delete marker of #25, not a decision anyone takes: it is what a
 * registration becomes when its table is deleted (#175). No response ever carries it - every read
 * filters it out - which is why the TypeScript union mirrors only the other three.
 */
public enum TableRegistrationStatus {
    Candidate,
    Player,
    Rejected,
    Deleted
}
