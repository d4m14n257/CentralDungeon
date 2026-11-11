package com.centraldungeon.matrix;

import org.jspecify.annotations.Nullable;

/**
 * What one actor is expected to get from one endpoint. Every cell fixes an exact status: there is no
 * "any 4xx" and no "anything but 403", because either of those also passes when the request never
 * reached the code it was meant to exercise.
 *
 * @param kind      which of the three kinds of cell this is
 * @param status    the exact status expected
 * @param errorCode the exact {@code errorCode} of the {@code ProblemDetail}, when a document fixes one
 * @param bodyCheck what to assert about the response once the status matched, or null for nothing
 * @param property  what the documents say about the status even when they do not fix it
 */
record MatrixCell(Kind kind, int status, @Nullable String errorCode, @Nullable BodyCheck bodyCheck, Property property) {

    /** The three kinds of cell, and the only ones. */
    enum Kind {
        /** The documents say it passes and with which status. */
        ALLOWED,
        /** The documents say it is refused and with which status. */
        DENIED,
        /** No document fixes the status: the observed one is pinned and listed for triage. */
        UNDOCUMENTED
    }

    /** What the documents do say about a status they do not fix. */
    enum Property {
        /** Nothing: the documents do not even say whether it passes. */
        NONE,
        /** It has to pass. */
        MUST_BE_2XX,
        /** It must not pass. */
        MUST_NOT_BE_2XX
    }

    /** An assertion about the response, run only after the status matched. */
    @FunctionalInterface
    interface BodyCheck {
        /**
         * @param w    the fixtures of this cell, to reach what the row remembered or to read the database
         * @param body the response body as text
         * @throws Exception if the check itself fails to run
         */
        void check(MatrixFixtures w, String body) throws Exception;
    }

    /**
     * @param status the exact 2xx the documents fix
     * @return a cell that passes with that status
     */
    static MatrixCell allowed(int status) {
        return allowed(status, null);
    }

    /**
     * @param status the exact 2xx the documents fix
     * @param check  what the response has to contain, typically own resource in and foreign resource out
     * @return a cell that passes with that status and that body
     */
    static MatrixCell allowed(int status, @Nullable BodyCheck check) {
        requireSuccess(status);
        return new MatrixCell(Kind.ALLOWED, status, null, check, Property.MUST_BE_2XX);
    }

    /**
     * @param status the exact refusal the documents fix
     * @return a cell refused with that status, whatever its code
     */
    static MatrixCell denied(int status) {
        return denied(status, null);
    }

    /**
     * @param status    the exact refusal the documents fix
     * @param errorCode the exact {@code errorCode}, or null when no document fixes one
     * @return a cell refused with that status and that code
     */
    static MatrixCell denied(int status, @Nullable String errorCode) {
        requireRefusal(status);
        return new MatrixCell(Kind.DENIED, status, errorCode, null, Property.MUST_NOT_BE_2XX);
    }

    /**
     * For the oracle's {@code SD}: no document says even whether it passes.
     *
     * @param status the status observed, pinned so a change gets noticed
     * @return a cell listed as pending triage
     */
    static MatrixCell undocumented(int status) {
        return new MatrixCell(Kind.UNDOCUMENTED, status, null, null, Property.NONE);
    }

    /**
     * For the oracle's {@code 2xx†}: it has to pass, and no document says with which status.
     *
     * @param status the 2xx observed
     * @return a cell listed as pending triage
     */
    static MatrixCell undocumented2xx(int status) {
        return undocumented2xx(status, null);
    }

    /**
     * For the oracle's {@code 2xx†} when it also fixes an effect to verify.
     *
     * @param status the 2xx observed
     * @param check  what has to hold after the call
     * @return a cell listed as pending triage
     */
    static MatrixCell undocumented2xx(int status, @Nullable BodyCheck check) {
        requireSuccess(status);
        return new MatrixCell(Kind.UNDOCUMENTED, status, null, check, Property.MUST_BE_2XX);
    }

    /**
     * For the oracle's {@code SD≠2xx}: the documents say it does not pass, not how it is refused. A
     * 2xx cannot be written here, because that is a finding and belongs in {@link KnownDisagreements}.
     *
     * @param status the refusal observed
     * @return a cell listed as pending triage
     */
    static MatrixCell undocumentedNot2xx(int status) {
        requireRefusal(status);
        return new MatrixCell(Kind.UNDOCUMENTED, status, null, null, Property.MUST_NOT_BE_2XX);
    }

    /** @return whether this cell, once green, proves that the row's path and body reach the handler */
    boolean isPositiveControl() {
        return isSuccess(status) && kind != Kind.DENIED;
    }

    /**
     * @param status an HTTP status
     * @return whether it is a 2xx
     */
    static boolean isSuccess(int status) {
        return status >= 200 && status < 300;
    }

    private static void requireSuccess(int status) {
        if (!isSuccess(status)) {
            throw new IllegalArgumentException(status + " is not a 2xx: this cell says the call passes");
        }
    }

    private static void requireRefusal(int status) {
        if (isSuccess(status)) {
            throw new IllegalArgumentException(status + " is a 2xx: this cell says the call does not pass");
        }
    }
}
