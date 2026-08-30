package com.centraldungeon.common.security;

import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Shared by the OAuth2 success handler and AuthController - both set the same cookie. */
@Component
public class RefreshCookieFactory {

    public static final String COOKIE_NAME = "refresh_token";
    private static final String COOKIE_PATH = "/api/v1/auth";

    private final JwtService jwtService;

    @Value("${app.cookie.secure}")
    private boolean cookieSecure;

    public RefreshCookieFactory(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    public Cookie create(String refreshToken) {
        Cookie cookie = base();
        cookie.setValue(refreshToken);
        cookie.setMaxAge((int) jwtService.refreshTokenTtl().toSeconds());
        return cookie;
    }

    public Cookie expire() {
        Cookie cookie = base();
        cookie.setValue("");
        cookie.setMaxAge(0);
        return cookie;
    }

    private Cookie base() {
        Cookie cookie = new Cookie(COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath(COOKIE_PATH);
        cookie.setAttribute("SameSite", "Strict");
        return cookie;
    }
}
