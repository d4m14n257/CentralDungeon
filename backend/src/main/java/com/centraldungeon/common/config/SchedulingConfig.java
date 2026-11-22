package com.centraldungeon.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on {@code @Scheduled}.
 *
 * <p>Its own class rather than another annotation on {@code CentralDungeonApplication}, so that
 * disabling background work in an environment is deleting one file's worth of configuration rather
 * than editing the application's entry point.
 *
 * <p>Four jobs exist: {@code FileRetentionService}, which marks the files nobody has used in months;
 * {@code ApprovalOrphanCheckService}, which reports the requests pointing at something that is
 * gone; {@code AdminQueueClaimReleaseService}, which hands back the reservations of the shared tray
 * that nobody finished; and {@code RefreshTokenPurgeService}, which deletes the refresh-token rows
 * that can no longer authenticate anyone. The other three are daily crons staggered half an hour
 * apart; {@code AdminQueueClaimReleaseService} is the only one on a short interval, because fifteen
 * minutes is a promise a daily sweep could not keep.
 *
 * <p>The scheduler is per JVM. With more than one instance every one of them runs the job, which is
 * the same limitation the Caffeine cache and the in-memory STOMP broker have and which the
 * architecture already accepts. It is harmless here - marking a file gone twice leaves it gone once -
 * but anything scheduled later that is <em>not</em> idempotent needs a lock first.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
