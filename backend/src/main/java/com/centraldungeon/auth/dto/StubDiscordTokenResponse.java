package com.centraldungeon.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Discord's token endpoint response, as Spring Security's token client expects to read it.
 * Only produced by the "test" profile stub (TestDiscordController).
 */
public record StubDiscordTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn,
        String scope) {
}
