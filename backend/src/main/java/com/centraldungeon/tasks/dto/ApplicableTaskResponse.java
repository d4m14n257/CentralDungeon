package com.centraldungeon.tasks.dto;

import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * A task as the person being asked sees it, on {@code /tables/:id} and {@code /my/tables/:id}.
 *
 * <p>Deliberately narrower than {@link TaskResponse}: no counts and no roster. How many other people
 * handed in their sheet is the master's information, not a recipient's.
 *
 * <p><b>{@code canSubmit} is what lets the button explain itself.</b> Somebody reading a table they
 * have not applied to sees what will be asked of them - which is half of deciding whether to apply -
 * and the button says why they cannot answer yet, rather than being grey for no stated reason
 * (principio 2 de frontend-diseno.md 1).
 *
 * @param taskId                the task's identifier
 * @param audience              who is being asked, as a string (arquitectura.md 2.3). The screen uses
 *                              it to say whether this is asked of candidates, of players, or of them
 *                              in particular
 * @param tableSessionId        the session this is tied to, or null for "at any point" (#63)
 * @param sessionSequenceNumber which session of the run that is, from 1. Null together with the id
 * @param title                 the headline
 * @param description           the detail, as sanitized rich text (#62), or null
 * @param acceptsText           whether a written answer is expected - it decides what the form offers
 * @param acceptsFiles          whether files are expected
 * @param isMandatory           whether the master labelled it indispensable. It is a label: missing it
 *                              does not block anything and never evicts anybody (#70)
 * @param dueAt                 when the master would like it by, in UTC (#22), or null
 * @param canSubmit             whether this reader may answer right now: they are in the audience and
 *                              the task is still open. False for somebody who has not applied yet
 * @param mySubmissionCount     how many times <b>this</b> reader has answered. Answers accumulate and
 *                              none replaces another (#76), so it is a count and not a boolean
 * @param createdAt             when it was published, in UTC
 */
public record ApplicableTaskResponse(
        String taskId,
        String audience,
        @Nullable String tableSessionId,
        @Nullable Integer sessionSequenceNumber,
        String title,
        @Nullable String description,
        boolean acceptsText,
        boolean acceptsFiles,
        boolean isMandatory,
        @Nullable LocalDateTime dueAt,
        boolean canSubmit,
        int mySubmissionCount,
        LocalDateTime createdAt) {
}
