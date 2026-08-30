package com.centraldungeon.common.security;

import com.centraldungeon.common.config.DiscordProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * Not a member of the guild is not a dead end: the frontend callback screen offers the invite
 * and only cuts the login if the user does not join (decisiones.md #38).
 */
@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private final DiscordProperties discordProperties;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    public OAuth2LoginFailureHandler(DiscordProperties discordProperties) {
        this.discordProperties = discordProperties;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        String errorCode = exception instanceof OAuth2AuthenticationException oauth2Exception
                ? oauth2Exception.getError().getErrorCode()
                : "login_failed";

        StringBuilder redirectUrl = new StringBuilder(frontendUrl).append("/auth/callback?error=").append(encode(errorCode));
        if (DiscordOAuth2UserService.NOT_GUILD_MEMBER_ERROR.equals(errorCode)) {
            redirectUrl.append("&inviteUrl=").append(encode(discordProperties.inviteUrl()));
        }

        response.sendRedirect(redirectUrl.toString());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
