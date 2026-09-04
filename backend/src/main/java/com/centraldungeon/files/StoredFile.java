package com.centraldungeon.files;

import com.centraldungeon.common.model.BaseEntity;
import com.centraldungeon.users.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * One uploaded file, as the system knows it. Table {@code files}.
 *
 * <p><b>Named {@code StoredFile} and not {@code File}</b>, for the same reason {@code GameSystem} is
 * not {@code System} (#182): the short name collides with a JDK type that everything in this package
 * would otherwise have to import around. The clash is with {@code java.io.File}, which this feature
 * legitimately uses one layer below.
 *
 * <p>This is the file gestor of the whole system, with the four uses of #60: what a master attaches
 * to their table, what a player answers an application with, what an admin publishes for everybody,
 * and the history any of them reuses from.
 *
 * <p><b>{@code name} and {@code storageKey} are two different things and that is the point</b> (#80).
 * The name is whatever the person called the file and it is metadata for the download header,
 * nothing more; the key is a server-generated id and is the only thing that ever reaches the
 * filesystem. The legacy built its path out of the name, unsanitized (M21.5).
 *
 * <p>The key is <b>a</b> generated id rather than literally this row's id, which is the one place
 * this departs from how #80 phrases it. The row's id is assigned on persist, so making the two equal
 * would cost an insert plus an update on every upload to buy nothing: the property #80 exists for is
 * that the physical name is opaque and server-chosen, and a second UUID v7 is exactly as opaque as
 * the first. {@code uk_files_storage_key} keeps it unique, and the column is the seam a future S3
 * implementation would prefix anyway.
 */
@Entity
@Table(name = "files")
public class StoredFile extends BaseEntity {

    /**
     * The original filename, exactly as it was uploaded. <b>Metadata only</b> (#80): it is echoed
     * back in the download's {@code Content-Disposition} and never used to build a path.
     */
    @Column(nullable = false, length = 256)
    private String name;

    /** What {@code StorageService} was told to store the content under: a generated id (#80). */
    @Column(name = "storage_key", nullable = false, length = 256)
    private String storageKey;

    /** SHA-256 of the content, which is what lets the same upload be recognised again (#75). */
    @Column(name = "content_hash", length = 64)
    private @Nullable String contentHash;

    /** The declared MIME type, checked against the whitelist before anything is written (M21.4). */
    @Column(name = "mime_type", nullable = false, length = 128)
    private String mimeType;

    /** The <b>uncompressed</b> size. What the screen shows and what the per-file cap is checked against. */
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** Which lifecycle this file has (#68). Persisted through {@link FileTypeConverter}. */
    @Column(name = "file_type", nullable = false, length = 32)
    private FileType fileType = FileType.SingleUse;

    /** Who a published file is for (#64). Null on anything that is not {@link FileType#Public}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "public_audience", length = 32)
    private @Nullable PublicAudience publicAudience;

    /**
     * Who uploaded it. A file belongs to whoever put it there, and that never changes - publishing it
     * (#64) changes what it is for, not whose it is.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_created_id", nullable = false)
    private User userCreated;

    /**
     * When it was last uploaded, attached or downloaded.
     *
     * <p>This is the column #75 says has to exist and did not: without it the purge has no way to
     * tell a file nobody needs from one that is used every week, and "purge what has gone unused" is
     * the biggest cost lever there is.
     */
    @Column(name = "last_used_at")
    private @Nullable LocalDateTime lastUsedAt;

    /** Live or marked gone. Marking never frees a byte (#25, #66). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FileStatus status = FileStatus.Current;

    /** Soft delete (#25): when it was marked gone. Null while live. */
    @Column(name = "deleted_at")
    private @Nullable LocalDateTime deletedAt;

    /** Required by JPA. */
    protected StoredFile() {
    }

    /**
     * Records a freshly uploaded file.
     *
     * @param name        the original filename, kept as metadata and never used as a path (#80)
     * @param storageKey  what the content was stored under - a generated id, never derived from the
     *                    name
     * @param contentHash SHA-256 of the content, for recognising the same upload again (#75)
     * @param mimeType    the declared MIME type, already checked against the whitelist
     * @param sizeBytes   the uncompressed size in bytes
     * @param fileType    whether the uploader is keeping it or it is tied to one context (#68)
     * @param userCreated who uploaded it
     */
    public StoredFile(String name, String storageKey, String contentHash, String mimeType, long sizeBytes,
            FileType fileType, User userCreated) {
        this.name = name;
        this.storageKey = storageKey;
        this.contentHash = contentHash;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.fileType = fileType;
        this.userCreated = userCreated;
        this.lastUsedAt = LocalDateTime.now();
    }

    /**
     * Returns the original filename.
     *
     * @return the name the file was uploaded under, never null
     */
    public String getName() {
        return name;
    }

    /**
     * Renames the file. Only its metadata changes - the content stays exactly where it is, because
     * the key it lives under is the id and has nothing to do with the name (#80).
     *
     * @param name the new name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the key the content is stored under.
     *
     * @return the storage key, never null. A generated id, unrelated to {@link #getName()} (#80)
     */
    public String getStorageKey() {
        return storageKey;
    }

    /**
     * Returns the SHA-256 of the content.
     *
     * @return the hash, or null on a row written before hashing existed
     */
    public @Nullable String getContentHash() {
        return contentHash;
    }

    /**
     * Returns the declared MIME type.
     *
     * @return the MIME type, never null
     */
    public String getMimeType() {
        return mimeType;
    }

    /**
     * Returns the uncompressed size.
     *
     * @return the size in bytes as it was uploaded, whatever storage did to it afterwards
     */
    public long getSizeBytes() {
        return sizeBytes;
    }

    /**
     * Returns which lifecycle this file has.
     *
     * @return the file type, never null
     */
    public FileType getFileType() {
        return fileType;
    }

    /**
     * Moves the file to another lifecycle: promoting a {@code Single-use} into the owner's library is
     * the "save this for later" of #68, and publishing makes it the platform's (#64).
     *
     * @param fileType the new type
     */
    public void setFileType(FileType fileType) {
        this.fileType = fileType;
    }

    /**
     * Returns who a published file is for.
     *
     * @return the audience, or null on anything that is not {@link FileType#Public} (#64)
     */
    public @Nullable PublicAudience getPublicAudience() {
        return publicAudience;
    }

    /**
     * Declares who a published file is for, or clears it when the file stops being published.
     *
     * @param publicAudience the audience, or null
     */
    public void setPublicAudience(@Nullable PublicAudience publicAudience) {
        this.publicAudience = publicAudience;
    }

    /**
     * Returns who uploaded the file.
     *
     * @return the uploader, never null on a persisted row
     */
    public User getUserCreated() {
        return userCreated;
    }

    /**
     * Returns when the file was last used.
     *
     * @return the timestamp of the last upload, attachment or download, or null if it was never
     *         recorded - which the purge treats as the creation date (#75)
     */
    public @Nullable LocalDateTime getLastUsedAt() {
        return lastUsedAt;
    }

    /**
     * Records that the file was just used. Called on every attachment and every download, which is
     * what keeps a file in active service from being purged (#75).
     *
     * @param lastUsedAt when it was used
     */
    public void setLastUsedAt(LocalDateTime lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    /**
     * Returns whether the file still counts.
     *
     * @return the status, never null
     */
    public FileStatus getStatus() {
        return status;
    }

    /**
     * Marks the file live or gone. Marking is the whole delete F1 has: the bytes stay until the
     * platform owner purges them, which is F5 (#25, #66).
     *
     * @param status the new status
     */
    public void setStatus(FileStatus status) {
        this.status = status;
    }

    /**
     * Returns when the file was marked gone.
     *
     * @return the timestamp of the logical delete, or null while the file is live
     */
    public @Nullable LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    /**
     * Stamps or clears the logical delete.
     *
     * @param deletedAt when it was marked gone, or null to bring it back
     */
    public void setDeletedAt(@Nullable LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}
