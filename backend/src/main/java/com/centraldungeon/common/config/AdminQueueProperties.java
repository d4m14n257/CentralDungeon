package com.centraldungeon.common.config;

import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How long an admin may hold an item of the shared tray before it goes back, bound from
 * {@code app.admin-queue.*} (#100).
 *
 * <p>Configuration and not a constant for the same reason {@link StorageProperties#retention()} is:
 * the right number is an operational judgement - how long a review actually takes on this platform -
 * and tightening it should never be a deploy of new code, let alone a migration.
 *
 * <p><b>F3.5 moves it to {@code system_settings}</b> (#141 enumerates it among the values the owner
 * edits from the administration menu). Until then it lives here, which is the smallest thing that
 * satisfies «configurable, arranca en 15» without inventing half of that screen early.
 *
 * @param claimTimeout how long a reservation survives without being resolved. The release job
 *                     ({@code AdminQueueClaimReleaseService}) hands back everything older than this,
 *                     so an admin who took an item and closed the tab does not block it for the rest.
 *                     Defaults to 15 minutes when nothing configures it
 */
@ConfigurationProperties(prefix = "app.admin-queue")
public record AdminQueueProperties(Duration claimTimeout) {

    /** The starting value #100 names, applied when no configuration file sets one. */
    private static final Duration DEFAULT_CLAIM_TIMEOUT = Duration.ofMinutes(15);

    /**
     * Fills in the default, so that a profile which says nothing about the tray still gets a working
     * timeout instead of a null that only fails when the job first runs.
     *
     * @param claimTimeout the configured timeout, or null when nothing configured one
     */
    public AdminQueueProperties(@Nullable Duration claimTimeout) {
        this.claimTimeout = claimTimeout == null ? DEFAULT_CLAIM_TIMEOUT : claimTimeout;
    }
}
