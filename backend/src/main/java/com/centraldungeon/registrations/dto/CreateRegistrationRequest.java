package com.centraldungeon.registrations.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Applying to a table.
 *
 * <p>The table comes from the path and the applicant from the token, never from the body (ADR-05 §5) -
 * which is why the only fields here are what the person wants to say about themselves and what they
 * attach.
 *
 * @param description the applicant's note to the master, plain text and never HTML (ADR-03 §8). Optional:
 *                    the karma and the profile already say a lot, and forcing a pitch would not add to it.
 *                    Capped at the same 2000 characters the form allows
 * @param fileIds     the character sheet, or anything else the master asked for, by id (ADR-13 §1 uso 2).
 *                    Each has to be the applicant's own or one the platform published (ADR-14 §1) - never
 *                    somebody else's private upload. Required as a list, but the list itself may be
 *                    empty: attaching is optional, being explicit about having nothing to attach is
 *                    not
 */
public record CreateRegistrationRequest(@Nullable @Size(max = 2000) String description, @NotNull List<String> fileIds) {
}
