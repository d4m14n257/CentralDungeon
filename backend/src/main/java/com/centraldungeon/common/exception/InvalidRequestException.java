package com.centraldungeon.common.exception;

import org.springframework.http.HttpStatus;

/**
 * The request is malformed in a way Bean Validation cannot see, because what is wrong is the
 * relation between fields rather than any field on its own. Answers 400.
 *
 * <p>The case that brought it into existence (#187): two slots of the <b>same</b> table whose
 * sessions overlap each other. Each slot is perfectly valid, the agenda they describe is not, and
 * #178 asks for a {@code 400} there - it is input that does not describe a playable week, not a
 * conflict with anybody else's state, which is what {@link ConflictException} and its {@code 409}
 * are for.
 */
public final class InvalidRequestException extends ApiException {

    /**
     * @param message what about the request does not hold together. It reaches the user, so it names
     *                the two things that contradict each other rather than saying "invalid"
     */
    public InvalidRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }
}
