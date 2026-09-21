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
 * {@code @EnableCaching} turns on the two Caffeine caches: the one that keeps the per-request
 * authorization read from being a query per call (#128), and the one F3.5 added so the editable
 * settings of #141 are not a table hit on every upload.
 *
 * <p>There were four records until F3.5. {@code AdminQueueProperties} is gone because its one value
 * moved to {@code system_settings}, which is what modelo-datos.md §5 said this slice would do: a
 * value the people running the platform adjust does not belong in a file only a deploy can change.
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
