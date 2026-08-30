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

    private final JwtService jwtService;
    private final RefreshCookieFactory refreshCookieFactory;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    public OAuth2LoginSuccessHandler(JwtService jwtService, RefreshCookieFactory refreshCookieFactory) {
        this.jwtService = jwtService;
        this.refreshCookieFactory = refreshCookieFactory;
    }

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
