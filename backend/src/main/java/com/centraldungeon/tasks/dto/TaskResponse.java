package com.centraldungeon.tasks.dto;

import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * A task as the people running the table see it, on the Peticiones tab.
 *
 * <p>Separate from {@link ApplicableTaskResponse} because the two audiences are allowed to know
 * different things (arquitectura.md 2.3): the master sees how many people have answered and how many
 * were asked, and a recipient has no business seeing either - who else handed in their character
 * sheet is not theirs to know.
 *
 * @param taskId                the task's identifier
 * @param gameTableId           the table doing the asking
 * @param tableSessionId        the session this is tied to, or null for "at any point" (#63)
 * @param sessionSequenceNumber which session of the run that is, from 1, so the screen can say
 *                              "session 4" without a second request. Null together with the id above
 * @param audience              who is being asked, as a string (arquitectura.md 2.3)
 * @param targetUserId          the one person addressed, or null on any audience but {@code Single}
 * @param targetUserName        how to name that person on screen. Null together with the id above
 * @param title                 the headline
 * @param description           the detail, as sanitized rich text (#62). Null when the title said it all
 * @param acceptsText           whether a written answer is expected
 * @param acceptsFiles          whether files are expected
 * @param isMandatory           whether the master labelled it indispensable. <b>Informational</b>: it
 *                              changes what the screen says and blocks nothing (#70)
 * @param dueAt                 when the master would like it by, in UTC (#22), or null. Nothing
 *                              happens when it passes
 * @param status                where the task is in its life, as a string
 * @param submissionCount       how many answers it has. Answers accumulate, so this can exceed the
 *                              number of people (#76)
 * @param respondentCount       how many different people have answered. This is the number that means
 *                              "handed in"
 * @param recipientCount        how many people were asked, derived from the audience at read time and
 *                              never stored - a table that gains a player gains a recipient
 * @param createdAt             when it was published, in UTC. Publishing and creating are the same
 *                              act: there is no draft (#77)
 */
public record TaskResponse(
        String taskId,
        String gameTableId,
        @Nullable String tableSessionId,
        @Nullable Integer sessionSequenceNumber,
        String audience,
        @Nullable String targetUserId,
        @Nullable String targetUserName,
        String title,
        @Nullable String description,
        boolean acceptsText,
        boolean acceptsFiles,
        boolean isMandatory,
        @Nullable LocalDateTime dueAt,
        String status,
        int submissionCount,
        int respondentCount,
        int recipientCount,
        LocalDateTime createdAt) {
}
