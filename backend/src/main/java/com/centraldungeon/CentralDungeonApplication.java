package com.centraldungeon;

import com.centraldungeon.common.config.DiscordProperties;
import com.centraldungeon.common.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableConfigurationProperties({DiscordProperties.class, JwtProperties.class})
@EnableCaching
public class CentralDungeonApplication {

    public static void main(String[] args) {
        SpringApplication.run(CentralDungeonApplication.class, args);
    }
}
