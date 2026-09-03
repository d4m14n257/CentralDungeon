package com.centraldungeon.auth;

import com.centraldungeon.auth.dto.StubDiscordGuildResponse;
import com.centraldungeon.auth.dto.StubDiscordTokenResponse;
import com.centraldungeon.auth.dto.StubDiscordUserResponse;
import com.centraldungeon.common.config.DiscordProperties;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * A stand-in for Discord itself, so the OAuth2 login can be driven end to end from a browser while
 * there is no real Discord application registered (plan-desarrollo.md, E1). TestLoginController
 * skips the handshake entirely to get a session cheaply; this one runs the real handshake -
 * DiscordOAuth2UserService, the guild check, the success and failure handlers - against a fake provider.
 *
 * The "test" profile points the four Discord URIs here (application-test.yml), so nothing in the
 * production path changes shape. Registered only under that profile: in dev and prod these routes
 * have no handler and 404, regardless of the permitAll matcher in SecurityConfig.
 *
 * Who logs in is server state, set by POST /test-discord/next-login before the browser starts the
 * flow - the login link carries no parameters of its own. That makes the stub single-tenant: it
 * holds only while a single spec file drives it, since Playwright parallelizes across files but
 * not within one (playwright.config.ts fullyParallel: false). A second spec that logs in through
 * Discord needs the pending identity keyed per caller, not a shared slot.
 */
@RestController
@RequestMapping("/test-discord")
@Profile("test")
public class TestDiscordController {

    private static final StubIdentity DEFAULT_IDENTITY = new StubIdentity("stub-discord-user", "StubUser", true);

    private final AtomicReference<StubIdentity> nextLogin = new AtomicReference<>(DEFAULT_IDENTITY);
    private final Map<String, StubIdentity> identityByCode = new ConcurrentHashMap<>();
    private final Map<String, StubIdentity> identityByAccessToken = new ConcurrentHashMap<>();

    private final DiscordProperties discordProperties;

    public TestDiscordController(DiscordProperties discordProperties) {
        this.discordProperties = discordProperties;
    }

    /** Chooses the identity the next handshake will return, and whether it is in the guild. */
    @PostMapping("/next-login")
    public void nextLogin(
            @RequestParam String discordId,
            @RequestParam String username,
            @RequestParam(defaultValue = "true") boolean inGuild) {
        nextLogin.set(new StubIdentity(discordId, username, inGuild));
    }

    /** Discord's authorization endpoint: hands back a code and echoes the state, as the real one does. */
    @GetMapping("/oauth2/authorize")
    public void authorize(
            @RequestParam("redirect_uri") String redirectUri, @RequestParam String state, HttpServletResponse response)
            throws IOException {
        String code = UUID.randomUUID().toString();
        identityByCode.put(code, nextLogin.get());

        String location = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("code", code)
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
        response.sendRedirect(location);
    }

    /** Discord's token endpoint. Called server to server, so the code is single use, like the real one. */
    @PostMapping("/oauth2/token")
    public StubDiscordTokenResponse token(@RequestParam String code) {
        StubIdentity identity = identityByCode.remove(code);
        if (identity == null) {
            throw new IllegalStateException("Unknown or already redeemed authorization code: " + code);
        }

        String accessToken = "stub-access-token-" + UUID.randomUUID();
        identityByAccessToken.put(accessToken, identity);
        return new StubDiscordTokenResponse(accessToken, "Bearer", 3600, "identify guilds");
    }

    @GetMapping("/users/@me")
    public StubDiscordUserResponse currentUser(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        StubIdentity identity = identityOf(authorization);
        return new StubDiscordUserResponse(identity.discordId(), identity.username());
    }

    /** A non member still belongs to other servers - that is what makes the check a filter and not a null test. */
    @GetMapping("/users/@me/guilds")
    public List<StubDiscordGuildResponse> guilds(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        StubIdentity identity = identityOf(authorization);
        return identity.inGuild()
                ? List.of(new StubDiscordGuildResponse(discordProperties.guildId(), "CentralDungeon"))
                : List.of(new StubDiscordGuildResponse("some-other-guild", "Another server"));
    }

    private StubIdentity identityOf(String authorizationHeader) {
        String accessToken = authorizationHeader.replaceFirst("(?i)^Bearer ", "");
        StubIdentity identity = identityByAccessToken.get(accessToken);
        if (identity == null) {
            throw new IllegalStateException("Unknown access token: " + accessToken);
        }
        return identity;
    }

    private record StubIdentity(String discordId, String username, boolean inGuild) {
    }
}
