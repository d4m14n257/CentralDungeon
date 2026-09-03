package com.centraldungeon.common.exception;

import org.springframework.http.HttpStatus;

/**
 * The resource does not exist, or must be treated as if it did not - a vetoed table answers this and
 * not a 403, so that a 403 never confirms what a 404 denies. Answers 404.
 */
public final class NotFoundException extends ApiException {

    /**
     * @param message what was looked for. Naming the kind and the id makes a log line readable
     *                without the stack trace
     */
    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}
