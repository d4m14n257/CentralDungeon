package com.centraldungeon.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.centraldungeon.common.config.StorageProperties;
import com.centraldungeon.users.User;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;

/**
 * The purge of #75, which is the lever that let the per-user quota of #61 be repealed: a quota limits
 * the person while the bill keeps growing, and this actually removes volume.
 *
 * <p>Which files qualify is a query, so what is asserted here is the contract around it - the cutoff
 * it is asked for, that a pass only marks, and that nothing physical happens (#25, #66).
 */
@ExtendWith(MockitoExtension.class)
class FileRetentionServiceTest {

    @Mock
    private StoredFileRepository fileRepository;

    private final StorageProperties storageProperties = new StorageProperties(
            "target/test-storage", DataSize.ofMegabytes(2), Set.of("application/pdf"), Duration.ofDays(90));

    private FileRetentionService retentionService() {
        return new FileRetentionService(fileRepository, storageProperties);
    }

    @Test
    void asksForTheFilesUnusedForLongerThanTheConfiguredWindow() {
        when(fileRepository.findPurgeCandidates(any(), eq(FileType.Public), any())).thenReturn(List.of());
        LocalDateTime before = LocalDateTime.now().minusDays(90);

        retentionService().markUnusedFiles();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        org.mockito.Mockito.verify(fileRepository)
                .findPurgeCandidates(cutoff.capture(), eq(FileType.Public), any(Pageable.class));
        assertThat(cutoff.getValue()).isBetween(before.minusMinutes(1), before.plusMinutes(1));
    }

    /** Marks and stamps. It never calls storage, because freeing bytes is F5's job (#25, #66). */
    @Test
    void marksTheCandidatesGoneAndStampsWhen() {
        StoredFile stale = file("file-1");
        StoredFile alsoStale = file("file-2");
        when(fileRepository.findPurgeCandidates(any(), eq(FileType.Public), any()))
                .thenReturn(List.of(stale, alsoStale));

        int marked = retentionService().markUnusedFiles();

        assertThat(marked).isEqualTo(2);
        assertThat(stale.getStatus()).isEqualTo(FileStatus.Deleted);
        assertThat(stale.getDeletedAt()).isNotNull();
        assertThat(alsoStale.getStatus()).isEqualTo(FileStatus.Deleted);
    }

    @Test
    void aPassThatFindsNothingChangesNothing() {
        when(fileRepository.findPurgeCandidates(any(), eq(FileType.Public), any())).thenReturn(List.of());

        assertThat(retentionService().markUnusedFiles()).isZero();
    }

    /**
     * The batch is bounded on purpose: the first run after this ships meets every file the platform
     * ever accumulated, and loading all of them into one transaction turns maintenance into an outage.
     */
    @Test
    void takesOneBoundedBatchPerPass() {
        when(fileRepository.findPurgeCandidates(any(), eq(FileType.Public), any())).thenReturn(List.of());

        retentionService().markUnusedFiles();

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(fileRepository)
                .findPurgeCandidates(any(), eq(FileType.Public), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isPositive();
        assertThat(pageable.getValue().getPageNumber()).isZero();
    }

    private static StoredFile file(String id) {
        User owner = new User("discord-" + id, id);
        ReflectionTestUtils.setField(owner, "id", "owner-" + id);
        StoredFile file = new StoredFile("viejo.pdf", "key-" + id, "hash", "application/pdf", 10, FileType.Private, owner);
        ReflectionTestUtils.setField(file, "id", id);
        return file;
    }
}
