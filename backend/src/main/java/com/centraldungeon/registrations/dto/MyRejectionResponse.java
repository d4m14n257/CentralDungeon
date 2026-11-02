package com.centraldungeon.registrations.dto;

import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * Why the <b>actor of the token</b> was turned down at a table, as that table's own screen shows it. It
 * is the person's own data and never anybody else's: it is built from their latest application there
 * and only while that one is a rejection.
 *
 * <p>Exactly one of {@code justification} and {@code reasonCode} is filled: a master's words are shown
 * verbatim, and the rejection the application wrote itself is a code the frontend translates, so the
 * notice never has an empty reason.
 *
 * @param justification the master's own words for turning the application down. Null when the system
 *                      rejected it
 * @param reasonCode    the code of a rejection the application wrote itself, today only
 *                      {@code TABLE_FULL}. Null whenever a person did the rejecting
 * @param rejectedAt    when it happened, in UTC
 */
public record MyRejectionResponse(
        @Nullable String justification,
        @Nullable String reasonCode,
        LocalDateTime rejectedAt) {
}
