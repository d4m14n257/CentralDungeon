package com.centraldungeon.tasks.dto;

import com.centraldungeon.tasks.TaskAudience;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * Correcting a task.
 *
 * <p><b>It carries the whole state the task should end in, not a delta</b> (#189): an absent
 * description clears the description, an absent due date clears the date. A request that describes
 * the end state can be replayed and reasoned about; a patch of optional fields cannot say the
 * difference between "leave it" and "clear it" without inventing a convention for it.
 *
 * <p><b>Correcting does not notify again.</b> #77 puts the notification at publication, and a task
 * fixed three times would otherwise ring three times for a headline that never changed - which is
 * how people learn to ignore the bell.
 *
 * @param title          the headline. Required
 * @param description    the detail as rich text, sanitized before storing (#62). Null clears it
 * @param audience       who is being asked. Changing it re-checks the target against it
 * @param targetUserId   the one person addressed, required only with {@link TaskAudience#Single}
 * @param tableSessionId the session to tie the ask to, or null to untie it
 * @param acceptsText    whether a written answer is expected
 * @param acceptsFiles   whether files are expected. At least one of the two has to be true
 * @param isMandatory    whether to label it indispensable (#70)
 * @param dueAt          when the master would like it by, in UTC (#22). Null clears the date
 */
public record UpdateTaskRequest(
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
