package com.centraldungeon.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.centraldungeon.common.config.StorageProperties;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The one class in F1.4 that touches the filesystem, and the one where the legacy's file handling
 * failed: it built paths out of the name the user typed (M21.5) and left orphans behind whenever a
 * transaction died mid-upload (M21.6).
 */
class LocalDiskStorageServiceTest {

    /** A real directory per test - the point of these cases is what actually lands on disk. */
    @TempDir
    private Path root;

    private LocalDiskStorageService storage;

    @BeforeEach
    void setUp() {
        storage = new LocalDiskStorageService(properties(root));
    }

    @Test
    void storesAndReadsBackTheSameBytes() {
        byte[] content = "una hoja de personaje".getBytes(StandardCharsets.UTF_8);

        storage.store("file-1", content);

        assertThat(storage.read("file-1")).isEqualTo(content);
    }

    /**
     * The compression of #75 lives below the seam: callers hand over and get back the original bytes,
     * and what sits on disk is smaller. Asserting the gzip magic number is what proves the file was
     * not simply written through.
     */
    @Test
    void whatLandsOnDiskIsCompressed() throws Exception {
        storage.store("file-1", "x".repeat(4096).getBytes(StandardCharsets.UTF_8));

        byte[] onDisk = Files.readAllBytes(root.resolve("file-1.gz"));

        assertThat(onDisk[0]).isEqualTo((byte) 0x1f);
        assertThat(onDisk[1]).isEqualTo((byte) 0x8b);
        assertThat(onDisk.length).isLessThan(4096);
    }

    /**
     * #80's guarantee, asserted rather than assumed. Every key the application passes is an id it
     * generated, so this can only fail if some future caller starts passing something a person typed
     * - which is exactly the mistake worth catching.
     */
    @Test
    void refusesAKeyThatIsNotAPlainSegment() {
        List<String> attempts = List.of("../../etc/passwd", "a/b", "a\\b", "..", "hoja de personaje.pdf");

        for (String key : attempts) {
            assertThatThrownBy(() -> storage.store(key, new byte[] {1}))
                    .as("key %s", key)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not a safe path segment");
        }
    }

    @Test
    void readingSomethingThatWasNeverStoredFails() {
        assertThatThrownBy(() -> storage.read("file-1"))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("No stored content");
    }

    @Test
    void existsAnswersForBothCases() {
        storage.store("file-1", new byte[] {1, 2, 3});

        assertThat(storage.exists("file-1")).isTrue();
        assertThat(storage.exists("file-2")).isFalse();
    }

    @Test
    void deleteRemovesTheBlobAndIsIdempotent() {
        storage.store("file-1", new byte[] {1, 2, 3});

        storage.delete("file-1");
        storage.delete("file-1");

        assertThat(storage.exists("file-1")).isFalse();
    }

    /**
     * M26.2: inside a transaction the content waits in staging and only becomes readable once the
     * transaction commits. Before that it is not there at all - which is what stops a row from
     * pointing at a file the rollback was going to take away.
     */
    @Test
    void insideATransactionTheContentLandsOnlyOnCommit() {
        withSynchronization(() -> {
            storage.store("file-1", "confirmado".getBytes(StandardCharsets.UTF_8));

            assertThat(storage.exists("file-1")).isFalse();

            commit();
        });

        assertThat(storage.read("file-1")).asString().isEqualTo("confirmado");
    }

    /** The orphan the legacy left behind on every failed upload (M21.6). Nothing survives a rollback. */
    @Test
    void aRollbackLeavesNothingBehind() {
        withSynchronization(() -> {
            storage.store("file-1", "descartado".getBytes(StandardCharsets.UTF_8));

            rollback();
        });

        assertThat(storage.exists("file-1")).isFalse();
        assertThat(stagingIsEmpty()).isTrue();
    }

    /** A rollback after the blob was already moved has to take the blob back out too. */
    @Test
    void aRollbackAfterTheMoveRemovesTheBlob() {
        withSynchronization(() -> {
            storage.store("file-1", "descartado".getBytes(StandardCharsets.UTF_8));

            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.beforeCommit(false));
            assertThat(storage.exists("file-1")).isTrue();

            rollback();
        });

        assertThat(storage.exists("file-1")).isFalse();
    }

    /** Two files in one transaction both land: each store registers its own confirmation. */
    @Test
    void twoFilesInOneTransactionBothLand() {
        withSynchronization(() -> {
            storage.store("file-1", new byte[] {1});
            storage.store("file-2", new byte[] {2});

            commit();
        });

        assertThat(storage.read("file-1")).isEqualTo(new byte[] {1});
        assertThat(storage.read("file-2")).isEqualTo(new byte[] {2});
        assertThat(stagingIsEmpty()).isTrue();
    }

    /**
     * Drives the synchronization list by hand instead of standing up a transaction manager: the
     * contract this class depends on is that Spring calls beforeCommit and then afterCompletion, and
     * the point of the test is what the callbacks do to the disk, not that Spring honours its own API.
     */
    private static void withSynchronization(Runnable body) {
        TransactionSynchronizationManager.initSynchronization();
        try {
            body.run();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static void commit() {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(s -> s.beforeCommit(false));
        synchronizations.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
    }

    private static void rollback() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    }

    private boolean stagingIsEmpty() {
        try (var entries = Files.list(root.resolve(".staging"))) {
            return entries.findAny().isEmpty();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static StorageProperties properties(Path root) {
        return new StorageProperties(root.toString(), org.springframework.util.unit.DataSize.ofMegabytes(2),
                Set.of("application/pdf"), Duration.ofDays(90));
    }
}
