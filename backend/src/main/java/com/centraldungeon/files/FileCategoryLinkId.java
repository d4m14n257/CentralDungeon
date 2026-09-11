package com.centraldungeon.files;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serializable;

/**
 * Composite key of {@link FileCategoryLink}: a file is in a given cajón once. Same pattern as
 * {@code TableFileId} and {@code SubmissionFileId}.
 *
 * <p>Because the pair <b>is</b> the key, adding a file to a cajón it already belongs to cannot insert
 * a second row. That is what makes the write idempotent everywhere it happens - a master attaching
 * their sheet to a fifth table adds no row at all, and the services can call it without first asking
 * whether they need to.
 *
 * @param fileId   the file. A plain id and not an association, so a cajón can be counted and filtered
 *                 without loading the file. The database still enforces the foreign key
 * @param category the cajón. Stored as the constant's name, like every other enum in this schema (#10)
 */
@Embeddable
public record FileCategoryLinkId(
        @Column(name = "file_id", length = 64) String fileId,
        @Enumerated(EnumType.STRING) @Column(name = "category", length = 32) FileCategory category)
        implements Serializable {
}
