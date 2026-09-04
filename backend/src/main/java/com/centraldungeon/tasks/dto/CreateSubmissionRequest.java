package com.centraldungeon.tasks.dto;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Handing in an answer to a task.
 *
 * <p><b>Every one of these inserts a new answer</b> (#76). There is no update endpoint and this
 * request carries no id to overwrite: sending a second version leaves the first exactly where it was,
 * because deciding which of two character sheets counts is a judgement the system does not have.
 *
 * <p>The files are <b>already uploaded</b>, exactly like attaching one to a table (#65, #79): the
 * character sheet somebody sent last season is reused by id rather than stored again, which is the
 * cost lever the file feature exists for. Uploading a new one is {@code POST /api/v1/files} and
 * happens before this call.
 *
 * <p>What may be filled in is the task's to decide: the service refuses text on a task that does not
 * take text, files on one that does not take files, and an answer that is empty on both counts.
 *
 * @param content the written answer as rich text, sanitized before it is stored (#62). Null or blank
 *                when the answer is files only
 * @param fileIds the files to hand in, by id. Each has to be the actor's own or one the platform
 *                published (#79) - never somebody else's private upload. Empty when the answer is
 *                text only
 */
public record CreateSubmissionRequest(@Nullable String content, @Nullable List<String> fileIds) {
}
