package com.centraldungeon.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.scheduling.config.Task;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.unit.DataSize;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * The production profile actually boots, and what only a real boot wires is wired.
 *
 * <p>Every other test runs under the development defaults, so the three periodic jobs, the servlet
 * container's upload cap and the start-up check that keeps that cap above the editable file-size
 * limit were proven only by calling their methods. Nothing showed that under {@code prod} the jobs are
 * registered with their schedule, that the cap binds from configuration, or that the application
 * starts at all with nothing but the values production has to supply. The secrets here are throwaway
 * values for the ten properties {@code prod} requires without a default.
 *
 * <p>Wired with {@code @DynamicPropertySource}, not {@code @ServiceConnection}: see
 * {@code RegistrationServiceIT} for why.
 */
@SpringBootTest
@ActiveProfiles("prod")
@Testcontainers
class ProductionWiringIT {

    @Container
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @DynamicPropertySource
    static void registerProductionProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.security.oauth2.client.registration.discord.client-id", () -> "prod-wiring-client");
        registry.add("spring.security.oauth2.client.registration.discord.client-secret", () -> "prod-wiring-secret");
        registry.add("discord.guild-id", () -> "prod-wiring-guild");
        registry.add("app.cors.allowed-origin", () -> "https://prod-wiring.invalid");
        registry.add("app.frontend.url", () -> "https://prod-wiring.invalid");
        registry.add("app.jwt.secret", () -> "prod-wiring-secret-that-is-at-least-32-bytes-long");
        registry.add("app.storage.root", () -> tempDirectory());
    }

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MultipartProperties multipartProperties;

    /** The three periodic jobs are registered with the schedule each one declares. */
    @Test
    void theThreePeriodicJobsAreRegisteredWithTheirSchedules() {
        // The runnable comes wrapped, and the wrapper reports the method as Class.method in toString.
        Map<String, Task> tasks = new HashMap<>();
        for (ScheduledTaskHolder holder : context.getBeansOfType(ScheduledTaskHolder.class).values()) {
            for (ScheduledTask scheduled : holder.getScheduledTasks()) {
                tasks.put(scheduled.getTask().getRunnable().toString(), scheduled.getTask());
            }
        }

        assertThat(job(tasks, "FileRetentionService.purgeUnusedFiles"))
                .isInstanceOfSatisfying(CronTask.class, cron -> assertThat(cron.getExpression()).isEqualTo("0 0 5 * * *"));
        assertThat(job(tasks, "ApprovalOrphanCheckService.checkForOrphanedReferences"))
                .isInstanceOfSatisfying(CronTask.class, cron -> assertThat(cron.getExpression()).isEqualTo("0 30 5 * * *"));
        assertThat(job(tasks, "AdminQueueClaimReleaseService.releaseTimedOutClaims"))
                .isInstanceOfSatisfying(FixedDelayTask.class, delay -> {
                    assertThat(delay.getIntervalDuration()).isEqualTo(Duration.ofSeconds(60));
                    assertThat(delay.getInitialDelayDuration()).isEqualTo(Duration.ofSeconds(30));
                });
    }

    /**
     * The container's upload cap binds from configuration. The context having started is the other
     * half: the settings service refuses to start when that cap is not above the editable limit.
     */
    @Test
    void theUploadCapBindsAndTheContextStartsWithItsGuard() {
        assertThat(multipartProperties.getMaxFileSize()).isEqualTo(DataSize.ofMegabytes(26));
        assertThat(multipartProperties.getMaxRequestSize()).isEqualTo(DataSize.ofMegabytes(32));
    }

    private static Task job(Map<String, Task> tasks, String method) {
        return tasks.entrySet().stream()
                .filter(entry -> entry.getKey().endsWith(method))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow(() -> new AssertionError(method + " is not scheduled; scheduled are " + tasks.keySet()));
    }

    private static String tempDirectory() {
        try {
            return Files.createTempDirectory("centraldungeon-prod-wiring").toString();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not create the storage root for the test", e);
        }
    }
}
