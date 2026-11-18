package com.centraldungeon.registrations;

/**
 * Where an application stands.
 *
 * <p>{@code Blocked} is the veto. Nobody vetoes directly: any master of the table asks for it with a
 * reason, an admin approves the request, and the {@code Primary} or an admin can lift it again.
 *
 * <p>{@code Deleted} is the soft-delete marker. It has two ways in: the cascade of a deleted table, and
 * the applicant withdrawing their own application - which is also the way out a schedule clash notice
 * needs to leave open. No response ever carries it, because every read filters it out.
 *
 * <p><b>{@code Blocked} is not like {@code Deleted} in that last respect</b>, and the difference is
 * deliberate: the table's masters keep seeing the row - with who asked for the veto, who approved it,
 * and why - because a veto that vanishes from the screen is not reversible in practice. What the
 * <em>vetoed person</em> sees is nothing at all: every read of that table answers them 404.
 */
public enum TableRegistrationStatus {

    /** Applied, waiting on a master. Counts as an active registration for the one-per-pair rule. */
    Candidate,

    /** Accepted. Counts towards max_players and against the one-per-pair rule too. */
    Player,

    /** Turned down, with the reason in {@code registration_rejections}. */
    Rejected,

    /**
     * Vetoed from this table: a master asked for it and an admin approved the request. Only a
     * {@code Player} is vetoed - a candidate is rejected instead.
     *
     * <p>Not a rejection and not a soft delete: it is the state that makes the whole table stop
     * existing for this person. It is the value every read of a table is measured against -
     * {@code TableVisibilityService} is the one place that asks, and it answers 404 rather than 403
     * everywhere, because a 403 would confirm what the 404 denies.
     *
     * <p>It is reversible, and both directions leave a row in {@code registration_status_changes}
     * with the reason: a pattern of impulsive vetoes has to be visible, and a single
     * {@code blocked_reason} column could never tell that the veto was lifted, or how often.
     */
    Blocked,

    /** Soft-delete marker - see the class note above. */
    Deleted
}
