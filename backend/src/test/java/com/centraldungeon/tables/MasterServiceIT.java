package com.centraldungeon.tables;

import static org.assertj.core.api.Assertions.assertThat;

import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.exception.ForbiddenActionException;
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
 * Exactly one live Primary per table (modelo-datos.md #73) - MySQL has no partial unique index for
 * it, so MasterService's row lock on the table's masters is what has to hold under a race. Two
 * shapes of that race are covered: concurrent hand-offs from the same original Primary, where only
 * the one that grabs the lock first should still find the actor as Primary; and hand-offs racing
 * removals, where the table must never come out with nobody in charge.
 *
 * Wired with @DynamicPropertySource, not @ServiceConnection: see RegistrationServiceIT for why
 * (spring-boot-testcontainers has an open Boot 4.x regression breaking unrelated bean injection).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class MasterServiceIT {

    @Container
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private MasterService masterService;

    @Autowired
    private GameTableRepository gameTableRepository;

    @Autowired
    private MasterRepository masterRepository;

    @Autowired
    private UserRepository userRepository;

    private GameTable table;
    private User originalPrimary;
    private List<User> candidates;

    @BeforeEach
    void setUp() {
        originalPrimary = userRepository.save(new User(randomDiscordId(), "Original Primary"));
        GameTable newTable = new GameTable("Concurrency Test Table", originalPrimary);
        table = gameTableRepository.save(newTable);
        masterService.createPrimary(table, originalPrimary);

        candidates = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            candidates.add(userRepository.save(new User(randomDiscordId(), "Candidate " + i)));
        }
    }

    @Test
    void onlyOneConcurrentHandoffFromTheSamePrimarySucceeds() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(candidates.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (User candidate : candidates) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    masterService.addOrPromote(table, originalPrimary.getId(), candidate.getId(), MasterType.Primary);
                    return true;
                } catch (ForbiddenActionException e) {
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

        long primaryCount = masterRepository.findByGameTable_Id(table.getId()).stream()
                .filter(master -> master.getMasterType() == MasterType.Primary)
                .count();
        assertThat(primaryCount).isEqualTo(1);
    }

    /**
     * The same invariant from the other side. Several threads try to hand Primary over and to remove
     * co-masters at the same time; whatever interleaving wins, the table must end with exactly one
     * live Primary and must never end with none - a table nobody is in charge of has nobody
     * authorized to run it.
     */
    @Test
    void concurrentPromotionsAndRemovalsNeverLeaveTheTableWithoutAPrimary() throws InterruptedException {
        for (User candidate : candidates) {
            masterService.addOrPromote(table, originalPrimary.getId(), candidate.getId(), MasterType.Secondary);
        }

        ExecutorService pool = Executors.newFixedThreadPool(candidates.size());
        CountDownLatch start = new CountDownLatch(1);

        for (int i = 0; i < candidates.size(); i++) {
            User candidate = candidates.get(i);
            boolean promote = i % 2 == 0;
            pool.submit(() -> {
                start.await();
                try {
                    if (promote) {
                        masterService.addOrPromote(table, originalPrimary.getId(), candidate.getId(), MasterType.Primary);
                    } else {
                        masterService.removeMaster(table, originalPrimary.getId(), candidate.getId());
                    }
                    return true;
                } catch (ForbiddenActionException | ConflictException e) {
                    return false;
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        List<Master> live = masterRepository.findByGameTable_IdAndStatus(table.getId(), MasterRowStatus.Created);
        assertThat(live).filteredOn(master -> master.getMasterType() == MasterType.Primary).hasSize(1);
    }

    /** Removed rows stay as a record (#175), and re-adding the same person revives that row. */
    @Test
    void reAddingARemovedCoMasterRevivesTheRowInsteadOfCreatingASecond() {
        User coMaster = candidates.getFirst();
        masterService.addOrPromote(table, originalPrimary.getId(), coMaster.getId(), MasterType.Secondary);
        masterService.removeMaster(table, originalPrimary.getId(), coMaster.getId());

        assertThat(masterService.isMasterOf(table.getId(), coMaster.getId())).isFalse();
        assertThat(masterRepository.findByGameTable_Id(table.getId())).hasSize(2);

        masterService.addOrPromote(table, originalPrimary.getId(), coMaster.getId(), MasterType.Secondary);

        assertThat(masterService.isMasterOf(table.getId(), coMaster.getId())).isTrue();
        assertThat(masterRepository.findByGameTable_Id(table.getId())).hasSize(2);
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
