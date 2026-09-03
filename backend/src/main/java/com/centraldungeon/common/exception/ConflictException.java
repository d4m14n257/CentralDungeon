package com.centraldungeon.common.exception;

import org.springframework.http.HttpStatus;

/**
 * The request is well formed and the actor is allowed, but the state of the system says no: an
 * illegal transition, a name already taken, an invariant that would break. Answers 409.
 */
public final class ConflictException extends ApiException {

    /**
     * @param message what state made the request impossible. It reaches the user, so it says which
     *                rule was hit and, where there is one, the way forward
     */
    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, "CONFLICT", message);
    }
}
