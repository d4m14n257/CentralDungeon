package com.centraldungeon.registrations;

import static org.assertj.core.api.Assertions.assertThat;

import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.registrations.dto.CreateRegistrationRequest;
import com.centraldungeon.tables.GameTable;
import com.centraldungeon.tables.GameTableRepository;
import com.centraldungeon.tables.GameTableStatus;
import com.centraldungeon.users.Role;
import com.centraldungeon.users.RoleRepository;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserRepository;
import com.centraldungeon.users.UserRole;
import com.centraldungeon.users.UserRoleRepository;
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
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * At most one active registration per pair (modelo-datos.md #28) is an invariant MySQL cannot
 * express (no partial unique index) - it only holds if RegistrationService's row lock on the
 * table actually serializes concurrent applications. This proves it under a real race, against a
 * real MySQL, with Flyway's real migrations applied.
 *
 * Wired with @DynamicPropertySource, not @ServiceConnection: the spring-boot-testcontainers
 * module has an open Spring Boot 4.x regression where its presence breaks unrelated constructor
 * injection elsewhere in the context (upstream spring-projects/spring-boot #41839, #46558,
 * #48234). The classic property-registration mechanism sidesteps that module entirely.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class RegistrationServiceIT {

    @Container
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private GameTableRepository gameTableRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private TableRegistrationRepository registrationRepository;

    private GameTable table;
    private User player;

    @BeforeEach
    void setUp() {
        Role playerRole = roleRepository.findByName("Player").orElseThrow();
        User master = userRepository.save(new User(randomDiscordId(), "Master"));
        player = userRepository.save(new User(randomDiscordId(), "Player"));
        userRoleRepository.save(new UserRole(player, playerRole));

        GameTable newTable = new GameTable("Concurrency Test Table", master);
        newTable.setStatus(GameTableStatus.Opened);
        table = gameTableRepository.save(newTable);
    }

    @Test
    void onlyOneOfManyConcurrentApplicationsForTheSamePairSucceeds() throws InterruptedException {
        int threadCount = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    registrationService.apply(table.getId(), player.getId(), new CreateRegistrationRequest(null));
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

        long activeInDb = registrationRepository.findByUser_Id(player.getId(), Pageable.unpaged()).stream()
                .filter(registration -> registration.getStatus() == TableRegistrationStatus.Candidate
                        || registration.getStatus() == TableRegistrationStatus.Player)
                .count();
        assertThat(activeInDb).isEqualTo(1);
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
