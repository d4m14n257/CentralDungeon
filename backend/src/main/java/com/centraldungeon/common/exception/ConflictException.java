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
     * The placeholder the clash message fills in: the name of the table the agenda collides with.
     *
     * <p>It travels as data rather than baked into a sentence (#197), which is what lets the same
     * clash read as "se pisa con «La Cripta»" or "clashes with “La Cripta”" depending on the
     * language the reader chose.
     */
    public static final String PARAM_OTHER_TABLE_NAME = "otherTableName";

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
