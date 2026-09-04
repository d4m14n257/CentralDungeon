package com.centraldungeon;

import com.centraldungeon.common.config.DiscordProperties;
import com.centraldungeon.common.config.JwtProperties;
import com.centraldungeon.common.config.StorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;

/**
 * The application's entry point.
 *
 * <p>{@code @EnableConfigurationProperties} binds the three records that carry external
 * configuration - Discord's guild, the JWT settings and where uploaded files live - and
 * {@code @EnableCaching} turns on the Caffeine cache that keeps the per-request authorization read
 * from being a query per call (#128).
 */
@SpringBootApplication
@EnableConfigurationProperties({DiscordProperties.class, JwtProperties.class, StorageProperties.class})
@EnableCaching
public class CentralDungeonApplication {

    /**
     * Boots the application.
     *
     * @param args the command line, passed through to Spring Boot - the active profile arrives here
     */
    public static void main(String[] args) {
        SpringApplication.run(CentralDungeonApplication.class, args);
    }
}
