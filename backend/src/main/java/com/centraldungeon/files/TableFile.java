package com.centraldungeon.files;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * A file attached to a table. Table {@code table_files}.
 *
 * <p><b>This links, it does not copy</b> (#79). Detaching a file from a table removes this row and
 * leaves the file untouched, and editing the file changes what every table using it shows - which is
 * the entire reason a master can use the platform's default character sheet instead of uploading
 * their own.
 *
 * <p><b>{@code isPrivate} is about the link, never about the file.</b> The two are different axes
 * and confusing them is the easiest mistake to make here:
 *
 * <ul>
 *   <li>{@code isPrivate} - <em>in this table</em>, do the players see it, or only the people running
 *       it? A master's own notes are private; the map everyone needs is not.
 *   <li>{@link FileType#Public} - an admin published this file for the whole platform, with its
 *       audience (#64). It says nothing about any particular table.
 * </ul>
 *
 * <p>No id and no {@code updated_at}: it is a bridge row keyed by the pair it joins, same shape as
 * {@code TableSystem} and {@code TableSchedule}.
 */
@Entity
@Table(name = "table_files")
public class TableFile {

    /** The pair (table, file) this link joins. */
    @EmbeddedId
    private TableFileId id;

    /** What the file is doing on the table: prepared beforehand, or produced at a session. */
    @Enumerated(EnumType.STRING)
    @Column(name = "table_file_type", nullable = false, length = 32)
    private TableFileType tableFileType = TableFileType.Preparation;

    /**
     * Whether only the people running this table see it.
     *
     * <p>Defaults to shared, and deliberately so: a master attaching something to their table is
     * usually attaching it <em>for</em> their players. Hiding by default would make the common case
     * the one that needs an extra step, and would quietly produce tables whose material nobody can
     * read.
     */
    @Column(name = "is_private", nullable = false)
    private boolean isPrivate;

    /** Whether the file is still attached, or was detached and kept as a record. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TableFileStatus status = TableFileStatus.Current;

    /** When the file was attached. Stamped on persist; bridge rows have no {@code updated_at}. */
    @Column(name = "created_at", nullable = false)
    private @Nullable LocalDateTime createdAt;

    /** Soft delete (#25): set when the file is detached. The row itself is never dropped. */
    @Column(name = "deleted_at")
    private @Nullable LocalDateTime deletedAt;

    /** Required by JPA. */
    protected TableFile() {
    }

    /**
     * Attaches a file to a table, live from the start.
     *
     * @param gameTableId   the table the file is being attached to
     * @param fileId        the file being attached. It is not copied (#79)
     * @param tableFileType what the file is doing on the table
     * @param isPrivate     true to keep it to the people running the table
     */
    public TableFile(String gameTableId, String fileId, TableFileType tableFileType, boolean isPrivate) {
        this.id = new TableFileId(gameTableId, fileId);
        this.tableFileType = tableFileType;
        this.isPrivate = isPrivate;
    }

    /** Stamps {@code createdAt} on insert. Called by JPA, never by application code. */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * Returns the pair this link joins.
     *
     * @return the composite key, never null on a persisted row
     */
    public TableFileId getId() {
        return id;
    }

    /**
     * Returns what the file is doing on the table.
     *
     * @return the attachment kind, never null
     */
    public TableFileType getTableFileType() {
        return tableFileType;
    }

    /**
     * Changes what the file is doing on the table.
     *
     * @param tableFileType the new attachment kind
     */
    public void setTableFileType(TableFileType tableFileType) {
        this.tableFileType = tableFileType;
    }

    /**
     * Returns whether only the people running the table see it.
     *
     * @return true when the file is not shared with the table's players and candidates
     */
    public boolean isPrivate() {
        return isPrivate;
    }

    /**
     * Shares the file with the table, or takes it back to the people running it.
     *
     * @param isPrivate true to keep it to the masters
     */
    public void setPrivate(boolean isPrivate) {
        this.isPrivate = isPrivate;
    }

    /**
     * Returns whether the file is still attached.
     *
     * @return the link's status, never null
     */
    public TableFileStatus getStatus() {
        return status;
    }

    /**
     * Attaches or detaches the file. Detaching never touches the file itself (#79), and re-attaching
     * revives this row rather than inserting a second one - the pair is the key.
     *
     * @param status the new status
     */
    public void setStatus(TableFileStatus status) {
        this.status = status;
    }

    /**
     * Returns when the file was attached.
     *
     * @return the creation timestamp, never null on a persisted row
     */
    public @Nullable LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Returns when the file was detached.
     *
     * @return the timestamp of the logical delete, or null while the file is attached
     */
    public @Nullable LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    /**
     * Stamps or clears the logical delete of the link.
     *
     * @param deletedAt when it was detached, or null to bring it back
     */
    public void setDeletedAt(@Nullable LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}
