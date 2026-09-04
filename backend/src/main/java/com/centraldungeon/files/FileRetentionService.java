package com.centraldungeon.files;

import com.centraldungeon.common.config.StorageProperties;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lets go of the files nobody has used in months (#75).
 *
 * <p><b>This is the biggest cost lever the platform has</b>, and the reason #75 could repeal the
 * per-user quota of #61 without giving anything up: a quota limits the person while the bill keeps
 * growing - 20 MB each is 20 GB across a thousand players - whereas purging what nobody needs
 * actually removes volume. The other two levers are reuse (#65) and compression, both in
 * {@code FileService} and {@code LocalDiskStorageService}.
 *
 * <p><b>It marks, it does not erase</b> (#25). Not a limitation to be fixed later: freeing the actual
 * bytes is a deliberate maintenance operation, run by the platform owner from the administration
 * menu, and that is F5 (#66). Marking gives a wide margin to notice a mistake before anything becomes
 * irreversible.
 *
 * <p>Which files qualify is {@link StoredFileRepository#findPurgeCandidates} - published files are
 * exempt, a live attachment keeps a file alive, and a file never used counts from the day it arrived.
 */
@Service
public class FileRetentionService {

    private static final Logger log = LoggerFactory.getLogger(FileRetentionService.class);

    /**
     * How many files one pass takes.
     *
     * <p>Bounded on purpose. The first run after this ships will find every file the platform has ever
     * accumulated, and loading all of them into one transaction is how a maintenance job turns into an
     * outage. What is left over is picked up by the next run - the job is idempotent, so running it
     * many times is the same as running it once.
     */
    private static final int BATCH_SIZE = 200;

    /** The {@code files} rows, and the query that decides which of them have gone unused. */
    private final StoredFileRepository fileRepository;

    /** Where the retention window comes from - configuration, so it can be tightened without a migration. */
    private final StorageProperties storageProperties;

    /**
     * @param fileRepository    the {@code files} rows
     * @param storageProperties the retention window, from {@code app.storage.retention}
     */
    public FileRetentionService(StoredFileRepository fileRepository, StorageProperties storageProperties) {
        this.fileRepository = fileRepository;
        this.storageProperties = storageProperties;
    }

    /**
     * Runs the purge once a day, in the small hours.
     *
     * <p>The time is not arbitrary: the community plays at night in the Americas, which is the early
     * morning of the next day in UTC (#22), so 05:00 UTC is roughly when the fewest people are around
     * to notice a batch of writes.
     */
    @Scheduled(cron = "0 0 5 * * *")
    public void purgeUnusedFiles() {
        int marked = markUnusedFiles();
        if (marked > 0) {
            log.info("Retention pass marked {} unused files (window {})", marked, storageProperties.retention());
        }
    }

    /**
     * Marks one batch of unused files gone.
     *
     * <p>Separate from the schedule so it can be called and asserted on directly - a test should not
     * have to wait for a cron expression to come round, and the rule worth testing is which files
     * qualify, not that Spring can read a crontab.
     *
     * @return how many files this pass marked. Zero means nothing had gone stale, which is the normal
     *         answer on a healthy platform
     */
    @Transactional
    public int markUnusedFiles() {
        LocalDateTime cutoff = LocalDateTime.now().minus(storageProperties.retention());
        List<StoredFile> candidates =
                fileRepository.findPurgeCandidates(cutoff, FileType.Public, PageRequest.of(0, BATCH_SIZE));

        LocalDateTime now = LocalDateTime.now();
        for (StoredFile file : candidates) {
            file.setStatus(FileStatus.Deleted);
            file.setDeletedAt(now);
        }
        return candidates.size();
    }
}
