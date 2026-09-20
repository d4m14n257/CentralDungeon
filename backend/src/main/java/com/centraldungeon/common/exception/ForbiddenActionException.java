package com.centraldungeon.common.exception;

import org.springframework.http.HttpStatus;

/**
 * The actor is authenticated but has no business doing this - typically because the resource is not
 * theirs (#121). Answers 403.
 *
 * <p>Not to be used to hide a resource's existence: a table someone is vetoed from answers 404, not
 * this (decisiones.md, ciclo de vida de la mesa).
 */
public final class ForbiddenActionException extends ApiException {

    /** The code every refusal carries unless it is one the frontend has to tell apart. */
    private static final String DEFAULT_ERROR_CODE = "FORBIDDEN";

    /**
     * An admin reaching for the Admin or Owner role, granting it or taking it away. Only an owner
     * moves the rank (fase-3-admin-owner.md 3, #169).
     *
     * <p>It gets a code of its own because it is the one 403 the screen has to <b>explain</b>: the
     * reader is an admin who legitimately administers the platform and is being told that this one
     * role is above their line. A generic "no tenés permiso" reads as a bug to them.
     */
    public static final String ROLE_GRANT_FORBIDDEN = "ROLE_GRANT_FORBIDDEN";

    /**
     * Blocking somebody who holds Admin or Owner. Nobody can, not even an owner: among peers there
     * is no authority, and the same limit already governs "ver como" (#140a).
     *
     * <p>Its own code for the same reason as the one above - and because it also covers blocking
     * yourself, where "you cannot block an admin" is the whole explanation the reader needs.
     */
    public static final String CANNOT_BLOCK_PRIVILEGED = "CANNOT_BLOCK_PRIVILEGED";

    /**
     * A {@code Secondary} reaching for something only the table's {@code Primary} may do: vetoing
     * somebody, lifting a veto, or answering a co-master's request for one (#39, #71).
     *
     * <p><b>403 and not 400</b>, the same shape F3.1 settled on: it is a question of who you are,
     * not of what you sent. Nothing about the body would make it succeed.
     *
     * <p>Its own code because the screen has to explain it rather than report it. A {@code Secondary}
     * genuinely co-runs the table and is being told that this one act is the {@code Primary}'s - and
     * that there is a door for them, which is asking for it. A generic "no tenés permiso" reads as a
     * bug to somebody who legitimately runs the table.
     *
     * <p>In practice the frontend should never provoke it: the screen knows whether the reader is
     * the {@code Primary} and says, before the button is pressed, that what it sends is a request
     * (fase-3-admin-owner.md §4). This is what answers the stale tab and the hand-written call.
     */
    public static final String NOT_PRIMARY_MASTER = "NOT_PRIMARY_MASTER";

    /**
     * @param message what the actor is not allowed to do
     */
    public ForbiddenActionException(String message) {
        this(message, DEFAULT_ERROR_CODE);
    }

    /**
     * @param message   what the actor is not allowed to do, in English and for a log (#197)
     * @param errorCode the stable code the frontend branches on. Use {@link #ROLE_GRANT_FORBIDDEN}
     *                  or {@link #CANNOT_BLOCK_PRIVILEGED} where the screen has to say something
     *                  specific; anything else has no reason not to be the default
     */
    public ForbiddenActionException(String message, String errorCode) {
        super(HttpStatus.FORBIDDEN, errorCode, message);
    }
}
