package com.centraldungeon.tasks.dto;

import com.centraldungeon.tasks.TaskAudience;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * Publishing a task.
 *
 * <p><b>Creating is publishing</b> (#77): there is no draft to save and no second call to send it.
 * The recipients are notified in the same transaction that writes the row, because a task nobody was
 * told about cannot be answered.
 *
 * <p>Bean Validation covers what a single field can be wrong about; the relations between them live
 * in the service, where they belong (arquitectura.md 2.3) - that a {@code Single} task names a
 * target, that a task with both channels off is not a task, and that a session belongs to this table.
 *
 * @param title          the headline. Required: it is what the notification and the list show
 * @param description    the detail, as rich text. Sanitized before it is stored (#62). Optional
 * @param audience       who is being asked (#63). Required
 * @param targetUserId   the one person addressed. Required when the audience is
 *                       {@link TaskAudience#Single} and refused on any other, both in the service
 * @param tableSessionId the session to tie the ask to, or null for "at any point". Must belong to
 *                       this table
 * @param acceptsText    whether a written answer is expected
 * @param acceptsFiles   whether files are expected. At least one of the two has to be true
 * @param isMandatory    whether to label it indispensable. A label and nothing more (#70)
 * @param dueAt          when the master would like it by, <b>in UTC</b> (#22), or null for no date.
 *                       A date in the past is accepted: recording what was asked for last week is
 *                       legitimate, and nothing in the system acts when a due date passes
 */
public record CreateTaskRequest(
        @NotBlank @Size(max = 128) String title,
        @Nullable String description,
        @NotNull TaskAudience audience,
        @Nullable String targetUserId,
        @Nullable String tableSessionId,
        boolean acceptsText,
        boolean acceptsFiles,
        boolean isMandatory,
        @Nullable LocalDateTime dueAt) {
}
