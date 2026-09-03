package com.centraldungeon.registrations.dto;

import org.jspecify.annotations.Nullable;

/**
 * Applying to a table.
 *
 * <p>The table comes from the path and the applicant from the token, never from the body (#121) -
 * which is why the only field here is what the person wants to say about themselves.
 *
 * @param description the applicant's note to the master. Optional: the karma and the profile
 *                    already say a lot, and forcing a pitch would not add to it. The character file
 *                    that goes with an application is F2
 */
public record CreateRegistrationRequest(@Nullable String description) {
}
