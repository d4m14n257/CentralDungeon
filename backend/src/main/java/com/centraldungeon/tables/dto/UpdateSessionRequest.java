package com.centraldungeon.tables.dto;

import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * A master correcting one session: its date, its notes, or both.
 *
 * <p>It is a replacement of both fields and not a patch, same as the table's own {@code PUT} (ADR-04 §7):
 * an absent {@code notes} clears the notes. An absent {@code scheduledAt} is the one exception - a
 * session always happens at some instant, so leaving it out means "do not move it".
 *
 * <p>The date is free, and deliberately does not have to land on one of the table's agenda slots:
 * correcting one evening is not the same as changing the week, and the clash rules of ADR-08 §2 compare
 * weekly intervals rather than instants.
 *
 * @param scheduledAt the new instant, <b>in UTC</b> (ADR-03 §6), or null to leave the date alone
 * @param notes       what the master writes about the session, or null to clear it
 */
public record UpdateSessionRequest(@Nullable LocalDateTime scheduledAt, @Nullable String notes) {
}
