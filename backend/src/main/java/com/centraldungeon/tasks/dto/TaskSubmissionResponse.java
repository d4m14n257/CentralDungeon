package com.centraldungeon.tasks.dto;

import java.time.LocalDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One answer somebody handed in.
 *
 * <p>There is no status and no verdict in it, and that is #76 showing through: the system records
 * that something was handed in and stops there. A field saying "accepted" would be the system
 * claiming a judgement it has no way to make.
 *
 * @param submissionId the answer's identifier
 * @param taskId       the task it answers
 * @param userId       who handed it in
 * @param userName     how to name them on screen - their Discord username, which everybody has
 * @param content      the written answer, as sanitized rich text (#62), or null when it was files only
 * @param files        the files handed in with it. Empty when the answer was text only. They are
 *                     linked, never copied (#65, #79)
 * @param submittedAt  when it was handed in, in UTC (#22). The frontend converts (#111)
 */
public record TaskSubmissionResponse(
        String submissionId,
        String taskId,
        String userId,
        String userName,
        @Nullable String content,
        List<SubmittedFileResponse> files,
        @Nullable LocalDateTime submittedAt) {
}
