package com.centraldungeon.health;

import com.centraldungeon.health.dto.HealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Whether the backend is answering at all.
 *
 * <p>Public on purpose: the frontend calls it from /login, before there is any session, to decide
 * whether offering the Discord button is worth it (decisiones.md #146).
 *
 * <p>It measures no dependency - not the database, not Discord. The question behind "is the backend
 * online?" is whether the process is up and can answer HTTP, and a health check that fails because
 * something downstream is slow answers a different, less useful one.
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    /** The only collaborator: a controller never reaches a repository (regla dura 1). */
    private final HealthService healthService;

    /**
     * @param healthService answers whether the process is serving
     */
    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    /**
     * Whether the backend is up. No {@code @PreAuthorize}: this is one of the few paths
     * {@code SecurityConfig} leaves public, because the caller has no session yet by definition.
     *
     * @return 200 with the status. Reaching it at all is most of the answer
     */
    @GetMapping
    public HealthResponse check() {
        return healthService.checkStatus();
    }
}
