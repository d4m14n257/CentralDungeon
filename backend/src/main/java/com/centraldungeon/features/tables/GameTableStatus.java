package com.centraldungeon.features.tables;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * The full 10-state machine, plus Deleted. PauseRequested is where a table waits
 * while an admin answers a master's request for a pause; an admin pausing directly skips it.
 *
 * <p><b>Deleted is not a lifecycle state.</b> It is the soft-delete marker, paired with
 * deleted_at, and only a table that was never public can reach it: every
 * other ending is Canceled, which is history and stays visible. Nothing transitions out of Deleted,
 * and every read filters it out.
 */
public enum GameTableStatus {

    /**
     * The master is still writing it and nobody else has seen it.
     *
     * <p>Where a master's table is born. It is theirs alone here: it appears in no explorer, in no
     * admin list, and no admin is told it exists — sending it to review is a deliberate act, and until
     * that happens the draft can be fixed or thrown away without anybody having looked.
     *
     * <p>A table an <em>admin</em> creates does not pass through here: it is born {@link #Unassigned}.
     * An admin has nobody to wait for — reviewing is their own job — so a draft state would be
     * a step that ends where it started.
     */
    Draft,

    /** Created by an admin who is not going to run it; waiting for masters to be assigned. */
    Unassigned,

    /**
     * Sent to review and waiting on an admin.
     *
     * <p><b>It is no longer the master's to edit.</b> Somebody else is reading it, and moving it while
     * they read is how a reviewer approves something that no longer exists. It comes back to the
     * master as {@link #ChangesRequested}, or it opens.
     */
    Preparation,

    /** An admin sent it back with a reason. The master edits and resubmits. */
    ChangesRequested,

    /** Approved and public: it shows in the explorer and accepts applications. */
    Opened,

    /** Being played. Sessions were materialized when it opened. */
    InProgress,

    /** The master asked for a pause and an admin has not answered yet. */
    PauseRequested,

    /** Paused: the agenda is frozen and pending sessions stop showing. */
    Pause,

    /** Played to its end. {@code closed_at} is stamped here. */
    Finished,

    /** Ended early, with a reason on the record. {@code closed_at} is stamped here too. */
    Canceled,

    /** Soft-delete marker, not a lifecycle state - see the class note above. */
    Deleted;

    /**
     * Whether a table in this status has never been public: nobody but its masters and the admins has
     * seen it. None of these statuses comes back once a table opens.
     *
     * <p>It draws two lines with one answer: such a table can still be deleted instead of cancelled,
     * and its public detail is a 404 for everybody else - the same 404 as a deleted one.
     *
     * @return true for Draft, Unassigned, Preparation and ChangesRequested
     */
    public boolean wasNeverPublic() {
        return this == Draft || this == Unassigned || this == Preparation || this == ChangesRequested;
    }

    /**
     * Looks a status up by name, forgivingly - this is the search box's door, not a body's.
     *
     * <p>Added with {@code /table_status}. The same shape and the same contract
     * {@code ApprovalStatus.fromName} has: a request body is parsed strictly by Jackson, a
     * {@code ?q=} has to survive being typed into, so this one is case-insensitive and an unknown
     * name is nobody rather than a 400.
     *
     * @param name a status name, in any case
     * @return the matching constant, or empty. Empty is a normal answer: an unknown state inside
     *         {@code ?q=} matches no table rather than refusing the search
     */
    public static Optional<GameTableStatus> fromName(String name) {
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(status -> status.name().toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst();
    }
}
