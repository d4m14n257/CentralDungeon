package com.centraldungeon.common.exception;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Turns every exception that escapes a controller into an RFC 9457 {@code ProblemDetail}. It is the reason a
 * service can throw a {@link ApiException} without knowing anything about HTTP.
 *
 * <p>Two rules the old project broke and this class enforces: an error is never a bare string, and
 * there is no catch-all status - the Node backend used 418 as a generic error, which told a client
 * nothing it could branch on.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Everything the application refuses on purpose: 404, 409, 403, 401.
     *
     * <p>{@code errorParams} rides along when the failure has values its message needs. The
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
     * A request body the message converter cannot turn into the DTO - malformed JSON, a string where
     * a number goes, or a value outside an enum.
     *
     * <p>This is {@link #handleTypeMismatch}'s twin. That one closed the hole for a path variable or
     * a query parameter; the body had the same hole and nobody had walked into it yet, because every
     * enum in the application used to be spelled on the wire exactly as its constants are - so the
     * only way to get a value Jackson rejected was to hand-write the request. {@code PlatformRole} is
     * the first enum whose wire spelling differs ({@code "Admin"}, not {@code ADMIN}), which made the
     * hole reachable: sending the wrong spelling answered <b>500</b>, telling whoever sent it that
     * the server broke when what happened is that they mistyped a field.
     *
     * <p>{@code VALIDATION_ERROR}, the same code Jakarta validation raises, because from the
     * frontend's side the two are the same event: the body was not acceptable. The detail stays
     * generic on purpose - Jackson's message names classes and parser positions, which is internal
     * shape that a 400 has no business publishing.
     *
     * @param exception what the converter refused. Logged, never echoed
     * @return a 400 problem detail carrying {@code VALIDATION_ERROR}
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed request body");
        problem.setProperty("errorCode", "VALIDATION_ERROR");
        return problem;
    }

    /**
     * A write the database refused because it collided with something already there.
     *
     * <p>Two requests racing for the same row end with one of them losing, and the loser deserves an
     * answer about the collision rather than about the server. Without this it falls through to
     * {@code handleUnexpected} and becomes a 500 that leaks the offending INSERT - telling somebody
     * whose only mistake was pressing at the same moment as someone else that the site is broken.
     *
     * <p><b>This is a backstop, not the rule.</b> Every race the application knows about is meant to
     * be serialized before it reaches the database - the pessimistic locks in
     * {@code UserRoleService}, and the ones in {@code ApprovalService}. When one of those is missing
     * or a new path forgets it, the constraint still holds and this turns the breakage into a
     * truthful 409 instead of an alarming 500. The same posture {@code handleUnreadableBody} has for
     * a malformed body: the detail stays English and for the log, and the frontend writes the
     * sentence from the code.
     *
     * <p>A lock the database gave up on - a deadlock it broke by aborting one side, as when two first
     * edits of the same setting both insert under their locking reads - is the same collision seen from
     * the other end, and gets the same answer.
     *
     * @param exception the constraint violation or lock failure the persistence layer raised
     * @return 409 carrying {@code CONFLICT}
     */
    @ExceptionHandler({DataIntegrityViolationException.class, PessimisticLockingFailureException.class})
    public ProblemDetail handleDataIntegrityViolation(DataAccessException exception) {
        if (exception instanceof PessimisticLockingFailureException) {
            // A deadlock or a lock timeout is usually two paths taking their locks in different orders:
            // the person gets an honest 409, and the log keeps what it takes to find the second path.
            log.warn("Lock failure answered 409", exception);
        }
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "The request collided with existing data");
        problem.setProperty("errorCode", "CONFLICT");
        return problem;
    }

    /**
     * An upload bigger than what the servlet container accepts.
     *
     * <p>Without this it would fall through to handleUnexpected and answer 500 to somebody who simply
     * picked a large file - a "the site is broken" where the truth is "that file is too big". It
     * carries {@code FILE_TOO_LARGE}, the same code {@code FileService} raises when the application's
     * own cap is the one that refuses, so the frontend writes one sentence for both.
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
     * how internals leak. It goes to the log instead, at ERROR: a 500 that leaves no trace is a failure
     * nobody can find, and the e2e cleanup failed that way for months.
     *
     * <p>Except for what the framework already knows how to describe as the caller's mistake - an
     * unknown route, a method the route does not take, a media type it does not read: anybody can send
     * those without a session, so they answer with the status the framework chose (404, 405, 415, 406,
     * 400) and a code the client can branch on, and a full trace at ERROR for each one would let a loop
     * of them bury the real alarms. They stay at DEBUG, and the response carries neither the framework's
     * message nor the path.
     *
     * @param exception whatever escaped
     * @return the framework's own 4xx with a generic detail, or a 500 with no detail about the cause
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception) {
        if (exception instanceof ErrorResponse described && described.getStatusCode().is4xxClientError()) {
            log.debug("Framework-described request failure", exception);
            return clientMistake(HttpStatus.valueOf(described.getStatusCode().value()));
        }
        log.error("Unhandled exception, answered 500", exception);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
        problem.setProperty("errorCode", "INTERNAL_ERROR");
        return problem;
    }

    private static ProblemDetail clientMistake(HttpStatus status) {
        String code = switch (status) {
            case NOT_FOUND -> "NOT_FOUND";
            case METHOD_NOT_ALLOWED -> "METHOD_NOT_ALLOWED";
            case NOT_ACCEPTABLE -> "NOT_ACCEPTABLE";
            case UNSUPPORTED_MEDIA_TYPE -> "UNSUPPORTED_MEDIA_TYPE";
            case BAD_REQUEST -> "VALIDATION_ERROR";
            default -> "BAD_REQUEST";
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, status.getReasonPhrase());
        problem.setProperty("errorCode", code);
        return problem;
    }

    private record FieldErrorDetail(String field, @Nullable String message) {
    }
}
