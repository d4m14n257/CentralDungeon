package com.centraldungeon.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Discord's access token answers two questions during the login - who this is, and whether they are in
 * the guild - and must not outlive the callback. Kept, it would be a live credential to the person's
 * Discord account sitting in the server's memory for as long as the process runs.
 *
 * <p>The whole handshake runs for real, over HTTP, against the stub provider the test profile serves:
 * the token exchange is a server-to-server call, so it needs a listening port and cannot be faked by
 * MockMvc.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("test")
@Testcontainers
class DiscordTokenIsDiscardedIT {

    private static final int PORT = freePort();
    private static final String BASE = "http://localhost:" + PORT;

    @Container
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("server.port", () -> PORT);
        registry.add("spring.security.oauth2.client.provider.discord.authorization-uri", () -> BASE + "/test-discord/oauth2/authorize");
        registry.add("spring.security.oauth2.client.provider.discord.token-uri", () -> BASE + "/test-discord/oauth2/token");
        registry.add("spring.security.oauth2.client.provider.discord.user-info-uri", () -> BASE + "/test-discord/users/@me");
        registry.add("discord.guilds-uri", () -> BASE + "/test-discord/users/@me/guilds");
    }

    @Autowired
    private OAuth2AuthorizedClientService authorizedClientService;

    @Autowired
    private JwtService jwtService;

    /** After a successful callback nothing on the server still holds Discord's token for that person. */
    @Test
    void theDiscordTokenDoesNotSurviveTheCallback() throws Exception {
        CookieManager cookies = new CookieManager();
        HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).followRedirects(HttpClient.Redirect.NEVER).build();
        String discordId = "e2e-disc-" + UUID.randomUUID().toString().substring(0, 12);
        send(client, HttpRequest.newBuilder(URI.create(BASE + "/test-discord/next-login?discordId=" + discordId + "&username=Acererak"))
                .POST(HttpRequest.BodyPublishers.noBody()));

        HttpResponse<String> callback = followToCallback(client);

        assertThat(callback.statusCode()).isEqualTo(302);
        assertThat(callback.headers().firstValue("Location")).hasValueSatisfying(location -> assertThat(location).endsWith("/auth/callback"));
        String refresh = cookies.getCookieStore().getCookies().stream()
                .filter(cookie -> cookie.getName().equals(RefreshCookieFactory.COOKIE_NAME))
                .map(HttpCookie::getValue)
                .findFirst()
                .orElseThrow();
        String userId = jwtService.verifyAndGetSubject(refresh, TokenType.REFRESH);

        assertThat((Object) authorizedClientService.loadAuthorizedClient("discord", userId))
                .as("the authorized client, which carries Discord's access token")
                .isNull();
    }

    /** Login link, stub authorize, callback: each hop a redirect, the session cookie carried along. */
    private HttpResponse<String> followToCallback(HttpClient client) throws IOException, InterruptedException {
        HttpResponse<String> response = send(client, HttpRequest.newBuilder(URI.create(BASE + "/oauth2/authorization/discord")).GET());
        for (int hop = 0; hop < 2; hop++) {
            assertThat(response.statusCode()).as("hop %d redirects", hop).isEqualTo(302);
            URI next = URI.create(response.headers().firstValue("Location").orElseThrow());
            response = send(client, HttpRequest.newBuilder(next).GET());
        }
        return response;
    }

    private static HttpResponse<String> send(HttpClient client, HttpRequest.Builder request) throws IOException, InterruptedException {
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("No free port for the test server", e);
        }
    }
}
