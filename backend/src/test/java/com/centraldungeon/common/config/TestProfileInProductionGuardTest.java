package com.centraldungeon.common.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.centraldungeon.CentralDungeonApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.mock.env.MockEnvironment;

class TestProfileInProductionGuardTest {

    private final TestProfileInProductionGuard guard = new TestProfileInProductionGuard();

    /** The real start-up, registration included: it fails before reaching the database. */
    @Test
    void theApplicationDoesNotStartWithProdAndTestTogether() {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(CentralDungeonApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("prod", "test");

        assertThatThrownBy(builder::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'prod' and 'test'");
    }

    @Test
    void eitherProfileAloneIsAllowed() {
        assertThatCode(() -> guard.postProcessEnvironment(environmentWith("prod"), new SpringApplication())).doesNotThrowAnyException();
        assertThatCode(() -> guard.postProcessEnvironment(environmentWith("dev", "test"), new SpringApplication()))
                .doesNotThrowAnyException();
    }

    private static MockEnvironment environmentWith(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return environment;
    }
}
