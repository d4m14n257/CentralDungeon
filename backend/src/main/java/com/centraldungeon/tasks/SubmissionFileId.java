package com.centraldungeon.tasks;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

/**
 * Composite key of {@link SubmissionFile}: one submission attaches a given file once. Same pattern as
 * {@code TableFileId} and {@code MasterId}.
 *
 * <p>Because the pair <b>is</b> the key, attaching the same file to the same submission twice cannot
 * insert a second row - it revives the one already there.
 *
 * @param submissionId the answer the file was handed in with. A plain id and not an association, so
 *                     the link can be counted and filtered without loading either side. The database
 *                     still enforces the foreign key
 * @param fileId       the file being attached. It is linked, never copied (#65, #79)
 */
@Embeddable
public record SubmissionFileId(
        @Column(name = "submission_id", length = 64) String submissionId,
        @Column(name = "file_id", length = 64) String fileId)
        implements Serializable {
}
