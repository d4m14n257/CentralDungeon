package com.centraldungeon.tables;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.registrations.TableRegistration;
import com.centraldungeon.registrations.TableRegistrationRepository;
import com.centraldungeon.registrations.TableRegistrationStatus;
import com.centraldungeon.tables.dto.AttendanceEntryRequest;
import com.centraldungeon.tables.dto.ChangeTableStatusRequest;
import com.centraldungeon.tables.dto.RecordAttendanceRequest;
import com.centraldungeon.tables.dto.TableScheduleEntry;
import com.centraldungeon.tables.dto.TableSessionResponse;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserRepository;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
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
 * The half of the calendar that only a real database can answer.
 *
 * <p>Three things nothing proves without MySQL. {@code table_sessions} has a unique constraint on
 * {@code (game_table_id, sequence_number)}, and the replacement of #194 takes a fresh number from a
 * {@code max()} the unit tests only ever stub - here it is the real column. {@code session_attendance}
 * has a composite key built from two foreign keys, and recording somebody twice has to be an update
 * and not a duplicate-key failure. And the whole materialization runs through the lifecycle, from
 * approving a draft to reading its dates back.
 *
 * <p>Wired with {@code @DynamicPropertySource}, not {@code @ServiceConnection}: see
 * {@code RegistrationServiceIT} for why.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class TableSessionIT {

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
    private TableSessionService tableSessionService;

    @Autowired
    private TableScheduleService tableScheduleService;

    @Autowired
    private TableSessionRepository sessionRepository;

    @Autowired
    private GameTableRepository gameTableRepository;

    @Autowired
    private TableRegistrationRepository registrationRepository;

    @Autowired
    private MasterService masterService;

    @Autowired
    private UserRepository userRepository;

    private User master;

    /**
     * The reviewer. No platform role is granted: {@code hasAnyRole('ADMIN','OWNER')} lives on the
     * controller, and what these tests exercise is the service and the database underneath it.
     */
    private User admin;

    private GameTable table;

    @BeforeEach
    void setUp() {
        master = userRepository.save(new User(randomDiscordId(), "Session Master"));
        admin = userRepository.save(new User(randomDiscordId(), "Session Admin"));

        table = gameTableRepository.save(draft("Mesa de los martes"));
        masterService.createPrimary(table, master);
        tableScheduleService.replace(table, List.of(new TableScheduleEntry(Weekday.Tuesday, LocalTime.of(20, 0))), master.getId());
    }

    /** The whole point of #26 and #33: approving a draft is what turns an agenda into dates. */
    @Test
    void approvingATableMaterializesItsCalendar() {
        gameTableService.approve(table.getId(), admin.getId());

        assertThat(sessionRepository.findByGameTable_IdOrderBySequenceNumberAsc(table.getId()))
                .hasSize(4)
                .extracting(TableSession::getScheduledAt)
                .containsExactly(
                        LocalDateTime.parse("2026-09-08T20:00"),
                        LocalDateTime.parse("2026-09-15T20:00"),
                        LocalDateTime.parse("2026-09-22T20:00"),
                        LocalDateTime.parse("2026-09-29T20:00"));
    }

    /**
     * #194 against the real unique constraint: the cancelled row keeps its number and the
     * replacement takes the next one, so the two never collide on {@code (table, sequence_number)}.
     */
    @Test
    void cancellingASessionAppendsAReplacementWithoutCollidingOnTheUniqueKey() {
        gameTableService.approve(table.getId(), admin.getId());
        TableSession second = sessionRepository.findByGameTable_IdOrderBySequenceNumberAsc(table.getId()).get(1);

        List<TableSessionResponse> calendar = tableSessionService.cancel(second.getId(), master.getId());

        assertThat(calendar).hasSize(5);
        assertThat(calendar).extracting(TableSessionResponse::sequenceNumber).containsExactly(1, 2, 3, 4, 5);
        assertThat(calendar.get(1).status()).isEqualTo(TableSessionStatus.Cancelled);
        assertThat(calendar.getLast().scheduledAt()).isEqualTo(LocalDateTime.parse("2026-10-06T20:00"));
    }

    /** The composite key of session_attendance: recording the same person twice is an update. */
    @Test
    void recordingTheSameAttendanceTwiceUpdatesTheRowInsteadOfDuplicatingIt() {
        gameTableService.approve(table.getId(), admin.getId());
        User player = userRepository.save(new User(randomDiscordId(), "Session Player"));
        joinAsPlayer(player);
        TableSession first = sessionRepository.findByGameTable_IdOrderBySequenceNumberAsc(table.getId()).getFirst();

        tableSessionService.recordAttendance(
                first.getId(),
                new RecordAttendanceRequest(List.of(new AttendanceEntryRequest(player.getId(), AttendanceStatus.Absent))),
                master.getId());
        TableSessionResponse corrected = tableSessionService.recordAttendance(
                first.getId(),
                new RecordAttendanceRequest(List.of(new AttendanceEntryRequest(player.getId(), AttendanceStatus.Present))),
                master.getId());

        assertThat(corrected.attendance()).singleElement().satisfies(line -> {
            assertThat(line.userId()).isEqualTo(player.getId());
            assertThat(line.attendance()).isEqualTo(AttendanceStatus.Present);
        });
        assertThat(tableSessionService.summarize(table.getId(), player.getId()).present()).isEqualTo(1);
        assertThat(tableSessionService.summarize(table.getId(), player.getId()).registered()).isEqualTo(1);
    }

    /** A pause hides the pending dates and resuming re-lays them from that moment on (#32, #33). */
    @Test
    void pausingHidesThePendingSessionsAndResumingRelaysThem() {
        gameTableService.approve(table.getId(), admin.getId());
        gameTableService.start(table.getId(), master.getId());
        TableSession first = sessionRepository.findByGameTable_IdOrderBySequenceNumberAsc(table.getId()).getFirst();
        tableSessionService.hold(first.getId(), master.getId());

        gameTableService.pauseDirect(table.getId(), admin.getId(), new ChangeTableStatusRequest("Se enfermó"));
        assertThat(tableSessionService.listForTable(table.getId(), master.getId()))
                .extracting(TableSessionResponse::status)
                .containsExactly(TableSessionStatus.Held);

        gameTableService.resume(table.getId(), admin.getId());

        List<TableSessionResponse> calendar = tableSessionService.listForTable(table.getId(), master.getId());
        assertThat(calendar).hasSize(4);
        // The three pending ones now start from the first Tuesday at or after today, not from 2026.
        assertThat(calendar.get(1).scheduledAt()).isAfter(LocalDateTime.now());
        assertThat(calendar.getFirst().scheduledAt()).isEqualTo(LocalDateTime.parse("2026-09-08T20:00"));
    }

    /** #193: coming back is when the slot is claimed again, and a taken slot refuses the resume. */
    @Test
    void resumingIsRefusedWhenTheMasterTookOnAClashingTableDuringThePause() {
        gameTableService.approve(table.getId(), admin.getId());
        gameTableService.start(table.getId(), master.getId());
        gameTableService.pauseDirect(table.getId(), admin.getId(), new ChangeTableStatusRequest("Se enfermó"));

        // Legal precisely because a paused table does not reserve its slot (#32, #178).
        GameTable second = gameTableRepository.save(draft("Mesa que ocupó el martes"));
        masterService.createPrimary(second, master);
        tableScheduleService.replace(second, List.of(new TableScheduleEntry(Weekday.Tuesday, LocalTime.of(21, 0))), master.getId());

        assertThatThrownBy(() -> gameTableService.resume(table.getId(), admin.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Mesa que ocupó el martes");

        assertThat(gameTableRepository.findById(table.getId()).orElseThrow().getStatus()).isEqualTo(GameTableStatus.Pause);
    }

    /** #196: a draft with no agenda opens anyway - materializing is a consequence, not a gate. */
    @Test
    void approvingATableWithNoAgendaOpensItWithNoSessions() {
        GameTable bare = gameTableRepository.save(draft("Mesa sin agenda"));
        masterService.createPrimary(bare, master);

        gameTableService.approve(bare.getId(), admin.getId());

        assertThat(gameTableRepository.findById(bare.getId()).orElseThrow().getStatus()).isEqualTo(GameTableStatus.Opened);
        assertThat(sessionRepository.findByGameTable_IdOrderBySequenceNumberAsc(bare.getId())).isEmpty();
    }

    private GameTable draft(String name) {
        GameTable draft = new GameTable(name, master);
        draft.setDuration(LocalTime.of(3, 0));
        draft.setStartDate(LocalDateTime.parse("2026-09-08T20:00"));
        draft.setTotalSessions(4);
        return draft;
    }

    private void joinAsPlayer(User player) {
        TableRegistration registration = new TableRegistration(table, player, null);
        registration.setStatus(TableRegistrationStatus.Player);
        registrationRepository.save(registration);
    }

    /** discord_id is VARCHAR(32); a UUID with the dashes stripped fits exactly. */
    private static String randomDiscordId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
