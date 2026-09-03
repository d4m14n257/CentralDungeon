package com.centraldungeon.common.exception;

import org.springframework.http.HttpStatus;

/**
 * There is no usable identity behind the request: no token, an expired one, or a refresh that no
 * longer holds. Answers 401, which is the client's signal to refresh once and then to log in again.
 */
public final class UnauthorizedException extends ApiException {

    /**
     * @param message why the identity could not be established
     */
    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }
}
