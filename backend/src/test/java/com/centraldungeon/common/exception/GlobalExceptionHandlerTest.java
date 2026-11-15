package com.centraldungeon.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * The answers the handler gives to failures no service raised on purpose: an upload the servlet
 * container refused, a lock the database gave up on, and a bug.
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

    @Test
    void anUnexpectedFailureIsA500ThatTellsNothingAboutItsCause() {
        ProblemDetail problem = handler.handleUnexpected(new IllegalStateException("delete from users where id = 'secret'"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getProperties()).containsEntry("errorCode", "INTERNAL_ERROR");
        assertThat(problem.getDetail()).doesNotContain("secret");
    }
}
