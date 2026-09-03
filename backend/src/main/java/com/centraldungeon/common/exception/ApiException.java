package com.centraldungeon.common.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Base of every error the application raises on purpose. {@code GlobalExceptionHandler} turns one of
 * these into an RFC 9457 {@code ProblemDetail}, so a service never has to know about HTTP to answer
 * correctly.
 *
 * <p>Sealed: the five subclasses below are the whole vocabulary of intentional failure, and keeping
 * it closed is what lets a {@code switch} over them stay exhaustive (arquitectura.md 2.4). Anything
 * that is not one of these is a bug, and a bug answers 500.
 *
 * <p><b>The message is for whoever is reading a log, not for the person on the screen</b> (#197). It
 * is written in English like the rest of the code; what the user reads is rendered by the frontend
 * from {@link #getErrorCode()} and {@link #getErrorParams()}, in whichever language they picked.
 * Before #197 the clash message of #188 was written in Spanish precisely because it was shown
 * verbatim - that is what stopped being true.
 */
public sealed abstract class ApiException extends RuntimeException
        permits NotFoundException, ConflictException, ForbiddenActionException, UnauthorizedException, InvalidRequestException {

    /** The HTTP status this failure maps to. */
    private final HttpStatus status;

    /** A stable machine-readable code the frontend can branch on without parsing the message. */
    private final String errorCode;

    /**
     * The values the frontend's translated message needs, keyed by placeholder name.
     *
     * <p>An open map rather than a named record, unlike everything else that crosses HTTP (regla
     * dura 3): this is not a response body but an RFC 9457 <em>extension member</em>, which is the
     * mechanism the standard defines for exactly this, and every error carries different values.
     * Naming a record per error code would be one type per message and none of them reused.
     */
    private final Map<String, String> errorParams;

    /**
     * @param status    the HTTP status this failure maps to
     * @param errorCode the stable code that travels in the {@code ProblemDetail}
     * @param message   the developer-facing detail, in English. It reaches the client but is not what
     *                  the client shows (#197)
     */
    protected ApiException(HttpStatus status, String errorCode, String message) {
        this(status, errorCode, message, Map.of());
    }

    /**
     * @param status      the HTTP status this failure maps to
     * @param errorCode   the stable code that travels in the {@code ProblemDetail}
     * @param message     the developer-facing detail, in English
     * @param errorParams the values the translated message needs, keyed by placeholder name
     */
    protected ApiException(HttpStatus status, String errorCode, String message, Map<String, String> errorParams) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.errorParams = Map.copyOf(errorParams);
    }

    /**
     * Returns the values the frontend's translated message needs.
     *
     * @return the placeholder values, never null and empty for an error whose message has none
     */
    public Map<String, String> getErrorParams() {
        return errorParams;
    }

    /**
     * Returns the HTTP status this failure maps to.
     *
     * @return the status, never null
     */
    public HttpStatus getStatus() {
        return status;
    }

    /**
     * Returns the stable code that travels in the {@code ProblemDetail}.
     *
     * @return the error code, never null
     */
    public String getErrorCode() {
        return errorCode;
    }
}
