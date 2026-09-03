package com.centraldungeon.common.exception;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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
     * @param exception the intentional failure, carrying its own status and code
     * @return the problem detail, with {@code errorCode} for the client to branch on
     */
    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.getStatus(), exception.getMessage());
        problem.setProperty("errorCode", exception.getErrorCode());
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
