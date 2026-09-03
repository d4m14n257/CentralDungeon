package com.centraldungeon.health.dto;

/**
 * What {@code GET /health} answers. The frontend polls it to tell "the backend is down" apart from
 * "this one call failed".
 *
 * @param status a single word describing the service. "UP" when it is serving
 */
public record HealthResponse(String status) {
}
