package com.centraldungeon.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.centraldungeon.auth.AuthService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

class OAuth2LoginSuccessHandlerTest {

    private final AuthService authService = mock(AuthService.class);
    private final RefreshCookieFactory refreshCookieFactory = mock(RefreshCookieFactory.class);
    private final OAuth2LoginSuccessHandler handler = new OAuth2LoginSuccessHandler(authService, refreshCookieFactory);

    /** The session only existed to carry the handshake; once it is over nothing may be left in it. */
    @Test
    void endsTheHandshakeSessionAndHandsOverANewSession() throws Exception {
        ReflectionTestUtils.setField(handler, "frontendUrl", "http://front.example");
        when(authService.openSession("user-1")).thenReturn("refresh-1");
        when(refreshCookieFactory.create("refresh-1")).thenReturn(new Cookie(RefreshCookieFactory.COOKIE_NAME, "refresh-1"));
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();
        DefaultOAuth2User principal = new DefaultOAuth2User(List.of(), Map.of("internalUserId", "user-1"), "internalUserId");

        handler.onAuthenticationSuccess(request, response, new TestingAuthenticationToken(principal, null));

        assertThat(session.isInvalid()).isTrue();
        assertThat(response.getCookie(RefreshCookieFactory.COOKIE_NAME)).extracting(Cookie::getValue).isEqualTo("refresh-1");
        assertThat(response.getRedirectedUrl()).isEqualTo("http://front.example/auth/callback");
    }
}
