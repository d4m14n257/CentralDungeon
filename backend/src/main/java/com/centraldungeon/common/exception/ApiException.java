package com.centraldungeon.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base of every error the application raises on purpose. {@code GlobalExceptionHandler} turns one of
 * these into an RFC 9457 {@code ProblemDetail}, so a service never has to know about HTTP to answer
 * correctly.
 *
 * <p>Sealed: the four subclasses below are the whole vocabulary of intentional failure, and keeping
 * it closed is what lets a {@code switch} over them stay exhaustive (arquitectura.md 2.4). Anything
 * that is not one of these is a bug, and a bug answers 500.
 */
public sealed abstract class ApiException extends RuntimeException
        permits NotFoundException, ConflictException, ForbiddenActionException, UnauthorizedException {

    /** The HTTP status this failure maps to. */
    private final HttpStatus status;

    /** A stable machine-readable code the frontend can branch on without parsing the message. */
    private final String errorCode;

    /**
     * @param status    the HTTP status this failure maps to
     * @param errorCode the stable code that travels in the {@code ProblemDetail}
     * @param message   the human-readable detail; it reaches the client, so it says what went wrong
     *                  without leaking anything internal
     */
    protected ApiException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
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
