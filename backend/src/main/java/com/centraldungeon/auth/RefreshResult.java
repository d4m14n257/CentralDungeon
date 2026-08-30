package com.centraldungeon.auth;

record RefreshResult(String accessToken, String refreshToken, long expiresInSeconds) {
}
