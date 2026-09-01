package com.centraldungeon.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.centraldungeon.common.config.DiscordProperties;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserService;
import com.centraldungeon.users.UserStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * The door to the whole platform (decisiones.md #38): guild membership decides, a blocked user
 * never gets a session, and the principal that comes out carries our internal user id - which is
 * what OAuth2LoginSuccessHandler signs the refresh token with.
 *
 * Discord's user-info call belongs to the delegate, so the delegate is stubbed here; the guild
 * call is ours, so it goes through a real RestClient against MockRestServiceServer.
 */
@ExtendWith(MockitoExtension.class)
class DiscordOAuth2UserServiceTest {

    private static final String GUILDS_URI = "https://discord.test/api/users/@me/guilds";
    private static final String OUR_GUILD_ID = "guild-central-dungeon";
    private static final String DISCORD_ACCESS_TOKEN = "discord-access-token";

    @Mock
    private UserService userService;

    private MockRestServiceServer discord;
    private DiscordOAuth2UserService service;
    private OAuth2User discordUser;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        discord = MockRestServiceServer.bindTo(restClientBuilder).build();
        service = new DiscordOAuth2UserService(
                userRequest -> discordUser,
                restClientBuilder,
                new DiscordProperties(OUR_GUILD_ID, "https://discord.gg/invite", GUILDS_URI),
                userService);
    }

    @Test
    void readsTheDiscordIdAndUsernameAsTheStringsDiscordSends() {
        discordUser = discordUser("1234567890", "Vecna");
        expectGuildsCall(guildsResponse(OUR_GUILD_ID));
        when(userService.findOrCreateByDiscordId("1234567890", "Vecna")).thenReturn(allowedUser("user-1"));

        OAuth2User principal = service.loadUser(userRequest());

        // getName() is the internal id, not the Discord one: the success handler signs the JWT with it.
        assertThat(principal.getName()).isEqualTo("user-1");
        assertThat(principal.<String>getAttribute("internalUserId")).isEqualTo("user-1");
        assertThat(principal.<String>getAttribute("id")).isEqualTo("1234567890");
        discord.verify();
    }

    @Test
    void rejectsSomeoneWhoIsNotInTheGuild() {
        discordUser = discordUser("999", "Randolph");
        expectGuildsCall(guildsResponse("some-other-guild"));

        assertThatThrownBy(() -> service.loadUser(userRequest()))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(error -> ((OAuth2AuthenticationException) error).getError().getErrorCode())
                // The exact code matters: OAuth2LoginFailureHandler branches on it to attach the invite url.
                .isEqualTo(DiscordOAuth2UserService.NOT_GUILD_MEMBER_ERROR);

        verify(userService, never()).findOrCreateByDiscordId(any(), any());
        discord.verify();
    }

    @Test
    void rejectsAGuildMemberWhoIsBlocked() {
        discordUser = discordUser("666", "Strahd");
        expectGuildsCall(guildsResponse(OUR_GUILD_ID));
        User blocked = allowedUser("user-2");
        blocked.setStatus(UserStatus.Blocked);
        when(userService.findOrCreateByDiscordId("666", "Strahd")).thenReturn(blocked);

        assertThatThrownBy(() -> service.loadUser(userRequest()))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(error -> ((OAuth2AuthenticationException) error).getError().getErrorCode())
                .isEqualTo(DiscordOAuth2UserService.USER_BLOCKED_ERROR);
    }

    @Test
    void treatsAnEmptyGuildListAsNotAMember() {
        discordUser = discordUser("777", "Acererak");
        expectGuildsCall("[]");

        assertThatThrownBy(() -> service.loadUser(userRequest())).isInstanceOf(OAuth2AuthenticationException.class);

        verify(userService, never()).findOrCreateByDiscordId(any(), any());
    }

    /** Every case asserts the same thing about the request: the Discord token travels as a bearer, once. */
    private void expectGuildsCall(String body) {
        discord.expect(requestTo(GUILDS_URI))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + DISCORD_ACCESS_TOKEN))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private static String guildsResponse(String guildId) {
        return "[{\"id\":\"" + guildId + "\",\"name\":\"Some server\"}]";
    }

    private static OAuth2User discordUser(String id, String username) {
        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("OAUTH2_USER")), Map.of("id", id, "username", username), "id");
    }

    private static User allowedUser(String id) {
        User user = new User("discord-" + id, "name-" + id);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static OAuth2UserRequest userRequest() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("discord")
                .clientId("client-id")
                .clientSecret("client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost:8080/login/oauth2/code/discord")
                .authorizationUri("https://discord.test/api/oauth2/authorize")
                .tokenUri("https://discord.test/api/oauth2/token")
                .userInfoUri("https://discord.test/api/users/@me")
                .userNameAttributeName("id")
                .build();

        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                DISCORD_ACCESS_TOKEN,
                Instant.now(),
                Instant.now().plusSeconds(3600));

        return new OAuth2UserRequest(registration, accessToken);
    }
}
