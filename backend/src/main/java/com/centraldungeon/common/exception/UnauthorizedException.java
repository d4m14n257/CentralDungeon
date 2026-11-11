package com.centraldungeon.common.exception;

import org.springframework.http.HttpStatus;

/**
 * There is no usable identity behind the request: no token, an expired one, or a refresh that no
 * longer holds. Answers 401, which is the client's signal to refresh once and then to log in again.
 */
public final class UnauthorizedException extends ApiException {

    /**
     * The credential is valid but its account is blocked. Still a 401, but with a code of its own
     * because the client must not refresh and retry: it has to tell the person their account is
     * blocked instead of sending them to log in again.
     */
    public static final String ACCOUNT_BLOCKED = "ACCOUNT_BLOCKED";

    /**
     * @param message why the identity could not be established
     */
    public UnauthorizedException(String message) {
        this(message, "UNAUTHORIZED");
    }

    /**
     * @param message   why the identity could not be established
     * @param errorCode the stable code the frontend branches on, such as {@link #ACCOUNT_BLOCKED}
     */
    public UnauthorizedException(String message, String errorCode) {
        super(HttpStatus.UNAUTHORIZED, errorCode, message);
    }
}
