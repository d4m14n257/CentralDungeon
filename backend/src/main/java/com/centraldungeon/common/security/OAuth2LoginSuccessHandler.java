package com.centraldungeon.common.security;

import com.centraldungeon.auth.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Opens the session, sets the refresh cookie and redirects to the frontend - never the access token in
 * the URL. The frontend's /auth/callback immediately calls POST /auth/refresh to get its first access
 * token in memory, using the cookie set here.
 */
@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    /** Opens the session whose first refresh token this handler hands over in a cookie. */
    private final AuthService authService;

    /** Builds the cookie, so its attributes live in exactly one place. */
    private final RefreshCookieFactory refreshCookieFactory;

    /** Where to send the browser once the handshake is done. */
    @Value("${app.frontend.url}")
    private String frontendUrl;

    /**
     * @param authService          opens the session and issues its first refresh token
     * @param refreshCookieFactory builds the cookie that carries it
     */
    public OAuth2LoginSuccessHandler(AuthService authService, RefreshCookieFactory refreshCookieFactory) {
        this.authService = authService;
        this.refreshCookieFactory = refreshCookieFactory;
    }

    /**
     * Ends the Discord handshake: sets the refresh cookie and redirects to the frontend callback.
     *
     * <p><b>No access token in the redirect.</b> The browser gets only the httpOnly cookie, and the
     * callback screen turns it into an access token by calling {@code /auth/refresh} - a token in a
     * URL would land in history, in logs and in the referrer.
     *
     * <p>The HTTP session that carried the handshake is invalidated here: past the callback nothing
     * about the login may stay on the server except the recorded refresh token.
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

        String refreshToken = authService.openSession(userId);
        response.addCookie(refreshCookieFactory.create(refreshToken));

        // The session only carried the handshake across the redirect to Discord; nothing may outlive it.
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        response.sendRedirect(frontendUrl + "/auth/callback");
    }
}
