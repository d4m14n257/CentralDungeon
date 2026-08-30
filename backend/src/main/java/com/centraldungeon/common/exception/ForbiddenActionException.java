package com.centraldungeon.common.exception;

import org.springframework.http.HttpStatus;

public final class ForbiddenActionException extends ApiException {

    public ForbiddenActionException(String message) {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }
}
