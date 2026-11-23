package com.centraldungeon.features.auth;

import java.time.Duration;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes the refresh-token rows that can no longer authenticate anyone, so the table does not grow
 * with every login and every renewal for ever.
 *
 * <p><b>An expired row is dead weight.</b> A refresh token past its own expiry is rejected when its
 * signature is verified, before the table is consulted, so its row can neither renew a session nor
 * reveal a reuse: reuse detection only ever needs rows whose token could still be presented. That is
 * why a row that is both revoked and expired goes at once.
 *
 * <p><b>The margin is a short audit trail, not a security need.</b> A row that is merely expired is
 * kept for {@link #RETENTION_AFTER_EXPIRY} so that, when someone investigates a session or a family
 * revoked for reuse, its last rows are still there. It is deliberately short: the table holds one row
 * per renewal, and a longer history would be paid for in storage.
 *
 * <p>⚠️ <b>{@code @Transactional} has to be on the {@code @Scheduled} method too</b>, not only on the
 * one it calls: a call from the scheduled method to {@link #purgeStaleTokens()} is self-invocation and
 * never goes through the Spring proxy, so a bulk delete would run with no transaction and fail.
 *
 * <p>The job is idempotent, so running it on every instance of a multi-instance deployment is harmless.
 */
@Service
public class RefreshTokenPurgeService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenPurgeService.class);

    /** How long an expired, non-revoked row is kept before it is purged. */
    static final Duration RETENTION_AFTER_EXPIRY = Duration.ofDays(7);

    /** The {@code refresh_tokens} rows. */
    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * @param refreshTokenRepository the {@code refresh_tokens} rows
     */
    public RefreshTokenPurgeService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /**
     * Runs the purge once a day, at 06:00 server time, after the other two daily jobs.
     */
    @Scheduled(cron = "0 0 6 * * *")
    @Transactional
    public void purgeExpiredRefreshTokens() {
        int deleted = purgeStaleTokens();
        if (deleted > 0) {
            log.info("Refresh token purge deleted {} stale rows", deleted);
        }
    }

    /**
     * Deletes the rows that expired more than the retention margin ago, and the revoked ones that
     * have expired at all.
     *
     * <p>Separate from the schedule so it can be called and asserted on directly.
     *
     * @return how many rows were deleted; zero is the normal answer on a quiet day
     */
    @Transactional
    public int purgeStaleTokens() {
        LocalDateTime now = LocalDateTime.now();
        return refreshTokenRepository.deleteStale(now.minus(RETENTION_AFTER_EXPIRY), now);
    }
}
