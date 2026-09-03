package com.centraldungeon.common.exception;

import org.springframework.http.HttpStatus;

/**
 * The actor is authenticated but has no business doing this - typically because the resource is not
 * theirs (#121). Answers 403.
 *
 * <p>Not to be used to hide a resource's existence: a table someone is vetoed from answers 404, not
 * this (decisiones.md, ciclo de vida de la mesa).
 */
public final class ForbiddenActionException extends ApiException {

    /**
     * @param message what the actor is not allowed to do
     */
    public ForbiddenActionException(String message) {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }
}
