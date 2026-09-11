package com.centraldungeon.files;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * A file's membership in one cajón. Table {@code file_categories}.
 *
 * <p><b>Add-only.</b> Unlike every other bridge row in this schema, this one carries no status and no
 * {@code deleted_at}, and nothing ever takes it away. The reason is what it records: {@code
 * table_files} says a file is attached to a table <em>right now</em>, while this says it has
 * <em>been</em> that table's material. Detaching changes the first and leaves the second true, and
 * letting go of the file marks {@code files.status} while these rows stand as the record (#25).
 *
 * <p>Two things write one of these, and a reader cannot tell them apart on purpose:
 *
 * <ul>
 *   <li><b>A real use.</b> Attaching to a table, handing in an answer, applying - the service adds
 *       the cajón as a side effect of the link it just made.
 *   <li><b>A declaration.</b> An admin publishing a blank into a cajón, or somebody uploading from
 *       their own library where there is no flow to observe. A published file is attached to nothing,
 *       so there is nothing to derive from - it is offered <em>for</em> a flow rather than used
 *       <em>in</em> one.
 * </ul>
 *
 * <p>That the two are indistinguishable is what makes the picker work: it asks for one cajón and gets
 * the community's blanks and the reader's own files back, in one shape.
 */
@Entity
@Table(name = "file_categories")
public class FileCategoryLink {

    /** The pair (file, cajón) this row joins. */
    @EmbeddedId
    private FileCategoryLinkId id;

    /** When the file first entered this cajón. Stamped on persist; there is no {@code updated_at}. */
    @Column(name = "created_at", nullable = false)
    private @Nullable LocalDateTime createdAt;

    /** Required by JPA. */
    protected FileCategoryLink() {
    }

    /**
     * Puts a file in a cajón. Doing it twice is not an error - the pair is the key, so the second
     * attempt is the row that is already there.
     *
     * @param fileId   the file
     * @param category the cajón it belongs to from now on
     */
    public FileCategoryLink(String fileId, FileCategory category) {
        this.id = new FileCategoryLinkId(fileId, category);
    }

    /** Stamps {@code createdAt} on insert. Called by JPA, never by application code. */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * Returns the pair this row joins.
     *
     * @return the composite key, never null on a persisted row
     */
    public FileCategoryLinkId getId() {
        return id;
    }

    /**
     * Returns when the file first entered this cajón.
     *
     * @return the creation timestamp, never null on a persisted row
     */
    public @Nullable LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
