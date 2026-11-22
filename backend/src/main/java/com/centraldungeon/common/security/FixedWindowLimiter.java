package com.centraldungeon.common.security;

import com.centraldungeon.common.config.RateLimitProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.InstantSource;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Counts requests per key inside fixed windows, in this process's memory.
 *
 * <p>A counter lives under the key and the index of its window, so a new window simply starts a new
 * counter and the old one is left for Caffeine to expire. Each instance of the application counts on
 * its own.
 */
final class FixedWindowLimiter {

    /**
     * Bounds the memory a flood of distinct keys can take.
     *
     * <p>shortcut: past this many live keys Caffeine evicts some, and an evicted key starts again from
     * zero inside its window; move the counters to a shared store if that is ever seen in practice.
     */
    private static final long MAX_KEYS = 100_000;

    private final int requests;
    private final long windowMillis;
    private final InstantSource clock;
    private final Cache<String, AtomicInteger> counters;

    /**
     * @param limit how many requests per window, and the window
     * @param clock where "now" comes from; a fixed one in the tests
     */
    FixedWindowLimiter(RateLimitProperties.Limit limit, InstantSource clock) {
        this.requests = limit.requests();
        this.windowMillis = limit.window().toMillis();
        this.clock = clock;
        this.counters = Caffeine.newBuilder()
                .expireAfterWrite(limit.window())
                .maximumSize(MAX_KEYS)
                .build();
    }

    /**
     * Counts one request for the key.
     *
     * @param key who is asking: an IP or a user id
     * @return empty when the request fits in the current window; otherwise how long until the next
     *         window opens, never less than one second
     */
    Optional<Duration> tryAcquire(String key) {
        long now = clock.millis();
        long window = now / windowMillis;
        int count = counters.get(key + '@' + window, k -> new AtomicInteger()).incrementAndGet();
        if (count <= requests) {
            return Optional.empty();
        }
        long untilNextWindow = (window + 1) * windowMillis - now;
        return Optional.of(Duration.ofSeconds(Math.max(1, (untilNextWindow + 999) / 1000)));
    }
}
