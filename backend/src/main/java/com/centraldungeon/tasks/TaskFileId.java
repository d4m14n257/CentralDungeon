package com.centraldungeon.tasks;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

/**
 * Composite key of {@link TaskFile}: a request attaches a given file once. Same pattern as
 * {@code SubmissionFileId} and {@code TableFileId}.
 *
 * <p>Because the pair <b>is</b> the key, attaching a file that was taken off earlier revives the row
 * that is already there rather than failing on a duplicate key.
 *
 * @param taskId the request the file is attached to
 * @param fileId the file being attached. It is linked, never copied (#65, #79)
 */
@Embeddable
public record TaskFileId(
        @Column(name = "task_id", length = 64) String taskId,
        @Column(name = "file_id", length = 64) String fileId)
        implements Serializable {
}
