package com.centraldungeon.health;

import com.centraldungeon.health.dto.HealthResponse;
import org.springframework.stereotype.Service;

/**
 * Answers the liveness question behind {@code HealthController}. Trivial, and it exists anyway
 * because a controller never skips the service layer (regla dura 1).
 */
@Service
public class HealthService {

    /**
     * Reports that the process is serving.
     *
     * <p>It checks no dependency on purpose - not the database, not Discord. The question the
     * frontend is asking from /login is "is the backend answering", and a health endpoint that
     * fails because a dependency is slow answers a different, less useful one (#146).
     *
     * @return the status, always "UP" - if it were not, the call would not have returned
     */
    public HealthResponse checkStatus() {
        return new HealthResponse("UP");
    }
}
