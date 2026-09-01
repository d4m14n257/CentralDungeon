package com.centraldungeon.tables;

import static org.assertj.core.api.Assertions.assertThat;

import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * approve() has no partial unique index to lean on either: two admins reviewing the same
 * Preparation table at once must not both succeed. This proves the findByIdForUpdate lock (the
 * same one MasterServiceIT and RegistrationServiceIT already rely on) actually serializes the
 * race against a real MySQL with Flyway's real migrations applied.
 *
 * Wired with @DynamicPropertySource, not @ServiceConnection: see RegistrationServiceIT for why
 * (open spring-boot-testcontainers regression under Boot 4.x).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class GameTableTransitionIT {

    @Container
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private GameTableService gameTableService;

    @Autowired
    private GameTableRepository gameTableRepository;

    @Autowired
    private UserRepository userRepository;

    private GameTable table;
    private List<User> admins;

    @BeforeEach
    void setUp() {
        User master = userRepository.save(new User(randomDiscordId(), "Master"));
        GameTable newTable = new GameTable("Concurrency Review Table", master);
        table = gameTableRepository.save(newTable);

        admins = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            admins.add(userRepository.save(new User(randomDiscordId(), "Admin " + i)));
        }
    }

    @Test
    void onlyOneOfManyConcurrentApprovalsForTheSameTableSucceeds() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(admins.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (User admin : admins) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    gameTableService.approve(table.getId(), admin.getId());
                    return true;
                } catch (ConflictException e) {
                    return false;
                }
            }));
        }
        start.countDown();
        pool.shutdown();
        boolean finished = pool.awaitTermination(30, TimeUnit.SECONDS);
        assertThat(finished).isTrue();

        long successCount = futures.stream().filter(this::succeeded).count();
        assertThat(successCount).isEqualTo(1);

        GameTable reloaded = gameTableRepository.findById(table.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(GameTableStatus.Opened);
    }

    private boolean succeeded(Future<Boolean> future) {
        try {
            return future.get();
        } catch (Exception e) {
            return false;
        }
    }

    /** discord_id is VARCHAR(32); a UUID with the dashes stripped fits exactly. */
    private static String randomDiscordId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
