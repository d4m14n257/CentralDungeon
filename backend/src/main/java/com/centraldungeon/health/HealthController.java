package com.centraldungeon.health;

import com.centraldungeon.health.dto.HealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Público a propósito: el frontend lo consulta desde /login, antes de tener sesión, para saber
 * si vale la pena ofrecer el botón de Discord (docs/decisiones.md #146). No mide dependencias
 * (DB, etc.) - solo si el proceso está arriba y puede responder HTTP, que es la pregunta real
 * detrás de "¿está online el backend?".
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping
    public HealthResponse check() {
        return healthService.checkStatus();
    }
}
