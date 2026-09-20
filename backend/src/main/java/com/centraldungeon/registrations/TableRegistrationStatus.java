package com.centraldungeon.registrations;

/**
 * Where an application stands.
 *
 * <p><b>{@code Blocked} came in with F3.4</b> and the note that used to sit here - «E1 subset:
 * Blocked (veto, #39) is out of scope until that rule is built» - was true for three phases and is
 * not any more. The rule is built: a table's {@code Primary} vetoes somebody, a {@code Secondary}
 * asks the {@code Primary} for it, and the whole thing is reversible (#39).
 *
 * <p>Deleted is the soft-delete marker of #25. It has two ways in: the cascade of a deleted table
 * (#175), and - since F1.2 - the applicant withdrawing their own application, which is the way out
 * R4's clash notice needs to leave open (#178). No response ever carries it, because every read
 * filters it out.
 *
 * <p><b>{@code Blocked} is not like {@code Deleted} in that last respect</b>, and the difference is
 * deliberate: the master who vetoed somebody keeps seeing the row - with who vetoed it and when -
 * because a veto that vanishes from the screen is not reversible in practice. What the <em>vetoed
 * person</em> sees is nothing at all: every read of that table answers them 404 (#29).
 */
public enum TableRegistrationStatus {

    /** Applied, waiting on a master. Counts as an active registration for the one-per-pair rule (#28). */
    Candidate,

    /** Accepted. Counts towards max_players (#34) and against the pair rule too. */
    Player,

    /** Turned down, with the reason in {@code registration_rejections}. */
    Rejected,

    /**
     * Vetoed from this table by its {@code Primary} (#39).
     *
     * <p>Not a rejection and not a soft delete: it is the state that makes the whole table stop
     * existing for this person (#29). It is the value every read of a table is measured against -
     * {@code TableVisibilityService} is the one place that asks, and it answers 404 rather than 403
     * everywhere, because a 403 would confirm what the 404 denies.
     *
     * <p>It is reversible, and both directions leave a row in {@code registration_status_changes}
     * with the reason: #39 wants a pattern of impulsive vetoes to be visible, and a single
     * {@code blocked_reason} column could never tell that the veto was lifted, or how often.
     */
    Blocked,

    /** Soft-delete marker - see the class note above. */
    Deleted
}
