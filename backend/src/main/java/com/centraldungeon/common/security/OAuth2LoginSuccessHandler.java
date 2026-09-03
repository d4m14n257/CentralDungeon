package com.centraldungeon.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Sets the refresh cookie and redirects to the frontend - never the access token in the URL.
 * The frontend's /auth/callback immediately calls POST /auth/refresh to get its first access
 * token in memory, using the cookie set here (decisiones.md #125).
 */
@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    /** Issues the refresh token this handler hands over in a cookie. */
    private final JwtService jwtService;

    /** Builds the cookie, so its attributes live in exactly one place. */
    private final RefreshCookieFactory refreshCookieFactory;

    /** Where to send the browser once the handshake is done. */
    @Value("${app.frontend.url}")
    private String frontendUrl;

    /**
     * @param jwtService           issues the refresh token
     * @param refreshCookieFactory builds the cookie that carries it
     */
    public OAuth2LoginSuccessHandler(JwtService jwtService, RefreshCookieFactory refreshCookieFactory) {
        this.jwtService = jwtService;
        this.refreshCookieFactory = refreshCookieFactory;
    }

    /**
     * Ends the Discord handshake: sets the refresh cookie and redirects to the frontend callback.
     *
     * <p><b>No access token in the redirect.</b> The browser gets only the httpOnly cookie, and the
     * callback screen turns it into an access token by calling {@code /auth/refresh} - a token in a
     * URL would land in history, in logs and in the referrer (#125).
     *
     * @param request        the completed callback
     * @param response       the response the cookie and the redirect are written onto
     * @param authentication the authenticated principal, whose name is the local user id
     * @throws IOException if the redirect cannot be written
     */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        OAuth2User principal = (OAuth2User) authentication.getPrincipal();
        String userId = principal.getName();

        String refreshToken = jwtService.issueRefreshToken(userId);
        response.addCookie(refreshCookieFactory.create(refreshToken));

        response.sendRedirect(frontendUrl + "/auth/callback");
    }
}
