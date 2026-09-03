package com.centraldungeon.common.security;

import com.centraldungeon.common.config.DiscordProperties;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserService;
import com.centraldungeon.users.UserStatus;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * There is no registration of our own: Discord guild membership is the door (decisiones.md #38).
 * The Discord access token is used once, right here, to check membership - it is never persisted.
 */
@Service
public class DiscordOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    /**
     * The login was refused because the person is not in the guild. Not a dead end: the failure
     * handler passes the invite along so they can join and try again (#38).
     */
    public static final String NOT_GUILD_MEMBER_ERROR = "not_guild_member";

    /** The login was refused because the account is blocked (#84, #86). This one <em>is</em> a dead end. */
    public static final String USER_BLOCKED_ERROR = "user_blocked";

    private static final String INTERNAL_USER_ID_ATTRIBUTE = "internalUserId";

    /** Spring's own user-info call to Discord. Kept as a field so the unit test can stand in for it. */
    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;

    /** Calls Discord's guilds endpoint. {@code RestClient}, because Boot 4 no longer autoconfigures RestTemplate. */
    private final RestClient restClient;

    /** Holds the guild id and the guilds URI - configuration, never constants in the code (#38). */
    private final DiscordProperties discordProperties;

    /** Creates the local user on first login, or refreshes their handle on every later one. */
    private final UserService userService;

    /**
     * @param restClientBuilder  builder for the client that calls Discord's guilds endpoint
     * @param discordProperties  the guild to check membership against
     * @param userService        creates or refreshes the local user
     */
    @Autowired
    public DiscordOAuth2UserService(
            RestClient.Builder restClientBuilder, DiscordProperties discordProperties, UserService userService) {
        this(new DefaultOAuth2UserService(), restClientBuilder, discordProperties, userService);
    }

    /**
     * Seam for the unit test: it stands in for the delegate's HTTP call to Discord's user-info
     * endpoint.
     *
     * @param delegate           the user-info call to fake out
     * @param restClientBuilder  builder for the guilds client
     * @param discordProperties  the guild to check membership against
     * @param userService        creates or refreshes the local user
     */
    DiscordOAuth2UserService(
            OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate,
            RestClient.Builder restClientBuilder,
            DiscordProperties discordProperties,
            UserService userService) {
        this.delegate = delegate;
        this.restClient = restClientBuilder.build();
        this.discordProperties = discordProperties;
        this.userService = userService;
    }

    /**
     * The gate of the whole system: Discord says who this is, and this method decides whether they
     * get in at all.
     *
     * <p>Three steps, in order - read the Discord profile, refuse anyone outside the guild (#38), and
     * create or refresh the local user. The Discord access token is used once here to check
     * membership and then discarded: keeping it would mean maintaining a second refresh cycle for a
     * capability v1 does not have (#125).
     *
     * @param userRequest the completed OAuth2 exchange, carrying Discord's access token
     * @return the principal, whose name is the <b>local</b> user id - which is what ends up as the
     *         JWT's subject
     * @throws OAuth2AuthenticationException with {@link #NOT_GUILD_MEMBER_ERROR} when the person is
     *         not in the guild, or {@link #USER_BLOCKED_ERROR} when the account is blocked
     */
    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User discordUser = delegate.loadUser(userRequest);

        if (!isGuildMember(userRequest.getAccessToken().getTokenValue())) {
            throw new OAuth2AuthenticationException(new OAuth2Error(NOT_GUILD_MEMBER_ERROR), NOT_GUILD_MEMBER_ERROR);
        }

        // Discord sends both as JSON strings; read them as such. Do not wrap getAttribute in
        // String.valueOf: the compiler resolves the char[] overload and infers A = char[].
        String discordId = discordUser.getAttribute("id");
        String discordUsername = discordUser.getAttribute("username");
        User user = userService.findOrCreateByDiscordId(discordId, discordUsername);

        if (user.getStatus() != UserStatus.Allowed) {
            throw new OAuth2AuthenticationException(new OAuth2Error(USER_BLOCKED_ERROR), USER_BLOCKED_ERROR);
        }

        Map<String, Object> attributes = new HashMap<>(discordUser.getAttributes());
        attributes.put(INTERNAL_USER_ID_ATTRIBUTE, user.getId());

        return new DefaultOAuth2User(discordUser.getAuthorities(), attributes, INTERNAL_USER_ID_ATTRIBUTE);
    }

    private boolean isGuildMember(String discordAccessToken) {
        List<Map<String, Object>> guilds = restClient
                .get()
                .uri(discordProperties.guildsUri())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + discordAccessToken)
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

        return guilds != null
                && guilds.stream().anyMatch(guild -> discordProperties.guildId().equals(String.valueOf(guild.get("id"))));
    }
}
