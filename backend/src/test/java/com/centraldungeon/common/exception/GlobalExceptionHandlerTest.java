package com.centraldungeon.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * The answers the handler gives to failures no service raised on purpose: an upload the servlet
 * container refused, a lock the database gave up on, a request the framework itself rejects, and a bug.
 *
 * <p>Both have to say what happened without saying how: the container's refusal is the same
 * {@code FILE_TOO_LARGE} the application's own limit raises, so the person reads one sentence, and a
 * bug never puts its message, a stack trace or a SQL fragment in the body.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void anUploadTheContainerRefusedIsFileTooLarge() {
        ProblemDetail problem = handler.handleUploadTooLarge(new MaxUploadSizeExceededException(26L * 1024 * 1024));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getProperties()).containsEntry("errorCode", "FILE_TOO_LARGE");
    }

    @Test
    void aLockTheDatabaseGaveUpOnIsAConflictNotA500() {
        ProblemDetail problem = handler.handleDataIntegrityViolation(new CannotAcquireLockException("Deadlock found"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getProperties()).containsEntry("errorCode", "CONFLICT");
        assertThat(problem.getDetail()).doesNotContain("Deadlock");
    }

    @ParameterizedTest
    @MethodSource("clientMistakesTheFrameworkDescribes")
    void aClientMistakeTheFrameworkDescribesKeepsItsStatusAndGetsACode(
            Exception exception, HttpStatus status, String errorCode) {
        ProblemDetail problem = handler.handleUnexpected(exception);

        assertThat(problem.getStatus()).isEqualTo(status.value());
        assertThat(problem.getProperties()).containsEntry("errorCode", errorCode);
        assertThat(problem.getDetail()).doesNotContain("secret").doesNotContain("Failed");
    }

    static Stream<Arguments> clientMistakesTheFrameworkDescribes() {
        return Stream.of(
                Arguments.of(
                        new NoResourceFoundException(HttpMethod.GET, "/secret/path", "secret/path"),
                        HttpStatus.NOT_FOUND,
                        "NOT_FOUND"),
                Arguments.of(
                        new HttpRequestMethodNotSupportedException("secret"),
                        HttpStatus.METHOD_NOT_ALLOWED,
                        "METHOD_NOT_ALLOWED"),
                Arguments.of(
                        new HttpMediaTypeNotSupportedException("secret"),
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "UNSUPPORTED_MEDIA_TYPE"),
                Arguments.of(
                        new HttpMediaTypeNotAcceptableException("secret"),
                        HttpStatus.NOT_ACCEPTABLE,
                        "NOT_ACCEPTABLE"),
                Arguments.of(
                        new MissingServletRequestParameterException("secret", "String"),
                        HttpStatus.BAD_REQUEST,
                        "VALIDATION_ERROR"));
    }

    @Test
    void aFrameworkFailureThatIsNotTheCallersMistakeStaysA500() {
        ProblemDetail problem = handler.handleUnexpected(new AsyncRequestTimeoutException());

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getProperties()).containsEntry("errorCode", "INTERNAL_ERROR");
    }

    @Test
    void anUnexpectedFailureIsA500ThatTellsNothingAboutItsCause() {
        ProblemDetail problem = handler.handleUnexpected(new IllegalStateException("delete from users where id = 'secret'"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getProperties()).containsEntry("errorCode", "INTERNAL_ERROR");
        assertThat(problem.getDetail()).doesNotContain("secret");
    }
}
