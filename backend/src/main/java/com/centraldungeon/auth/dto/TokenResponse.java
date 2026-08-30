package com.centraldungeon.auth.dto;

public record TokenResponse(String accessToken, long expiresInSeconds) {
}
