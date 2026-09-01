package com.centraldungeon.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The OAuth2 endpoints themselves live under spring.security.oauth2.client.provider.discord;
 * guildsUri is the one Discord call we make by hand, and it is configurable for the same reason
 * they are: the e2e run points the whole set at the stubbed Discord of the "test" profile.
 */
@ConfigurationProperties(prefix = "discord")
public record DiscordProperties(String guildId, String inviteUrl, String guildsUri) {
}
