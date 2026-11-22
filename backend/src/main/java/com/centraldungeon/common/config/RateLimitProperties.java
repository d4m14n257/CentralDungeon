package com.centraldungeon.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How often the doors anybody can knock on repeatedly may be used, bound from {@code app.rate-limit.*}.
 *
 * <p>Operational configuration, not a community rule: it lives in the application's configuration
 * files, never in the editable settings, and the {@code test} profile raises every limit so the
 * end-to-end runs never trip over them.
 *
 * @param login        the Discord login and its callback together, per client IP
 * @param refresh      {@code POST /api/v1/auth/refresh}, per client IP
 * @param upload       every file upload, per authenticated user
 * @param registration applying to a table, per authenticated user
 * @param catalogProposal proposing a value for any of the three catalogs (tags, systems, platforms),
 *                     per authenticated user, one counter shared by the three
 * @param masterCandidates the co-master search of a table, per authenticated user
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        Limit login, Limit refresh, Limit upload, Limit registration, Limit catalogProposal, Limit masterCandidates) {

    /**
     * One limit: how many requests a key may make inside one fixed window.
     *
     * @param requests how many requests are let through per window; the next one is refused
     * @param window   the length of the window. Windows are aligned to the epoch, so every key's window
     *                 opens at the same instant
     * @throws IllegalArgumentException if {@code requests} is below one or {@code window} is missing or
     *                                  shorter than a second: a zero window divides by zero on every
     *                                  request and a zero allowance refuses everybody
     */
    public record Limit(int requests, Duration window) {

        /** Rejects at startup a limit that would break every request or refuse every one. */
        public Limit {
            if (requests < 1) {
                throw new IllegalArgumentException("A rate limit needs requests >= 1, got " + requests);
            }
            if (window == null || window.compareTo(Duration.ofSeconds(1)) < 0) {
                throw new IllegalArgumentException("A rate limit needs a window of at least one second, got " + window);
            }
        }
    }
}
