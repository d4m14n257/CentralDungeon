package com.centraldungeon.features.queue;

import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.features.users.User;
import org.jspecify.annotations.Nullable;

/**
 * An item another admin holds is not yours to resolve - the whole rule, in one place.
 *
 * <p>The rule was first implemented as "resolving an item requires having reserved it". The
 * sentence above is the corrected one; the paragraph after it says what the
 * stricter reading broke, because the wrong version is the kind that looks right on paper.
 *
 * <p><b>What the rule protects, precisely.</b> The shared queue buys one thing: <em>if one admin takes it, it
 * drops for everybody else</em>. So what resolving must refuse is acting on an item <b>somebody else
 * holds</b> - a stale link, a second tab, a tray that has not refreshed. An item nobody holds is not
 * that situation, and resolving it is an implicit claim.
 *
 * <p><b>Why it is not "you must claim first".</b> That stricter reading was tried and it broke
 * {@code /admin/requests}, which ships with Approve and Reject and <em>no way to reserve
 * anything</em>: every resolution from that screen answered 409 for a reservation the screen could
 * not offer. Two designs had collided - requests have their own screen, while the shared queue assumed
 * the tray was the only place anything gets resolved - and the stricter rule silently made the tray mandatory for
 * a flow that never went through it. Refusing only what another admin holds keeps both screens
 * working and still gives the shared queue everything it asks for.
 *
 * <p>Correctness does not rest on this check either way: two admins resolving the same unclaimed row
 * at once are serialized by the pessimistic lock on the row, and the loser is told it was already
 * resolved. This rule is about not stepping on a colleague's work, not about consistency.
 *
 * <p><b>Four callers, one sentence.</b> {@code ApprovalService.approve} and {@code reject},
 * {@code GameTableService.approve} and {@code requestChanges}. Written here rather than four times
 * because the failure mode of a copied rule is that one copy quietly stops matching the others - and
 * the copy that stops matching is a door left open.
 *
 * <p><b>A static method and not a bean</b>, deliberately. The rule needs nothing but the row it is
 * about and the actor: no repository, no clock, no configuration. Making it a {@code @Service} would
 * put {@code approvals} and {@code tables} in a bean cycle with {@code queue} - which already
 * reads both of them - to buy an injection point nobody needs.
 */
public final class QueueClaimRule {

    /** Utility class: it holds no state and is never instantiated. */
    private QueueClaimRule() {
    }

    /**
     * Refuses to resolve an item another admin has reserved.
     *
     * <p>Unreserved is allowed and is the common case: somebody working from {@code /admin/requests}
     * never passes through the tray. What is refused is the item with somebody else's name on it,
     * and the reader's next step is to leave it alone - it is being handled.
     *
     * @param claimedBy who holds the reservation, or null when nobody does
     * @param actorId   the actor, always from the token
     * @param what      how to name the item in the log line - "request abc" or "table abc". English
     *                  and for a log: the sentence the reader sees is written by the frontend from
     *                  the error code
     * @throws ConflictException 409 {@code ITEM_ALREADY_CLAIMED} when another admin holds it
     */
    public static void requireNotHeldByAnother(@Nullable User claimedBy, String actorId, String what) {
        if (claimedBy == null || claimedBy.getId().equals(actorId)) {
            return;
        }
        throw new ConflictException(
                "Cannot resolve " + what + ": another admin reserved it from the shared queue",
                ConflictException.ITEM_ALREADY_CLAIMED);
    }
}
