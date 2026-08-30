package com.centraldungeon.common.exception;

import org.springframework.http.HttpStatus;

public sealed abstract class ApiException extends RuntimeException
        permits NotFoundException, ConflictException, ForbiddenActionException, UnauthorizedException {

    private final HttpStatus status;
    private final String errorCode;

    protected ApiException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
