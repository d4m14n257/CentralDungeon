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

    /** Source of the invite URL. Configuration, never a constant in the code (#38). */
    private final DiscordProperties discordProperties;

    /** Where to send the browser back to; the callback screen turns the query string into a message. */
    @Value("${app.frontend.url}")
    private String frontendUrl;

    /**
     * @param discordProperties holds the guild id and the invite URL
     */
    public OAuth2LoginFailureHandler(DiscordProperties discordProperties) {
        this.discordProperties = discordProperties;
    }

    /**
     * Redirects to the frontend callback with the reason, and - only when the reason is "not a
     * member of the guild" - with the invite to join.
     *
     * @param request   the failed login callback
     * @param response  the response to redirect on
     * @param exception what failed. Only its error code travels, never its message
     * @throws IOException if the redirect cannot be written
     */
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

    /**
     * URL-encodes a query string value.
     *
     * @param value the raw value
     * @return the value, safe to concatenate into the redirect
     */
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
