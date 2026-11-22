package com.centraldungeon.common.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

/**
 * Refuses to start the application with the {@code prod} and {@code test} profiles both active.
 *
 * <p>The {@code test} profile registers the doubles the end-to-end runs drive: a login that hands out a
 * session for any user and any roles without Discord, and endpoints that create and wipe data. Under
 * {@code prod} that would be a way in for anybody. The check runs as soon as the profiles are known,
 * before any bean exists, so the application never connects to the database nor serves a request.
 */
public class TestProfileInProductionGuard implements EnvironmentPostProcessor {

    /**
     * Aborts the start-up when both profiles are active.
     *
     * @param environment the environment, with its profiles already resolved
     * @param application the application being started
     * @throws IllegalStateException when {@code prod} and {@code test} are both active
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.acceptsProfiles(Profiles.of("prod & test"))) {
            throw new IllegalStateException("The 'prod' and 'test' profiles cannot be active together: "
                    + "'test' registers a login that bypasses Discord");
        }
    }
}
