package com.centraldungeon.files;

import java.util.Arrays;
import java.util.Optional;

/**
 * What kind of life a file has (#68). Each answers to a different lifecycle, which is the whole reason
 * they were kept when #65 had briefly collapsed them into one. {@link #Public} and {@link #Library} are
 * the platform's library, published and not (#282); the other two are somebody's own.
 *
 * <p>The stored value is <b>not</b> the constant's name: {@code Single-use} carries a hyphen, which
 * Java identifiers cannot. {@link FileTypeConverter} owns that mapping, the same way
 * {@code CatalogTypeConverter} owns the path segments of the catalogs.
 */
public enum FileType {

    /**
     * Published by an admin for the whole platform, with the audience of #64. Belongs to nobody in
     * particular, so a master may attach one to their table without copying it (#79) and the purge
     * of #75 leaves it alone.
     */
    Public("Public"),

    /**
     * In the platform's library but offered to nobody (#282): uploaded by an admin and not published
     * yet, or published once and hidden again - {@code StoredFile.publishedAt} tells the two apart.
     *
     * <p>The platform's, not the uploader's, exactly like {@link #Public}: it never shows in anybody's
     * own library, only an admin changes or removes it, and the purge of #75 leaves it alone. What it
     * does not do is appear in a picker, so no master can attach it while it waits.
     */
    Library("Library"),

    /**
     * The uploader chose to keep it. Lives as long as they want it and is what the history of #65
     * offers when they attach something - the reuse that makes the same character sheet cost one
     * upload rather than one per table.
     */
    Private("Private"),

    /**
     * Uploaded for one context and transient by nature. Promoting one to {@link #Private} is the
     * "save this for later" action (#68) - literally what {@code handlePrivateStatus} did in the
     * legacy (M21.2).
     */
    SingleUse("Single-use");

    /**
     * Whether this lifecycle is the platform's library rather than somebody's own (#282): published or
     * not, such a file belongs to the platform, never shows in a personal library, only an admin
     * changes or removes it, and the purge leaves it alone.
     *
     * @return true for {@link #Public} and {@link #Library}
     */
    public boolean isInLibrary() {
        return this == Public || this == Library;
    }

    /** The value as it is written in {@code files.file_type}. */
    private final String storedValue;

    /**
     * @param storedValue the value as it is written in the column
     */
    FileType(String storedValue) {
        this.storedValue = storedValue;
    }

    /**
     * Returns the value as it is written in the database.
     *
     * @return the stored value, never null
     */
    public String storedValue() {
        return storedValue;
    }

    /**
     * Resolves a stored value back to its constant.
     *
     * @param storedValue the value read from the column
     * @return the matching type, or empty when the column holds something no constant covers
     */
    public static Optional<FileType> fromStoredValue(String storedValue) {
        return Arrays.stream(values()).filter(type -> type.storedValue.equals(storedValue)).findFirst();
    }
}
