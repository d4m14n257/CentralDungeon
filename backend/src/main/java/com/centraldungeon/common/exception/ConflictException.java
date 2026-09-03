package com.centraldungeon.common.exception;

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
     * @param message what state made the request impossible. It reaches the user, so it says which
     *                rule was hit and, where there is one, the way forward
     */
    public ConflictException(String message) {
        this(message, DEFAULT_ERROR_CODE);
    }

    /**
     * @param message   what state made the request impossible
     * @param errorCode the stable code the frontend branches on. Use {@link #SCHEDULE_CONFLICT} for
     *                  a clash; anything else has no reason not to be the default
     */
    public ConflictException(String message, String errorCode) {
        super(HttpStatus.CONFLICT, errorCode, message);
    }
}
