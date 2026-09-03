package com.centraldungeon.common.security;

/**
 * A token that did not verify: bad signature, expired, malformed, or of the wrong
 * {@link TokenType}.
 *
 * <p>Deliberately not an {@code ApiException}. It is thrown inside the security filter chain, before
 * {@code GlobalExceptionHandler} is in play, and what turns it into a response is
 * {@code RestAuthenticationEntryPoint} - which answers 401 without saying which of the checks
 * failed, because that difference is only useful to someone probing.
 */
public class InvalidJwtException extends RuntimeException {

    /**
     * @param message what failed, for the log. It does not reach the client
     */
    public InvalidJwtException(String message) {
        super(message);
    }

    /**
     * @param message what failed, for the log. It does not reach the client
     * @param cause   the underlying library failure
     */
    public InvalidJwtException(String message, Throwable cause) {
        super(message, cause);
    }
}
