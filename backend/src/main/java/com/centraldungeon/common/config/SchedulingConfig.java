package com.centraldungeon.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on {@code @Scheduled}, which nothing needed until F1.4.
 *
 * <p>Its own class rather than another annotation on {@code CentralDungeonApplication}, so that
 * disabling background work in an environment is deleting one file's worth of configuration rather
 * than editing the application's entry point.
 *
 * <p>Only one job exists so far: {@code FileRetentionService}, which marks the files nobody has used
 * in months (#75).
 *
 * <p>⚠️ <b>The scheduler is per JVM.</b> With more than one instance every one of them runs the job,
 * which is the same limitation the Caffeine cache (#128) and the in-memory STOMP broker (#101) have
 * and which the architecture already accepts. It is harmless here - marking a file gone twice leaves
 * it gone once - but anything scheduled later that is <em>not</em> idempotent needs a lock first.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
