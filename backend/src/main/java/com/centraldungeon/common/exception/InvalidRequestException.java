package com.centraldungeon.common.exception;

import java.util.Map;
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
     * @param message what about the request does not hold together, for whoever reads a log. It names
     *                the two things that contradict each other rather than saying "invalid"
     */
    public InvalidRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }

    /**
     * The same 400 under a code of its own, so the frontend can tell one bad request from another
     * and write a sentence about the actual problem.
     *
     * @param message   the developer-facing detail, in English
     * @param errorCode the stable code the frontend branches on. {@code INVALID_REQUEST} stays the
     *                  fallback for anything that has no message worth writing separately
     */
    public InvalidRequestException(String message, String errorCode) {
        super(HttpStatus.BAD_REQUEST, errorCode, message);
    }

    /**
     * A coded 400 that carries values, for the messages that are useless without a number in them -
     * "up to 2 MB" says something, "too large" does not.
     *
     * <p>Same shape {@link ConflictException} grew for #188, and the same reason: the sentence is the
     * frontend's to write, in the reader's language, from a code and its parameters (#197).
     *
     * @param message     the developer-facing detail, in English
     * @param errorCode   the stable code the frontend branches on
     * @param errorParams the values the translated sentence needs, keyed by placeholder name
     */
    public InvalidRequestException(String message, String errorCode, Map<String, String> errorParams) {
        super(HttpStatus.BAD_REQUEST, errorCode, message, errorParams);
    }
}
