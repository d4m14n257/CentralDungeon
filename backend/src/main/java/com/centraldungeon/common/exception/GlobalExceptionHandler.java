package com.centraldungeon.common.exception;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Turns every exception that escapes a controller into an RFC 9457 {@code ProblemDetail}
 * (arquitectura.md 2.5). It is the reason a service can throw a {@link ApiException} without knowing
 * anything about HTTP.
 *
 * <p>Two rules the old project broke and this class enforces: an error is never a bare string, and
 * there is no catch-all status - the Node backend used 418 as a generic error, which told a client
 * nothing it could branch on.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Everything the application refuses on purpose: 404, 409, 403, 401.
     *
     * <p>{@code errorParams} rides along when the failure has values its message needs (#197). The
     * {@code detail} is still sent, in English, but it is for a log: what the person reads is
     * rendered by the frontend from the code and these values, in the language they chose.
     *
     * @param exception the intentional failure, carrying its own status, code and parameters
     * @return the problem detail, with {@code errorCode} for the client to branch on
     */
    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.getStatus(), exception.getMessage());
        problem.setProperty("errorCode", exception.getErrorCode());
        if (!exception.getErrorParams().isEmpty()) {
            problem.setProperty("errorParams", exception.getErrorParams());
        }
        return problem;
    }

    /**
     * A {@code @PreAuthorize} that did not pass.
     *
     * <p>The detail is deliberately generic: which role was missing is not something to tell whoever
     * was denied.
     *
     * @param exception what Spring Security rejected. Logged, never echoed
     * @return a 403 problem detail
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied");
        problem.setProperty("errorCode", "FORBIDDEN");
        return problem;
    }

    /**
     * Jakarta validation on a request body.
     *
     * <p>It answers with {@code fieldErrors} - field name and message - so the form can put each
     * message under the input that caused it instead of showing one banner for the whole submit.
     *
     * @param exception the binding failure
     * @return a 400 problem detail carrying the per-field errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setProperty("errorCode", "VALIDATION_ERROR");
        problem.setProperty(
                "fieldErrors",
                exception.getBindingResult().getFieldErrors().stream()
                        .map(error -> new FieldErrorDetail(error.getField(), error.getDefaultMessage()))
                        .toList());
        return problem;
    }

    /**
     * A path variable or query parameter that does not convert - {@code /admin/catalogs/colors},
     * {@code ?status=Nope}. Without this it would fall through to handleUnexpected and answer 500 to
     * what is plainly a bad request.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Invalid value for '" + exception.getName() + "'");
        problem.setProperty("errorCode", "VALIDATION_ERROR");
        return problem;
    }

    /**
     * An upload bigger than what the servlet container accepts.
     *
     * <p>Without this it would fall through to handleUnexpected and answer 500 to somebody who simply
     * picked a large file - a "the site is broken" where the truth is "that file is too big". It
     * carries {@code FILE_TOO_LARGE}, the same code {@code FileService} raises when the application's
     * own cap is the one that refuses, so the frontend writes one sentence for both (#197).
     *
     * <p>The container's limit sits slightly above {@code app.storage.max-file-size} on purpose
     * (application.yml), so in normal use it is the application that answers and this stays the
     * backstop for a request that lied about its size. That is why the limit is not in the parameters
     * here: this handler does not know it, and it is not the number the person needs anyway.
     *
     * @param exception the container's complaint
     * @return a 400 problem detail carrying {@code FILE_TOO_LARGE}
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleUploadTooLarge(MaxUploadSizeExceededException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The uploaded file is over the limit");
        problem.setProperty("errorCode", "FILE_TOO_LARGE");
        return problem;
    }

    /**
     * The last resort: anything not handled above is a bug, and a bug is a 500.
     *
     * <p>The message never reaches the client. A stack trace or a SQL fragment in a response body is
     * how internals leak.
     *
     * @param exception whatever escaped
     * @return a 500 problem detail with no detail about the cause
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
        problem.setProperty("errorCode", "INTERNAL_ERROR");
        return problem;
    }

    private record FieldErrorDetail(String field, @Nullable String message) {
    }
}
