package com.centraldungeon.tables;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * The full 10-state machine (arquitectura §4, ADR-07 §1, §2, ADR-06 §7), plus Deleted. PauseRequested exists here
 * but no endpoint produces it yet - it is only reachable once approval_requests lands (F3,
 * plan-desarrollo.md) and a master can ask for a pause instead of an admin pausing directly.
 *
 * <p><b>Deleted is not a lifecycle state.</b> It is the soft-delete marker of ADR-03 §7, paired with
 * deleted_at, and only a table that was never public can reach it (ADR-07 §6): every
 * other ending is Canceled, which is history and stays visible. Nothing transitions out of Deleted,
 * and every read filters it out.
 */
public enum GameTableStatus {

    /**
     * The master is still writing it and nobody else has seen it (ADR-07 §1).
     *
     * <p>Where a master's table is born. It is theirs alone here: it appears in no explorer, in no
     * admin list, and no admin is told it exists — sending it to review is a deliberate act, and until
     * that happens the draft can be fixed or thrown away without anybody having looked.
     *
     * <p>A table an <em>admin</em> creates does not pass through here: it is born {@link #Unassigned}
     * (ADR-06 §7). An admin has nobody to wait for — reviewing is their own job — so a draft state would be
     * a step that ends where it started.
     */
    Draft,

    /** Created by an admin who is not going to run it; waiting for masters to be assigned (ADR-06 §7). */
    Unassigned,

    /**
     * Sent to review and waiting on an admin (ADR-07 §1).
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

    /** Being played. Sessions were materialized when it opened (ADR-08 §3). */
    InProgress,

    /** The master asked for a pause and an admin has not answered yet. Unreachable until F3. */
    PauseRequested,

    /** Paused: the agenda is frozen and pending sessions stop showing (ADR-07 §2, ADR-08 §3). */
    Pause,

    /** Played to its end. {@code closed_at} is stamped here (ADR-07 §7). */
    Finished,

    /** Ended early, with a reason on the record. {@code closed_at} is stamped here too (ADR-07 §7). */
    Canceled,

    /** Soft-delete marker, not a lifecycle state - see the class note above. */
    Deleted;

    /**
     * Looks a status up by name, forgivingly - this is the search box's door, not a body's.
     *
     * <p>Added with {@code /table_status} (F3.3). The same shape and the same contract
     * {@code ApprovalStatus.fromName} has: a request body is parsed strictly by Jackson, a
     * {@code ?q=} has to survive being typed into, so this one is case-insensitive and an unknown
     * name is nobody rather than a 400 (arquitectura §2.5).
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
