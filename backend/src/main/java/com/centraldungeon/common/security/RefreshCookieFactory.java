package com.centraldungeon.common.security;

import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Builds the refresh cookie. Shared by the OAuth2 success handler and AuthController - both set the
 * same cookie, and two places writing the same attributes by hand is how they drift apart.
 *
 * <p>The attributes are the security decision of #125, not preferences: {@code httpOnly} keeps the
 * refresh token out of reach of any script that gets injected through the rich text editor (#62),
 * and {@code SameSite=Strict} is what stops a third-party site from forcing a rotation - the attack
 * the CSRF token on {@code /auth/refresh} covers for agents that ignore the attribute (#127).
 */
@Component
public class RefreshCookieFactory {

    /** Name of the cookie, shared with whoever has to read or clear it. */
    public static final String COOKIE_NAME = "refresh_token";

    /** Scoped to the auth endpoints: no other call has any use for the cookie. */
    private static final String COOKIE_PATH = "/api/v1/auth";

    /** Source of the refresh TTL, so the cookie never outlives the token inside it. */
    private final JwtService jwtService;

    /** False in local development, where there is no TLS to mark the cookie against. */
    @Value("${app.cookie.secure}")
    private boolean cookieSecure;

    /**
     * @param jwtService used to read the configured refresh token lifetime
     */
    public RefreshCookieFactory(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    /**
     * Builds the cookie that carries a freshly issued refresh token.
     *
     * @param refreshToken the token to store
     * @return the cookie, with a max age matching the token's own lifetime
     */
    public Cookie create(String refreshToken) {
        Cookie cookie = base();
        cookie.setValue(refreshToken);
        cookie.setMaxAge((int) jwtService.refreshTokenTtl().toSeconds());
        return cookie;
    }

    /**
     * Builds the cookie that clears the current one, for logout.
     *
     * @return an empty cookie with max age 0, on the same name and path - the only way a browser
     *         drops the original
     */
    public Cookie expire() {
        Cookie cookie = base();
        cookie.setValue("");
        cookie.setMaxAge(0);
        return cookie;
    }

    /**
     * The attributes both flavours share. Name and path included, since a cookie is only replaced by
     * one that matches on both.
     *
     * @return an empty cookie with every security attribute already set
     */
    private Cookie base() {
        Cookie cookie = new Cookie(COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath(COOKIE_PATH);
        cookie.setAttribute("SameSite", "Strict");
        return cookie;
    }
}
