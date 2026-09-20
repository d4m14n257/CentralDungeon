package com.centraldungeon.common.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * The request is well formed and the actor is allowed, but the state of the system says no: an
 * illegal transition, a name already taken, an invariant that would break. Answers 409.
 */
public final class ConflictException extends ApiException {

    /** The code every conflict carries unless it is one the frontend has to tell apart. */
    private static final String DEFAULT_ERROR_CODE = "CONFLICT";

    /**
     * A schedule clash of #178. It gets a code of its own because it is the one conflict the
     * interface has to <b>explain</b> rather than merely report: the message names the table the
     * agenda collides with, and a screen that cannot tell this 409 from any other has nothing to
     * show but a generic failure (principio 2 de frontend-diseno.md 1).
     */
    public static final String SCHEDULE_CONFLICT = "SCHEDULE_CONFLICT";

    /**
     * The same clash of #178, seen from the other side: a master accepting somebody whose own
     * commitments collide with this table (R3).
     *
     * <p>It gets a code of its own and not {@link #SCHEDULE_CONFLICT} because the sentence is about
     * a <b>third person</b>, not about the reader - "it clashes with a table where you are already
     * committed" is simply false when what clashed is the candidate's week. Two situations, two
     * messages, and the backend picks neither of them (#197).
     */
    public static final String CANDIDATE_SCHEDULE_CONFLICT = "CANDIDATE_SCHEDULE_CONFLICT";

    /**
     * The placeholder the clash message fills in: the name of the table the agenda collides with.
     *
     * <p>It travels as data rather than baked into a sentence (#197), which is what lets the same
     * clash read as "se pisa con «La Cripta»" or "clashes with “La Cripta”" depending on the
     * language the reader chose.
     */
    public static final String PARAM_OTHER_TABLE_NAME = "otherTableName";

    /**
     * The operation would leave the platform with no active Owner. The one global invariant of roles
     * (fase-3-admin-owner.md 3): a system where nobody can grant Owner has no way back from the
     * inside, and MySQL cannot express the rule, so the service is the only place it can live.
     */
    public static final String LAST_OWNER = "LAST_OWNER";

    /**
     * An owner taking their own Owner role away. Separate from {@link #LAST_OWNER} on purpose: the
     * two are different sentences even when they fire on the same person, and the more specific one
     * is checked first so the reader is told what they actually did.
     */
    public static final String CANNOT_REVOKE_OWN_OWNER = "CANNOT_REVOKE_OWN_OWNER";

    /** Blocking an account that cannot be blocked because it is not usable already (#84). */
    public static final String USER_ALREADY_BLOCKED = "USER_ALREADY_BLOCKED";

    /** Unblocking an account that is not blocked. A Deleted account answers this too: F3.1 never moves one back to Allowed. */
    public static final String USER_NOT_BLOCKED = "USER_NOT_BLOCKED";

    /**
     * A second request of the same type from the same person while the first is still Pending (#42).
     *
     * <p>Its own code because the screen has something specific to do with it: stop offering the
     * button and show the pending request instead. A button whose only possible answer is this 409
     * is a button that should not be there (principio 2 de frontend-diseno.md 1) - which is also why
     * {@code GET /requests/mine} exists.
     */
    public static final String REQUEST_ALREADY_PENDING = "REQUEST_ALREADY_PENDING";

    /**
     * Approving or rejecting a request that is no longer Pending. A resolution is not re-resolved,
     * the same shape the table's state machine has.
     *
     * <p>The likeliest way to see it is two admins answering the same request at once, and the
     * sentence the loser needs is "somebody already answered this", not a generic conflict.
     */
    public static final String REQUEST_ALREADY_RESOLVED = "REQUEST_ALREADY_RESOLVED";

    /**
     * The entity a request points at is gone (#78, #126).
     *
     * <p>The direct consequence of a polymorphic reference with no foreign key: the row outlives what
     * it points at, which #126 accepts on purpose - «una solicitud sobre una mesa borrada sigue
     * siendo un hecho» - but resolving it would be acting on a ghost. Its own code because it is the
     * one 409 here that is nobody's fault and that no retry fixes.
     */
    public static final String REQUEST_ENTITY_GONE = "REQUEST_ENTITY_GONE";

    /**
     * Asking for the Master role while already holding it.
     *
     * <p>A 409 rather than a request an admin has to open, read and reject in order to discover it
     * was unnecessary. Its own code so the screen can say the true thing - "you already have it" -
     * instead of reporting a conflict the reader cannot interpret.
     */
    public static final String MASTER_ROLE_ALREADY_HELD = "MASTER_ROLE_ALREADY_HELD";

    /**
     * Acting on an item of the shared admin tray that <b>another admin holds</b> (#100) - reserving
     * it, giving it back, or resolving it.
     *
     * <p>Its own code because it is the one 409 here whose sentence names <b>somebody else</b>: the
     * reader has to be told the item is taken, not that "a conflict occurred". It is also the answer
     * to the race the reservation exists to stop, so the loser of two admins clicking at the same
     * instant gets a sentence instead of a generic failure.
     *
     * <p>There is deliberately <b>no counterpart for "you have not reserved it"</b>. An unreserved
     * item is nobody's, and resolving it is an implicit claim: {@code /admin/requests} resolves
     * without ever passing through the tray, and a rule that refused that would make the tray
     * mandatory for a flow that was never built to use it. See {@code AdminQueueClaimRule} for the
     * whole reasoning.
     */
    public static final String ITEM_ALREADY_CLAIMED = "ITEM_ALREADY_CLAIMED";

    /**
     * Vetoing somebody who is already vetoed on this table (#39).
     *
     * <p>Its own code because the screen has something to do with it: the row is already showing the
     * veto and its reason, so the answer is "refresh, it is done", not a failure. The likeliest way
     * to see it is two masters pressing at the same instant - the loser of that race should not be
     * told something went wrong.
     */
    public static final String REGISTRATION_ALREADY_BLOCKED = "REGISTRATION_ALREADY_BLOCKED";

    /**
     * Lifting a veto on somebody who is not vetoed (#39).
     *
     * <p>The mirror of {@link #REGISTRATION_ALREADY_BLOCKED}, and it exists for the same reason a
     * veto is reversible at all: the two buttons sit on the same row, and a screen that cannot tell
     * these two 409s apart cannot say which of them the reader just hit.
     */
    public static final String REGISTRATION_NOT_BLOCKED = "REGISTRATION_NOT_BLOCKED";

    /**
     * Asking for a pause of a table that is already waiting on one (#32).
     *
     * <p>Its own code for the same reason as {@link #REQUEST_ALREADY_PENDING}, which it deliberately
     * does not reuse: that one is about the person - «you already asked for this» - and this one is
     * about the <b>table</b>. A co-master can hit it without ever having asked for anything, and
     * being told they have a request open would simply be false.
     */
    public static final String PAUSE_ALREADY_REQUESTED = "PAUSE_ALREADY_REQUESTED";

    /**
     * @param message what state made the request impossible, in English and for a log (#197)
     */
    public ConflictException(String message) {
        this(message, DEFAULT_ERROR_CODE);
    }

    /**
     * @param message   what state made the request impossible, in English
     * @param errorCode the stable code the frontend branches on. Use {@link #SCHEDULE_CONFLICT} for
     *                  a clash; anything else has no reason not to be the default
     */
    public ConflictException(String message, String errorCode) {
        super(HttpStatus.CONFLICT, errorCode, message);
    }

    /**
     * @param message     what state made the request impossible, in English
     * @param errorCode   the stable code the frontend branches on
     * @param errorParams the values the translated message needs, keyed by placeholder name (#197)
     */
    public ConflictException(String message, String errorCode, Map<String, String> errorParams) {
        super(HttpStatus.CONFLICT, errorCode, message, errorParams);
    }
}
