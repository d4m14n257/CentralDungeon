package com.centraldungeon.common.storage;

/**
 * Where the bytes of an uploaded file live, behind one seam.
 *
 * <p><b>This is the interface that earns being an interface</b> (arquitectura.md 2.4, #15). Today
 * the only implementation writes to local disk; the plan is that it can write to S3 later without
 * the rest of the backend noticing. It is not an {@code Impl} pair created out of habit.
 *
 * <p>The key is opaque to every caller. {@code FileService} passes the file's own id, which is what
 * makes the physical name the id and never the name the user typed (#80) - the legacy concatenated
 * the original filename unsanitized into the path, which is path traversal with extra steps (M21.5).
 * Whether an implementation prefixes, shards or compresses under that key is its own business.
 *
 * <p>Failures here are bugs, not business rules: they surface as {@link java.io.UncheckedIOException}
 * and answer 500. Nothing in this interface throws an {@code ApiException}.
 */
public interface StorageService {

    /**
     * Writes the content under a key, becoming durable only if the surrounding transaction commits.
     *
     * <p><b>The database and the filesystem do not share a commit</b> (M26.2). Writing straight away
     * leaves an orphan behind whenever the transaction that was going to record the file rolls back
     * - the legacy knew about this and left it as an unresolved TODO (M21.6). So the content goes to
     * a staging area first and is only moved into place after the commit.
     *
     * @param key     the name to store under. Callers pass the file's id (#80)
     * @param content the bytes exactly as they were uploaded, uncompressed
     * @throws java.io.UncheckedIOException if the content cannot be written
     * @throws IllegalArgumentException     if the key is not a safe single path segment
     */
    void store(String key, byte[] content);

    /**
     * Reads back what was stored under a key.
     *
     * @param key the key the content was stored under
     * @return the bytes as they were uploaded, whatever the implementation did to them in between
     * @throws java.io.UncheckedIOException if the content is missing or cannot be read
     * @throws IllegalArgumentException     if the key is not a safe single path segment
     */
    byte[] read(String key);

    /**
     * Removes the bytes for good.
     *
     * <p><b>Nothing in F1 calls this.</b> Deleting a file in the application is logical (#25): the
     * row is marked and the bytes stay. Freeing real space is a deliberate maintenance operation the
     * platform owner runs, and it is F5 (#66) - this method is what that will be built on.
     *
     * @param key the key to remove
     * @throws java.io.UncheckedIOException if the content exists but cannot be removed
     * @throws IllegalArgumentException     if the key is not a safe single path segment
     */
    void delete(String key);

    /**
     * Whether anything is stored under a key.
     *
     * @param key the key to look for
     * @return true if the content is there and readable
     * @throws IllegalArgumentException if the key is not a safe single path segment
     */
    boolean exists(String key);
}
