package com.centraldungeon.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.centraldungeon.common.config.RateLimitProperties;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import org.junit.jupiter.api.Test;

/** The fixed window, with a clock the test moves by hand instead of waiting for it. */
class FixedWindowLimiterTest {

    /** Ten seconds into a minute-long window, so the next one opens fifty seconds later. */
    private Instant now = Instant.parse("2026-11-20T10:00:10Z");

    private final InstantSource clock = () -> now;

    private final FixedWindowLimiter limiter =
            new FixedWindowLimiter(new RateLimitProperties.Limit(3, Duration.ofMinutes(1)), clock);

    @Test
    void letsTheLimitThroughAndRefusesTheNextUntilTheWindowOpens() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("10.0.0.1")).isEmpty();
        }

        assertThat(limiter.tryAcquire("10.0.0.1")).contains(Duration.ofSeconds(50));
    }

    @Test
    void theRetryAfterShrinksAsTheWindowRunsOut() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("10.0.0.1");
        }
        now = now.plusMillis(49_500);

        // Rounded up: answering 0 would invite a retry that is still refused.
        assertThat(limiter.tryAcquire("10.0.0.1")).contains(Duration.ofSeconds(1));
    }

    @Test
    void aNewWindowStartsFromZero() {
        for (int i = 0; i < 4; i++) {
            limiter.tryAcquire("10.0.0.1");
        }
        now = now.plusSeconds(50);

        assertThat(limiter.tryAcquire("10.0.0.1")).isEmpty();
    }

    @Test
    void eachKeyHasItsOwnCounter() {
        for (int i = 0; i < 4; i++) {
            limiter.tryAcquire("user-a");
        }

        assertThat(limiter.tryAcquire("user-b")).isEmpty();
    }
}
