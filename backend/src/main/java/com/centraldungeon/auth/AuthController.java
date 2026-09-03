package com.centraldungeon.auth;

import com.centraldungeon.auth.dto.TokenResponse;
import com.centraldungeon.common.security.RefreshCookieFactory;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The two endpoints that are about the session rather than about the domain: renewing an access
 * token and ending the session.
 *
 * <p>Neither carries a {@code @PreAuthorize}: {@code SecurityConfig} leaves both public, because
 * their whole point is being reachable when the access token is gone. They authenticate with the
 * refresh cookie instead - which is why refresh is the one path in the API that keeps CSRF
 * protection on (#127): a cookie travels on its own, a bearer header does not.
 *
 * <p>There is no login endpoint here. Logging in is Discord's OAuth2 flow, and it ends at
 * {@code OAuth2LoginSuccessHandler} (#38).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    /** Validates the refresh token and re-reads the person behind it. */
    private final AuthService authService;

    /** Builds the cookie, so its security attributes are written in exactly one place. */
    private final RefreshCookieFactory refreshCookieFactory;

    /**
     * @param authService          validates refresh tokens and issues new ones
     * @param refreshCookieFactory builds the refresh cookie and the one that clears it
     */
    public AuthController(AuthService authService, RefreshCookieFactory refreshCookieFactory) {
        this.authService = authService;
        this.refreshCookieFactory = refreshCookieFactory;
    }

    /**
     * Renews the access token, rotating the refresh token with it.
     *
     * <p>This is the point where the system asks again whether the person is still allowed in: status
     * and roles are re-read from the database, so somebody blocked stops being renewed (#122, #125).
     *
     * <p>The new refresh token goes back <b>only</b> in the cookie, never in the body - which is what
     * keeps it out of reach of any script (#125).
     *
     * @param refreshCookie the current refresh token, from the cookie. Null when the browser sent
     *                      none, which is answered as 401 like any other invalid token
     * @param response      the response the rotated cookie is written onto
     * @return 200 with a fresh access token and its lifetime. 401 if the refresh no longer holds
     */
    @PostMapping("/refresh")
    public TokenResponse refresh(
            @CookieValue(name = RefreshCookieFactory.COOKIE_NAME, required = false) @Nullable String refreshCookie,
            HttpServletResponse response) {
        RefreshResult result = authService.refresh(refreshCookie);
        response.addCookie(refreshCookieFactory.create(result.refreshToken()));
        return new TokenResponse(result.accessToken(), result.expiresInSeconds());
    }

    /**
     * Ends the session by clearing the refresh cookie. The access token is not revoked - it simply
     * expires, which is what its short lifetime is for (#125).
     *
     * @param response the response the expiring cookie is written onto
     * @return nothing - 204
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletResponse response) {
        response.addCookie(refreshCookieFactory.expire());
    }
}
