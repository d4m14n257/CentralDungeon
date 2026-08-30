package com.centraldungeon.common.config;

import com.centraldungeon.notifications.NotificationMapper;
import com.centraldungeon.registrations.RegistrationMapper;
import com.centraldungeon.tables.GameTableMapper;
import com.centraldungeon.users.UserMapper;
import org.mapstruct.factory.Mappers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Mappers are registered as explicit @Bean factory methods instead of MapStruct's
 * componentModel="spring" (the documented convention, arquitectura.md 2.2). Written while chasing
 * what looked like a Spring Boot 4.1.1 bug where the generated @Component failed to resolve for
 * constructor injection - the real cause (see backend/README.md) turned out to be VS Code's Java
 * language server racing Maven for target/classes, not the framework or this class's approach.
 * Left as-is since Mappers.getMapper(...) works and there is no reason to touch it now that the
 * actual trigger is understood.
 */
@Configuration
public class MapperConfig {

    @Bean
    public UserMapper userMapper() {
        return Mappers.getMapper(UserMapper.class);
    }

    @Bean
    public GameTableMapper gameTableMapper() {
        return Mappers.getMapper(GameTableMapper.class);
    }

    @Bean
    public RegistrationMapper registrationMapper() {
        return Mappers.getMapper(RegistrationMapper.class);
    }

    @Bean
    public NotificationMapper notificationMapper() {
        return Mappers.getMapper(NotificationMapper.class);
    }
}
