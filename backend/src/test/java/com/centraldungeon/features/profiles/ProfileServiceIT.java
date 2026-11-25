package com.centraldungeon.features.profiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.centraldungeon.features.catalogs.CatalogStatus;
import com.centraldungeon.features.catalogs.GameSystem;
import com.centraldungeon.features.catalogs.SystemRepository;
import com.centraldungeon.features.catalogs.TableSystem;
import com.centraldungeon.features.catalogs.TableSystemRepository;
import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.features.profiles.dto.FinishedTableResponse;
import com.centraldungeon.features.registrations.TableRegistration;
import com.centraldungeon.features.registrations.TableRegistrationRepository;
import com.centraldungeon.features.registrations.TableRegistrationStatus;
import com.centraldungeon.features.tables.AttendanceStatus;
import com.centraldungeon.features.tables.GameTable;
import com.centraldungeon.features.tables.GameTableRepository;
import com.centraldungeon.features.tables.GameTableStatus;
import com.centraldungeon.features.tables.MasterService;
import com.centraldungeon.features.tables.SessionAttendance;
import com.centraldungeon.features.tables.SessionAttendanceRepository;
import com.centraldungeon.features.tables.TableSession;
import com.centraldungeon.features.tables.TableSessionRepository;
import com.centraldungeon.features.users.Role;
import com.centraldungeon.features.users.RoleRepository;
import com.centraldungeon.features.users.User;
import com.centraldungeon.features.users.UserRepository;
import com.centraldungeon.features.users.UserRole;
import com.centraldungeon.features.users.UserRoleRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
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
 * The two things a mocked repository has no opinion about, over real MySQL: the two-week window
 * against a real {@code closed_at} column, and the aggregate attendance summed across more
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

    @Autowired
    private SystemRepository systemRepository;

    @Autowired
    private TableSystemRepository tableSystemRepository;

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

    /** The attendance aggregate, summed across two tables in the same read - the risk a single-table mock cannot show. */
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

    /**
     * The list and the count behind it agree on real SQL: a {@code Player} on a {@code Finished} table, nothing
     * else; newest closing first; a vetoed viewer does not see the table they are vetoed on; and only
     * the accepted systems of each table are named - one taken out of service is not shown to the viewer.
     */
    @Test
    void finishedTablesAgreeWithTheCountAndHideWhatTheViewerIsVetoedOn() {
        User master = givenAPlayer("Master");
        User player = givenAPlayer("Player");
        User viewer = givenAPlayer("Viewer");
        GameSystem system = system(CatalogStatus.Accepted);
        GameSystem disabled = system(CatalogStatus.Disabled);

        GameTable older = givenATableRunBy(master, GameTableStatus.Finished, LocalDateTime.now().minusDays(5));
        GameTable newer = givenATableRunBy(master, GameTableStatus.Finished, LocalDateTime.now().minusDays(1));
        GameTable vetoed = givenATableRunBy(master, GameTableStatus.Finished, LocalDateTime.now().minusDays(3));
        GameTable canceled = givenATableRunBy(master, GameTableStatus.Canceled, LocalDateTime.now().minusDays(2));
        GameTable stillRunning = givenATableRunBy(master, GameTableStatus.InProgress, null);
        for (GameTable table : new GameTable[] {older, newer, vetoed, canceled, stillRunning}) {
            registrationRepository.save(playerRegistrationOf(table, player));
        }
        tableSystemRepository.save(new TableSystem(newer.getId(), system.getId()));
        tableSystemRepository.save(new TableSystem(newer.getId(), disabled.getId()));
        TableRegistration veto = new TableRegistration(vetoed, viewer, null);
        veto.setStatus(TableRegistrationStatus.Blocked);
        registrationRepository.save(veto);
        // The viewer plays at one of the tables, which is what makes the profile readable to them.
        registrationRepository.save(playerRegistrationOf(older, viewer));

        assertThat(profileService.getProfile(player.getId(), viewer.getId()).finishedTables()).isEqualTo(3);
        PageResponse<FinishedTableResponse> page =
                profileService.listFinishedTables(player.getId(), viewer.getId(), PageRequest.of(0, 10));

        assertThat(page.content()).extracting(FinishedTableResponse::id).containsExactly(newer.getId(), older.getId());
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.content().get(0).systems()).containsExactly(system.getName());
        assertThat(page.content().get(0).masters()).containsExactly(master.getDiscordUsername());
    }

    /** An actor who cannot read the profile cannot read the list either, over the real visibility SQL. */
    @Test
    void finishedTablesAreRefusedToWhoeverCannotReadTheProfile() {
        User player = givenAPlayer("Player");
        User stranger = givenAPlayer("Stranger");

        assertThatThrownBy(() -> profileService.listFinishedTables(player.getId(), stranger.getId(), PageRequest.of(0, 10)))
                .isInstanceOf(NotFoundException.class);
    }

    /**
     * Over the real queries: a vetoed person loses the master's profile through the table that vetoed
     * them, and the master keeps reading the profile of the person they vetoed.
     */
    @Test
    void aVetoClosesTheMastersProfileToTheVetoedAndLeavesTheVetoedOpenToTheMaster() {
        User master = givenAPlayer("Master");
        User vetoed = givenAPlayer("Vetoed");
        GameTable table = givenATableRunBy(master, GameTableStatus.Opened, null);
        TableRegistration veto = new TableRegistration(table, vetoed, null);
        veto.setStatus(TableRegistrationStatus.Blocked);
        registrationRepository.save(veto);

        assertThatThrownBy(() -> profileService.getProfile(master.getId(), vetoed.getId())).isInstanceOf(NotFoundException.class);
        assertThatCode(() -> profileService.getProfile(vetoed.getId(), master.getId())).doesNotThrowAnyException();

        givenATableRunBy(master, GameTableStatus.Opened, null);
        assertThatCode(() -> profileService.getProfile(master.getId(), vetoed.getId())).doesNotThrowAnyException();
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

    private GameSystem system(CatalogStatus status) {
        GameSystem system = new GameSystem("System " + UUID.randomUUID());
        system.setStatus(status);
        return systemRepository.save(system);
    }

    /** discord_id is VARCHAR(32); a UUID with the dashes stripped fits exactly. */
    private static String randomDiscordId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
