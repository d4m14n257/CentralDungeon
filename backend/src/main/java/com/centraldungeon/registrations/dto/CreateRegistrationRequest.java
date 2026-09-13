package com.centraldungeon.registrations.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Applying to a table.
 *
 * <p>The table comes from the path and the applicant from the token, never from the body (#121) -
 * which is why the only fields here are what the person wants to say about themselves and what they
 * attach.
 *
 * @param description the applicant's note to the master. Optional: the karma and the profile
 *                    already say a lot, and forcing a pitch would not add to it
 * @param fileIds     the character sheet, or anything else the master asked for, by id (#60 uso 2).
 *                    Each has to be the applicant's own or one the platform published (#79) - never
 *                    somebody else's private upload. Required as a list, but the list itself may be
 *                    empty: attaching is optional, being explicit about having nothing to attach is
 *                    not
 */
public record CreateRegistrationRequest(@Nullable String description, @NotNull List<String> fileIds) {
}
