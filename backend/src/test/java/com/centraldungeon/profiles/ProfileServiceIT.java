package com.centraldungeon.profiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.registrations.TableRegistration;
import com.centraldungeon.registrations.TableRegistrationRepository;
import com.centraldungeon.registrations.TableRegistrationStatus;
import com.centraldungeon.tables.AttendanceStatus;
import com.centraldungeon.tables.GameTable;
import com.centraldungeon.tables.GameTableRepository;
import com.centraldungeon.tables.GameTableStatus;
import com.centraldungeon.tables.MasterService;
import com.centraldungeon.tables.SessionAttendance;
import com.centraldungeon.tables.SessionAttendanceRepository;
import com.centraldungeon.tables.TableSession;
import com.centraldungeon.tables.TableSessionRepository;
import com.centraldungeon.users.Role;
import com.centraldungeon.users.RoleRepository;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserRepository;
import com.centraldungeon.users.UserRole;
import com.centraldungeon.users.UserRoleRepository;
import java.time.LocalDateTime;
import java.util.UUID;
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
 * The two things a mocked repository has no opinion about, over real MySQL: the two-week window of
 * #44 against a real {@code closed_at} column, and the aggregate attendance of #137 summed across more
 * than one table.
 *
 * <p>Wired with {@code @DynamicPropertySource}, not {@code @ServiceConnection} - see
 * {@code RegistrationServiceIT} for why.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class ProfileServiceIT {

    @Container
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private ProfileService profileService;

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

    private Role playerRole;

    @BeforeEach
    void setUp() {
        playerRole = roleRepository.findByName("Player").orElseThrow();
    }

    /** The window's own boundary, over a real {@code closed_at}: fifteen days is out, ten is still in. */
    @Test
    void theTwoWeekWindowOfClosedAtIsEnforcedByRealSql() {
        User master = givenAPlayer("Master");
        User onlooker = givenAPlayer("Onlooker");

        GameTable longClosed = givenATableRunBy(master, GameTableStatus.Finished, LocalDateTime.now().minusDays(15));
        assertThatThrownBy(() -> profileService.getProfile(master.getId(), onlooker.getId())).isInstanceOf(NotFoundException.class);

        GameTable recentlyClosed = givenATableRunBy(master, GameTableStatus.Finished, LocalDateTime.now().minusDays(10));
        assertThatCode(() -> profileService.getProfile(master.getId(), onlooker.getId())).doesNotThrowAnyException();

        // Both tables exist only to prove the sequencing above; nothing further to assert on them.
        assertThat(longClosed.getId()).isNotEqualTo(recentlyClosed.getId());
    }

    /** #137's aggregate, summed across two tables in the same read - the risk a single-table mock cannot show. */
    @Test
    void attendanceAggregatesAcrossSeveralTablesAtOnce() {
        User master = givenAPlayer("Master");
        User player = givenAPlayer("Player");

        GameTable tableOne = givenATableRunBy(master, GameTableStatus.InProgress, null);
        registrationRepository.save(playerRegistrationOf(tableOne, player));
        TableSession sessionOneA = sessionRepository.save(new TableSession(tableOne, 1, LocalDateTime.now().minusDays(20)));
        TableSession sessionOneB = sessionRepository.save(new TableSession(tableOne, 2, LocalDateTime.now().minusDays(13)));
        attendanceRepository.save(new SessionAttendance(sessionOneA, player, AttendanceStatus.Present));
        attendanceRepository.save(new SessionAttendance(sessionOneB, player, AttendanceStatus.Absent));

        GameTable tableTwo = givenATableRunBy(master, GameTableStatus.InProgress, null);
        registrationRepository.save(playerRegistrationOf(tableTwo, player));
        TableSession sessionTwoA = sessionRepository.save(new TableSession(tableTwo, 1, LocalDateTime.now().minusDays(6)));
        TableSession sessionTwoB = sessionRepository.save(new TableSession(tableTwo, 2, LocalDateTime.now()));
        attendanceRepository.save(new SessionAttendance(sessionTwoA, player, AttendanceStatus.Present));
        // sessionTwoB is left without a row: Unknown, and it must stay out of the denominator.

        var profile = profileService.getProfile(player.getId(), player.getId());

        assertThat(profile.attendance().present()).isEqualTo(2);
        assertThat(profile.attendance().absent()).isEqualTo(1);
        assertThat(profile.attendance().excused()).isEqualTo(0);
        assertThat(profile.attendance().registered()).isEqualTo(3);
    }

    private GameTable givenATableRunBy(User master, GameTableStatus status, LocalDateTime closedAt) {
        GameTable table = gameTableRepository.save(new GameTable("Mesa " + UUID.randomUUID(), master));
        masterService.createPrimary(table, master);
        table.setStatus(status);
        table.setClosedAt(closedAt);
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
