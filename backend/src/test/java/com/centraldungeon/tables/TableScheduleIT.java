package com.centraldungeon.tables;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.exception.InvalidRequestException;
import com.centraldungeon.tables.dto.TableScheduleEntry;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserRepository;
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
 * The half of #178 that only a real database can answer.
 *
 * <p>{@code table_schedules} has a composite primary key made of a table id, an <b>enum stored as
 * text</b> and a {@code TIME} column. Every unit test in {@code ScheduleConflictServiceTest} works
 * on that key as Java objects; what nothing proves without MySQL is that a slot survives the round
 * trip - that {@code Weekday.Tuesday} comes back as {@code Tuesday} and not as an ordinal, that
 * 20:00 comes back without seconds, and that removing a slot and putting it back updates the row
 * instead of colliding with the key it never dropped.
 *
 * <p>Wired with {@code @DynamicPropertySource}, not {@code @ServiceConnection}: see
 * {@code RegistrationServiceIT} for why.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class TableScheduleIT {

    @Container
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private TableScheduleService tableScheduleService;

    @Autowired
    private ScheduleConflictService scheduleConflictService;

    @Autowired
    private GameTableRepository gameTableRepository;

    @Autowired
    private MasterService masterService;

    @Autowired
    private UserRepository userRepository;

    private User master;
    private GameTable table;

    @BeforeEach
    void setUp() {
        master = userRepository.save(new User(randomDiscordId(), "Schedule Master"));
        table = gameTableRepository.save(withDuration(new GameTable("Mesa de los martes", master), LocalTime.of(3, 0)));
        masterService.createPrimary(table, master);
    }

    @Test
    void aSlotSurvivesTheRoundTripThroughTheCompositeKey() {
        tableScheduleService.replace(table, List.of(new TableScheduleEntry(Weekday.Tuesday, LocalTime.of(20, 0))), master.getId());

        assertThat(tableScheduleService.findByTable(table.getId()))
                .containsExactly(new TableScheduleEntry(Weekday.Tuesday, LocalTime.of(20, 0)));
    }

    /** The agenda is handed out the way a week reads, whatever order it was written in. */
    @Test
    void theAgendaComesBackInWeekOrder() {
        tableScheduleService.replace(
                table,
                List.of(
                        new TableScheduleEntry(Weekday.Saturday, LocalTime.of(18, 0)),
                        new TableScheduleEntry(Weekday.Tuesday, LocalTime.of(23, 0)),
                        new TableScheduleEntry(Weekday.Tuesday, LocalTime.of(9, 0))),
                master.getId());

        assertThat(tableScheduleService.findByTable(table.getId()))
                .containsExactly(
                        new TableScheduleEntry(Weekday.Tuesday, LocalTime.of(9, 0)),
                        new TableScheduleEntry(Weekday.Tuesday, LocalTime.of(23, 0)),
                        new TableScheduleEntry(Weekday.Saturday, LocalTime.of(18, 0)));
    }

    /**
     * The reason slots are marked and not dropped: the primary key is (table, weekday, hourtime), so
     * re-adding one the master had removed has to find the old row instead of inserting its key
     * twice - which against a real MySQL would be a duplicate-key failure, not a silent overwrite.
     */
    @Test
    void aSlotThatIsRemovedAndPutBackDoesNotCollideWithItsOwnKey() {
        TableScheduleEntry tuesday = new TableScheduleEntry(Weekday.Tuesday, LocalTime.of(20, 0));
        tableScheduleService.replace(table, List.of(tuesday), master.getId());
        tableScheduleService.replace(table, List.of(), master.getId());

        tableScheduleService.replace(table, List.of(tuesday), master.getId());

        assertThat(tableScheduleService.findByTable(table.getId())).containsExactly(tuesday);
    }

    /** R1 end to end: the second live table of the same master in the same stretch is refused. */
    @Test
    void aMasterCannotRunTwoTablesWhoseAgendasOverlap() {
        tableScheduleService.replace(table, List.of(new TableScheduleEntry(Weekday.Tuesday, LocalTime.of(20, 0))), master.getId());

        GameTable second = gameTableRepository.save(withDuration(new GameTable("Mesa que choca", master), LocalTime.of(3, 0)));
        masterService.createPrimary(second, master);

        // 22:00 falls inside the 20:00 + 3 h stretch: they overlap without sharing an hourtime (#178).
        assertThatThrownBy(() -> tableScheduleService.replace(
                        second, List.of(new TableScheduleEntry(Weekday.Tuesday, LocalTime.of(22, 0))), master.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Mesa de los martes");

        assertThat(tableScheduleService.findByTable(second.getId())).isEmpty();
    }

    /** Chaining two tables is legitimate: the interval is half-open (#178). */
    @Test
    void aMasterCanRunATableThatStartsExactlyWhenAnotherEnds() {
        tableScheduleService.replace(table, List.of(new TableScheduleEntry(Weekday.Tuesday, LocalTime.of(20, 0))), master.getId());

        GameTable second = gameTableRepository.save(withDuration(new GameTable("Mesa encadenada", master), LocalTime.of(2, 0)));
        masterService.createPrimary(second, master);

        tableScheduleService.replace(second, List.of(new TableScheduleEntry(Weekday.Tuesday, LocalTime.of(23, 0))), master.getId());

        assertThat(tableScheduleService.findByTable(second.getId())).hasSize(1);
    }

    /** The weekly wrap, against the database: Tuesday 23:00 + 3 h ends on Wednesday (#22, #178). */
    @Test
    void aSessionThatRunsIntoTheNextDayBlocksTheNextMorning() {
        tableScheduleService.replace(table, List.of(new TableScheduleEntry(Weekday.Tuesday, LocalTime.of(23, 0))), master.getId());

        GameTable second = gameTableRepository.save(withDuration(new GameTable("Mesa del miércoles", master), LocalTime.of(2, 0)));
        masterService.createPrimary(second, master);

        assertThatThrownBy(() -> tableScheduleService.replace(
                        second, List.of(new TableScheduleEntry(Weekday.Wednesday, LocalTime.of(1, 0))), master.getId()))
                .isInstanceOf(ConflictException.class);
    }

    /** Two slots of the same table overlapping is bad input, not a clash with anybody (#178). */
    @Test
    void anAgendaThatOverlapsItselfIsRejectedBeforeAnythingIsWritten() {
        assertThatThrownBy(() -> tableScheduleService.replace(
                        table,
                        List.of(
                                new TableScheduleEntry(Weekday.Friday, LocalTime.of(20, 0)),
                                new TableScheduleEntry(Weekday.Friday, LocalTime.of(22, 0))),
                        master.getId()))
                .isInstanceOf(InvalidRequestException.class);

        assertThat(tableScheduleService.findByTable(table.getId())).isEmpty();
    }

    /** A table with no duration occupies no interval, so it clashes with nothing (#178). */
    @Test
    void aTableWithoutADurationNeverClashes() {
        tableScheduleService.replace(table, List.of(new TableScheduleEntry(Weekday.Thursday, LocalTime.of(20, 0))), master.getId());

        GameTable noDuration = gameTableRepository.save(new GameTable("Mesa sin duración", master));
        masterService.createPrimary(noDuration, master);

        tableScheduleService.replace(noDuration, List.of(new TableScheduleEntry(Weekday.Thursday, LocalTime.of(20, 0))), master.getId());

        assertThat(scheduleConflictService.intervalsOf(noDuration)).isEmpty();
        assertThat(tableScheduleService.findByTable(noDuration.getId())).hasSize(1);
    }

    private static GameTable withDuration(GameTable gameTable, LocalTime duration) {
        gameTable.setDuration(duration);
        gameTable.setStatus(GameTableStatus.Opened);
        return gameTable;
    }

    /** discord_id is VARCHAR(32); a UUID with the dashes stripped fits exactly. */
    private static String randomDiscordId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
