package com.centraldungeon.common.security;

import com.centraldungeon.common.config.DiscordProperties;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserService;
import com.centraldungeon.users.UserStatus;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    public static final String NOT_GUILD_MEMBER_ERROR = "not_guild_member";
    public static final String USER_BLOCKED_ERROR = "user_blocked";

    private static final String GUILDS_URI = "https://discord.com/api/users/@me/guilds";
    private static final String INTERNAL_USER_ID_ATTRIBUTE = "internalUserId";

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final RestClient restClient;
    private final DiscordProperties discordProperties;
    private final UserService userService;

    public DiscordOAuth2UserService(
            RestClient.Builder restClientBuilder, DiscordProperties discordProperties, UserService userService) {
        this.restClient = restClientBuilder.build();
        this.discordProperties = discordProperties;
        this.userService = userService;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User discordUser = delegate.loadUser(userRequest);

        if (!isGuildMember(userRequest.getAccessToken().getTokenValue())) {
            throw new OAuth2AuthenticationException(new OAuth2Error(NOT_GUILD_MEMBER_ERROR), NOT_GUILD_MEMBER_ERROR);
        }

        String discordId = String.valueOf(discordUser.getAttribute("id"));
        String discordUsername = String.valueOf(discordUser.getAttribute("username"));
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
                .uri(GUILDS_URI)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + discordAccessToken)
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

        return guilds != null
                && guilds.stream().anyMatch(guild -> discordProperties.guildId().equals(String.valueOf(guild.get("id"))));
    }
}
