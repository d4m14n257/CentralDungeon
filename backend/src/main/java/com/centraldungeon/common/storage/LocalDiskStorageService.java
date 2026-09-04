package com.centraldungeon.common.storage;

import com.centraldungeon.common.config.StorageProperties;
import com.centraldungeon.common.model.IdGenerator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * {@link StorageService} over a directory on local disk (#15).
 *
 * <p><b>Content is gzipped on the way in and expanded on the way out</b>, which is the compression
 * lever of #75 and is invisible above this class: what callers hand over and get back are the
 * original bytes, and {@code files.size_bytes} records the original size - that is the number shown
 * on screen and the one checked against the cap.
 *
 * <p><b>A key is a single path segment and is validated as one.</b> Every key the application passes
 * is an id it generated (#80), so the check can never fail in practice - which is exactly why it is
 * here. The legacy built its paths from the filename the user typed (M21.5); the guarantee that no
 * user-supplied text reaches the filesystem is worth asserting rather than assuming.
 */
@Service
public class LocalDiskStorageService implements StorageService {

    /** A key is an id: letters, digits, dash and underscore. No dots, no separators, no traversal. */
    private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z0-9_-]{1,200}");

    /** What the compressed blob is called on disk. The key stays the key; this is storage's business. */
    private static final String BLOB_SUFFIX = ".gz";

    /** Where content waits until the transaction that records it commits (M26.2). */
    private static final String STAGING_DIRECTORY = ".staging";

    /** The directory the blobs live under, absolute and normalized. */
    private final Path root;

    /** The staging area, inside the root so the confirming move never crosses a filesystem. */
    private final Path staging;

    /**
     * @param storageProperties where the blobs live, from {@code app.storage.root}
     * @throws UncheckedIOException if the root or its staging area cannot be created
     */
    public LocalDiskStorageService(StorageProperties storageProperties) {
        this.root = Path.of(storageProperties.root()).toAbsolutePath().normalize();
        this.staging = root.resolve(STAGING_DIRECTORY);
        try {
            Files.createDirectories(staging);
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot create the storage root at " + root, exception);
        }
    }

    /**
     * Writes the content to the staging area and moves it into place when the transaction commits.
     *
     * <p>The move happens in {@code beforeCommit} rather than after: a filesystem that cannot take
     * the file then fails the transaction, so the row describing it is never written. The reverse
     * order - commit first, move afterwards - would swallow that failure, because an exception
     * thrown after a commit cannot undo it, and would leave a row pointing at nothing.
     *
     * <p>Outside a transaction the move is immediate. That is the path the tests take and it is not
     * a fallback the application uses: every caller of this method is inside a {@code @Transactional}
     * service method.
     *
     * @param key     the name to store under - the file's id (#80)
     * @param content the bytes exactly as they were uploaded, uncompressed
     */
    @Override
    public void store(String key, byte[] content) {
        Path target = blobPath(key);
        Path staged = staging.resolve(key + "-" + IdGenerator.newId() + BLOB_SUFFIX);
        writeCompressed(staged, content);

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            moveIntoPlace(staged, target);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new ConfirmOnCommit(staged, target));
    }

    /**
     * @param key the key the content was stored under
     * @return the original, uncompressed bytes
     */
    @Override
    public byte[] read(String key) {
        Path blob = blobPath(key);
        try (GZIPInputStream in = new GZIPInputStream(Files.newInputStream(blob))) {
            return in.readAllBytes();
        } catch (NoSuchFileException exception) {
            throw new UncheckedIOException("No stored content for key " + key, exception);
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot read stored content for key " + key, exception);
        }
    }

    /**
     * @param key the key to remove
     */
    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(blobPath(key));
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot delete stored content for key " + key, exception);
        }
    }

    /**
     * @param key the key to look for
     * @return true if a readable blob is stored under it
     */
    @Override
    public boolean exists(String key) {
        return Files.isReadable(blobPath(key));
    }

    /** Resolves a key to its blob, refusing anything that is not a plain single segment. */
    private Path blobPath(String key) {
        if (!SAFE_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Storage key is not a safe path segment: " + key);
        }
        return root.resolve(key + BLOB_SUFFIX);
    }

    /** Compresses the content into a file that does not exist yet. */
    private static void writeCompressed(Path destination, byte[] content) {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
            gzip.write(content);
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot compress content for " + destination, exception);
        }
        try {
            Files.write(destination, compressed.toByteArray());
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot stage content at " + destination, exception);
        }
    }

    /** Moves a staged blob to its final name, replacing nothing and losing nothing on the way. */
    private static void moveIntoPlace(Path staged, Path target) {
        try {
            Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot move staged content to " + target, exception);
        }
    }

    /** Best-effort cleanup: a leftover in staging is garbage, and failing to remove it is not an error. */
    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Nothing useful to do: the transaction already decided its outcome.
        }
    }

    /**
     * Ties a staged blob to the outcome of the transaction that is recording it (M26.2).
     *
     * <p>Not a lambda: the two halves run at different moments and the second one needs to know
     * whether the first got as far as moving the file.
     */
    private static final class ConfirmOnCommit implements TransactionSynchronization {

        /** Where the compressed content is waiting. */
        private final Path staged;

        /** Where it belongs once the transaction commits. */
        private final Path target;

        /** Whether the move already happened - what tells a rollback if there is a blob to undo. */
        private boolean moved;

        private ConfirmOnCommit(Path staged, Path target) {
            this.staged = staged;
            this.target = target;
        }

        @Override
        public void beforeCommit(boolean readOnly) {
            moveIntoPlace(staged, target);
            moved = true;
        }

        @Override
        public void afterCompletion(int status) {
            deleteQuietly(staged);
            if (status != STATUS_COMMITTED && moved) {
                deleteQuietly(target);
            }
        }
    }
}
