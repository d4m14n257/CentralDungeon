package com.centraldungeon.approvals;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Where a request is. Three states and one transition each: a request is resolved once and never
 * re-resolved, the same shape the table's state machine has.
 *
 * <p>Constants in PascalCase, which is what every enum of this project that crosses HTTP does
 * ({@code UserStatus.Allowed}, {@code MasterType.Primary}). No {@code @JsonValue} here because the
 * status only ever <em>leaves</em> the backend, as a string inside a response DTO - nothing accepts
 * it in a request body.
 */
public enum ApprovalStatus {

    /** Nobody has resolved it yet. The only state a request can be approved or rejected from. */
    Pending,

    /** An admin said yes, with a reason. Whatever effect the type has already happened. */
    Approved,

    /** An admin said no, with a reason. Rejecting without saying why is half the mechanism (#42). */
    Rejected;

    /**
     * Looks a status up by name, forgivingly - this is the search box's door, not a body's.
     *
     * @param name a status name, in any case
     * @return the matching constant, or empty. Empty is a normal answer: {@code ?q=/status Maybe}
     *         matches nothing rather than answering 400 (arquitectura.md 2.5)
     */
    public static Optional<ApprovalStatus> fromName(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(status -> status.name().toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst();
    }
}
