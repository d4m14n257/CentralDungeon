package com.centraldungeon.registrations.dto;

import java.time.LocalDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One application, in both directions: the master's candidate list and the applicant's own
 * "my applications" screen read the same record.
 *
 * <p><b>{@code attachedFiles} travels here rather than through an endpoint of its own</b> (ADR-13 §1 uso
 * 2), the same criterion F1.3 used for a table's sessions and F1.4 for its shared files: this read
 * already decides who may see the application - the master of that table, or the applicant
 * themselves - and repeating that check in a second place is where it would drift.
 *
 * @param id                     the application's identifier
 * @param gameTableId            the table applied to
 * @param gameTableName          its title, so the applicant's list does not need a second request
 * @param userId                 the applicant
 * @param userName               their display name - the master sees a person, not an id
 * @param status                 where the application stands, as a string
 * @param description            the applicant's note, or null when they wrote none
 * @param createdAt              when they applied, in UTC. FIFO order is not decoration: it is the
 *                               rule the auto-reject on fill depends on (ADR-09 §1, §3)
 * @param rejectionJustification the master's own words for turning it down, shown verbatim. Null
 *                               for anything not rejected, and null when the rejection was the
 *                               system's rather than a person's - that one is
 *                               {@code rejectionReasonCode}
 * @param rejectionReasonCode    the code of a rejection the application wrote itself, today only
 *                               {@code TABLE_FULL} (ADR-09 §3). The frontend renders it in the reader's
 *                               language (ADR-23 §3). Null whenever a person did the rejecting
 * @param attachedFiles          the character sheet, or anything else the applicant attached (ADR-13 §1
 *                               uso 2). They are linked, never copied (ADR-13 §2, ADR-14 §1). Empty when they
 *                               attached nothing - attaching was optional
 * @param blockedByName          who vetoed this application, or null when it is not vetoed (ADR-09 §5).
 *                               <b>A veto that disappears from the screen is not reversible in
 *                               practice</b>, so the row stays and says who decided it - the master
 *                               reading it has to be able to go and ask them
 * @param blockedAt              when the veto was applied, or null when it is not vetoed. The other
 *                               half of the same sentence: «hace seis meses» and «anteayer» are
 *                               different arguments for lifting it
 * @param blockJustification     the reason that was written down (ADR-09 §5), or null when it is not
 *                               vetoed. <b>None of these three ever travels to the vetoed person</b>
 *                               - every read of that table answers them 404 (ADR-09 §4), so the only
 *                               readers are the table's masters
 */
public record RegistrationResponse(
        String id,
        String gameTableId,
        String gameTableName,
        String userId,
        String userName,
        String status,
        @Nullable String description,
        LocalDateTime createdAt,
        @Nullable String rejectionJustification,
        @Nullable String rejectionReasonCode,
        List<RegistrationFileResponse> attachedFiles,
        @Nullable String blockedByName,
        @Nullable LocalDateTime blockedAt,
        @Nullable String blockJustification) {
}
