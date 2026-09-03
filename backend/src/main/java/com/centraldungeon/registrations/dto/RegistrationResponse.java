package com.centraldungeon.registrations.dto;

import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * One application, in both directions: the master's candidate list and the applicant's own
 * "my applications" screen read the same record.
 *
 * @param id                     the application's identifier
 * @param gameTableId            the table applied to
 * @param gameTableName          its title, so the applicant's list does not need a second request
 * @param userId                 the applicant
 * @param userName               their display name - the master sees a person, not an id
 * @param userKarma              their karma at the time of the read (#97), which is most of what a
 *                               master decides on
 * @param status                 where the application stands, as a string
 * @param description            the applicant's note, or null when they wrote none
 * @param createdAt              when they applied, in UTC. FIFO order is not decoration: it is the
 *                               rule the auto-reject on fill depends on (#28, #34)
 * @param rejectionJustification why it was turned down, or null for anything not rejected
 */
public record RegistrationResponse(
        String id,
        String gameTableId,
        String gameTableName,
        String userId,
        String userName,
        int userKarma,
        String status,
        @Nullable String description,
        LocalDateTime createdAt,
        @Nullable String rejectionJustification) {
}
