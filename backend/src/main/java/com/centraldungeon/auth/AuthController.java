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

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookieFactory refreshCookieFactory;

    public AuthController(AuthService authService, RefreshCookieFactory refreshCookieFactory) {
        this.authService = authService;
        this.refreshCookieFactory = refreshCookieFactory;
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(
            @CookieValue(name = RefreshCookieFactory.COOKIE_NAME, required = false) @Nullable String refreshCookie,
            HttpServletResponse response) {
        RefreshResult result = authService.refresh(refreshCookie);
        response.addCookie(refreshCookieFactory.create(result.refreshToken()));
        return new TokenResponse(result.accessToken(), result.expiresInSeconds());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletResponse response) {
        response.addCookie(refreshCookieFactory.expire());
    }
}
