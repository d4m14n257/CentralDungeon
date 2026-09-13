package com.centraldungeon.tables;

import static org.assertj.core.api.Assertions.assertThat;

import com.centraldungeon.registrations.TableRegistration;
import com.centraldungeon.registrations.TableRegistrationRepository;
import com.centraldungeon.registrations.TableRegistrationStatus;
import com.centraldungeon.tables.dto.AttendanceSummaryResponse;
import com.centraldungeon.tables.dto.GameTableHistoryResponse;
import com.centraldungeon.users.Role;
import com.centraldungeon.users.RoleRepository;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserRepository;
import com.centraldungeon.users.UserRole;
import com.centraldungeon.users.UserRoleRepository;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * The player's history (#133a), over real MySQL: whether the two endings come back with their own,
 * different attendance, and whether resolving that attendance for a whole page still costs one
 * query rather than one per table - the exact risk a mocked {@code SessionAttendanceRepository}
 * cannot show, the same reason {@code ProfileServiceIT} exists for #137's other aggregate.
 *
 * <p>Wired with {@code @DynamicPropertySource}, not {@code @ServiceConnection} - see
 * {@code ProfileServiceIT} / {@code RegistrationServiceIT} for why.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class GameTableHistoryIT {

    @Container
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private GameTableService gameTableService;

    @Autowired
    private TableSessionService tableSessionService;

    @Autowired
    private GameTableRepository gameTableRepository;

    @Autowired
    private MasterService masterService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private TableRegistrationRepository registrationRepository;

    @Autowired
    private TableSessionRepository sessionRepository;

    @Autowired
    private SessionAttendanceRepository attendanceRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Role playerRole;

    @BeforeEach
    void setUp() {
        playerRole = roleRepository.findByName("Player").orElseThrow();
    }

    /**
     * Two closed tables, two different attendance records, and a third table that is still live:
     * the history has to keep the two endings apart from each other and from the one table that
     * has not closed at all.
     */
    @Test
    void historyCarriesEachTablesOwnDifferentAttendance() {
        User master = givenAPlayer("Master");
        User player = givenAPlayer("Player");

        GameTable finished = givenATableRunBy(master, GameTableStatus.Finished);
        registrationRepository.save(playerRegistrationOf(finished, player));
        TableSession finishedSessionA = sessionRepository.save(new TableSession(finished, 1, LocalDateTime.now().minusDays(10)));
        TableSession finishedSessionB = sessionRepository.save(new TableSession(finished, 2, LocalDateTime.now().minusDays(5)));
        attendanceRepository.save(new SessionAttendance(finishedSessionA, player, AttendanceStatus.Present));
        attendanceRepository.save(new SessionAttendance(finishedSessionB, player, AttendanceStatus.Absent));

        GameTable canceled = givenATableRunBy(master, GameTableStatus.Canceled);
        registrationRepository.save(playerRegistrationOf(canceled, player));
        TableSession canceledSession = sessionRepository.save(new TableSession(canceled, 1, LocalDateTime.now().minusDays(20)));
        attendanceRepository.save(new SessionAttendance(canceledSession, player, AttendanceStatus.Excused));

        // Still playing here - it must never leak into the history (#133a).
        GameTable stillLive = givenATableRunBy(master, GameTableStatus.InProgress);
        registrationRepository.save(playerRegistrationOf(stillLive, player));

        var page = gameTableService.listMineHistory(player.getId(), PageRequest.of(0, 20));

        assertThat(page.content()).extracting(GameTableHistoryResponse::id)
                .containsExactlyInAnyOrder(finished.getId(), canceled.getId());
        assertThat(historyOf(page.content(), finished.getId()).attendance())
                .isEqualTo(new AttendanceSummaryResponse(1, 0, 1, 2));
        assertThat(historyOf(page.content(), canceled.getId()).attendance())
                .isEqualTo(new AttendanceSummaryResponse(0, 1, 0, 1));
    }

    /**
     * The batched read behind the history (#133a): resolving two tables' attendance for one person
     * costs exactly one query against real MySQL, never one per table - the N+1
     * {@code CatalogUsageCount} and {@code FileService.usagesByFileId} already avoid for a page of
     * catalog values and a page of files.
     */
    @Test
    void resolvesAttendanceForAWholePageInOneQueryNotOnePerTable() {
        User master = givenAPlayer("Master");
        User player = givenAPlayer("Player");

        GameTable tableOne = givenATableRunBy(master, GameTableStatus.Finished);
        registrationRepository.save(playerRegistrationOf(tableOne, player));
        TableSession sessionOne = sessionRepository.save(new TableSession(tableOne, 1, LocalDateTime.now().minusDays(10)));
        attendanceRepository.save(new SessionAttendance(sessionOne, player, AttendanceStatus.Present));

        GameTable tableTwo = givenATableRunBy(master, GameTableStatus.Canceled);
        registrationRepository.save(playerRegistrationOf(tableTwo, player));
        TableSession sessionTwo = sessionRepository.save(new TableSession(tableTwo, 1, LocalDateTime.now().minusDays(3)));
        attendanceRepository.save(new SessionAttendance(sessionTwo, player, AttendanceStatus.Absent));

        Statistics statistics = sessionFactory().getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        var summaries = tableSessionService.summarizeByTables(List.of(tableOne.getId(), tableTwo.getId()), player.getId());

        // One grouped query for both tables - not two, one per table, which is exactly what a naive
        // per-row `summarize` call in a loop would have cost.
        assertThat(statistics.getQueryExecutionCount()).isEqualTo(1);
        assertThat(summaries.get(tableOne.getId())).isEqualTo(new AttendanceSummaryResponse(1, 0, 0, 1));
        assertThat(summaries.get(tableTwo.getId())).isEqualTo(new AttendanceSummaryResponse(0, 0, 1, 1));
    }

    private SessionFactory sessionFactory() {
        return entityManagerFactory.unwrap(SessionFactory.class);
    }

    private static GameTableHistoryResponse historyOf(List<GameTableHistoryResponse> content, String gameTableId) {
        return content.stream().filter(response -> response.id().equals(gameTableId)).findFirst().orElseThrow();
    }

    private GameTable givenATableRunBy(User master, GameTableStatus status) {
        GameTable table = gameTableRepository.save(new GameTable("Mesa " + UUID.randomUUID(), master));
        masterService.createPrimary(table, master);
        table.setStatus(status);
        if (status == GameTableStatus.Finished || status == GameTableStatus.Canceled) {
            table.setClosedAt(LocalDateTime.now());
        }
        return gameTableRepository.save(table);
    }

    private static TableRegistration playerRegistrationOf(GameTable table, User user) {
        TableRegistration registration = new TableRegistration(table, user, null);
        registration.setStatus(TableRegistrationStatus.Player);
        return registration;
    }

    private User givenAPlayer(String label) {
        User user = userRepository.save(new User(randomDiscordId(), label + "-" + UUID.randomUUID()));
        userRoleRepository.save(new UserRole(user, playerRole));
        return user;
    }

    /** discord_id is VARCHAR(32); a UUID with the dashes stripped fits exactly. */
    private static String randomDiscordId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
