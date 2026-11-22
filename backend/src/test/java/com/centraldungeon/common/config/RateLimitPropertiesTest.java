package com.centraldungeon.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/**
 * The limits as the configuration files really set them: the base ones everywhere, and out of reach
 * under the {@code test} profile, which the end-to-end runs use.
 */
class RateLimitPropertiesTest {

    @Test
    void theBaseLimitsAreTheDecidedOnes() throws IOException {
        RateLimitProperties limits = bind(false);

        assertThat(limits.login()).isEqualTo(new RateLimitProperties.Limit(10, Duration.ofMinutes(1)));
        assertThat(limits.refresh()).isEqualTo(new RateLimitProperties.Limit(30, Duration.ofMinutes(1)));
        assertThat(limits.upload()).isEqualTo(new RateLimitProperties.Limit(20, Duration.ofHours(1)));
        assertThat(limits.registration()).isEqualTo(new RateLimitProperties.Limit(10, Duration.ofHours(1)));
        assertThat(limits.catalogProposal()).isEqualTo(new RateLimitProperties.Limit(20, Duration.ofHours(1)));
        assertThat(limits.masterCandidates()).isEqualTo(new RateLimitProperties.Limit(30, Duration.ofMinutes(1)));
    }

    @Test
    void theTestProfileRaisesEveryLimit() throws IOException {
        RateLimitProperties limits = bind(true);

        assertThat(limits.login().requests()).isGreaterThanOrEqualTo(10_000);
        assertThat(limits.refresh().requests()).isGreaterThanOrEqualTo(10_000);
        assertThat(limits.upload().requests()).isGreaterThanOrEqualTo(10_000);
        assertThat(limits.registration().requests()).isGreaterThanOrEqualTo(10_000);
        assertThat(limits.catalogProposal().requests()).isGreaterThanOrEqualTo(10_000);
        assertThat(limits.masterCandidates().requests()).isGreaterThanOrEqualTo(10_000);
    }

    @Test
    void aLimitWithAZeroWindowIsRejectedAtStartup() {
        assertThat(catchThrowable(() -> bindWith("app.rate-limit.login.window", "PT0S")))
                .isInstanceOf(BindException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause().hasMessageContaining("window");
    }

    @Test
    void aLimitWithASubSecondWindowIsRejectedAtStartup() {
        assertThat(catchThrowable(() -> bindWith("app.rate-limit.refresh.window", "PT0.5S")))
                .isInstanceOf(BindException.class);
    }

    @Test
    void aLimitWithoutRequestsIsRejectedAtStartup() {
        assertThat(catchThrowable(() -> bindWith("app.rate-limit.upload.requests", "0")))
                .isInstanceOf(BindException.class)
                .rootCause().hasMessageContaining("requests");
    }

    @Test
    void theSmallestValidLimitIsAccepted() {
        assertThat(new RateLimitProperties.Limit(1, Duration.ofSeconds(1)).requests()).isEqualTo(1);
    }

    private static void bindWith(String key, String value) throws IOException {
        StandardEnvironment environment = environment(false);
        environment.getPropertySources().addFirst(new MapPropertySource("override", Map.of(key, value)));
        Binder.get(environment).bind("app.rate-limit", RateLimitProperties.class).get();
    }

    private static RateLimitProperties bind(boolean testProfile) throws IOException {
        return Binder.get(environment(testProfile)).bind("app.rate-limit", RateLimitProperties.class).get();
    }

    private static StandardEnvironment environment(boolean testProfile) throws IOException {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        StandardEnvironment environment = new StandardEnvironment();
        loader.load("base", new ClassPathResource("application.yml"))
                .forEach(source -> environment.getPropertySources().addLast(source));
        if (testProfile) {
            loader.load("test", new ClassPathResource("application-test.yml"))
                    .forEach(source -> environment.getPropertySources().addFirst(source));
        }
        return environment;
    }
}
