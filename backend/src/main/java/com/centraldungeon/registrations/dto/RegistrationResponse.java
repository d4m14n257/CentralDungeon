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
 * @param rejectionJustification the master's own words for turning it down, shown verbatim. Null
 *                               for anything not rejected, and null when the rejection was the
 *                               system's rather than a person's - that one is
 *                               {@code rejectionReasonCode}
 * @param rejectionReasonCode    the code of a rejection the application wrote itself, today only
 *                               {@code TABLE_FULL} (#34). The frontend renders it in the reader's
 *                               language (#197). Null whenever a person did the rejecting
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
        @Nullable String rejectionJustification,
        @Nullable String rejectionReasonCode) {
}
