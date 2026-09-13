package com.centraldungeon.registrations;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

/**
 * Composite key of {@link RegistrationFile}: one application attaches a given file once. Same
 * pattern as {@code SubmissionFileId} and {@code TaskFileId}.
 *
 * <p>Because the pair <b>is</b> the key, attaching the same file to the same application twice
 * cannot insert a second row - it revives the one already there.
 *
 * @param registrationId the application the file was submitted with. A plain id and not an
 *                        association, so the link can be counted and filtered without loading
 *                        either side. The database still enforces the foreign key
 * @param fileId          the file being attached. It is linked, never copied (#65, #79)
 */
@Embeddable
public record RegistrationFileId(
        @Column(name = "registration_id", length = 64) String registrationId,
        @Column(name = "file_id", length = 64) String fileId)
        implements Serializable {
}
