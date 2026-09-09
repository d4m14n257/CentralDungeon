package com.centraldungeon.tables;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.centraldungeon.registrations.TableRegistrationRepository;
import com.centraldungeon.tables.dto.WeeklyBlockResponse;
import com.centraldungeon.tables.dto.WeeklyCommitmentResponse;
import com.centraldungeon.users.User;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The matrix #178 asks for, and the piece of F1 with the most edge cases - which is why it is tested
 * on its own, before being wired to anything (fase-1-master.md 7).
 *
 * <p>Two levels, deliberately: the interval arithmetic is pure and tested without a repository in
 * sight, and only the questions that need to know who is committed to what use mocks.
 */
@ExtendWith(MockitoExtension.class)
class ScheduleConflictServiceTest {

    @Mock
    private TableScheduleRepository scheduleRepository;

    @Mock
    private GameTableRepository gameTableRepository;

    @Mock
    private TableRegistrationRepository registrationRepository;

    @InjectMocks
    private ScheduleConflictService scheduleConflictService;

    /** The interval arithmetic. No database, no mocks: it is a function over two pairs of numbers. */
    @Nested
    class WeeklyIntervals {

        @Test
        void twoSessionsThatShareAnHourOverlap() {
            WeeklyInterval first = interval(Weekday.Tuesday, "20:00", "03:00");
            WeeklyInterval second = interval(Weekday.Tuesday, "22:00", "03:00");

            assertThat(first.overlaps(second)).isTrue();
            assertThat(second.overlaps(first)).isTrue();
        }

        /** Comparing weekday + hourtime for equality would have missed exactly this case (#178). */
        @Test
        void twoSessionsAtDifferentHoursOfTheSameDayCanStillCollide() {
            assertThat(interval(Weekday.Friday, "18:00", "04:00").overlaps(interval(Weekday.Friday, "21:00", "01:00"))).isTrue();
        }

        /** Half-open: chaining two tables is legitimate and must not be refused (#178). */
        @Test
        void aSessionStartingExactlyWhenAnotherEndsDoesNotClash() {
            assertThat(interval(Weekday.Tuesday, "20:00", "03:00").overlaps(interval(Weekday.Tuesday, "23:00", "02:00"))).isFalse();
        }

        /**
         * The most likely bug of F1: the community plays at night in America, which is the small
         * hours of the next day in UTC (#22).
         */
        @Test
        void aSessionThatRunsIntoTheNextDayClashesWithOneThatStartsThere() {
            WeeklyInterval tuesdayNight = interval(Weekday.Tuesday, "23:00", "03:00");
            WeeklyInterval wednesdayDawn = interval(Weekday.Wednesday, "01:00", "02:00");

            assertThat(tuesdayNight.overlaps(wednesdayDawn)).isTrue();
        }

        /** And the same thing at the seam of the week: Sunday night runs into Monday. */
        @Test
        void aSessionThatWrapsPastSundayClashesWithOneOnMondayMorning() {
            WeeklyInterval sundayNight = interval(Weekday.Sunday, "22:00", "04:00");
            WeeklyInterval mondayDawn = interval(Weekday.Monday, "01:00", "01:00");

            assertThat(sundayNight.overlaps(mondayDawn)).isTrue();
            assertThat(mondayDawn.overlaps(sundayNight)).isTrue();
        }

        @Test
        void aSessionThatWrapsDoesNotClashWithAnUnrelatedSlotMidWeek() {
            assertThat(interval(Weekday.Sunday, "22:00", "04:00").overlaps(interval(Weekday.Wednesday, "20:00", "03:00"))).isFalse();
        }

        @Test
        void slotsOnDifferentDaysDoNotClash() {
            assertThat(interval(Weekday.Monday, "20:00", "03:00").overlaps(interval(Weekday.Thursday, "20:00", "03:00"))).isFalse();
        }
    }

    /** Two slots of the same table that overlap each other: the 400 of #178, before anything is saved. */
    @Test
    void detectsTwoSlotsOfTheSameTableOverlappingEachOther() {
        List<WeeklyInterval> agenda = List.of(interval(Weekday.Tuesday, "20:00", "03:00"), interval(Weekday.Tuesday, "22:00", "03:00"));

        assertThat(scheduleConflictService.hasSelfOverlap(agenda)).isTrue();
    }

    @Test
    void anAgendaWhoseSlotsMerelyTouchIsFine() {
        List<WeeklyInterval> agenda = List.of(interval(Weekday.Tuesday, "20:00", "03:00"), interval(Weekday.Tuesday, "23:00", "02:00"));

        assertThat(scheduleConflictService.hasSelfOverlap(agenda)).isFalse();
    }

    /** A table that never said how long a session lasts has no interval, so it clashes with nothing. */
    @Test
    void aTableWithoutADurationHasNoIntervals() {
        GameTable table = table("table-1", GameTableStatus.Opened, null);

        assertThat(scheduleConflictService.intervalsOf(table)).isEmpty();
    }

    @Test
    void aTableWithoutAnAgendaHasNoIntervals() {
        GameTable table = table("table-2", GameTableStatus.Opened, LocalTime.of(3, 0));
        when(scheduleRepository.findById_GameTableIdAndStatus("table-2", TableScheduleStatus.Created)).thenReturn(List.of());

        assertThat(scheduleConflictService.intervalsOf(table)).isEmpty();
    }

    @Test
    void findsTheCommittedTableAnAgendaCollidesWith() {
        GameTable committed = table("committed", GameTableStatus.InProgress, LocalTime.of(3, 0));
        when(gameTableRepository.findMasteredByUserInStatuses(anyString(), any())).thenReturn(List.of(committed));
        when(registrationRepository.findTablesPlayedByUserInStatuses(anyString(), any())).thenReturn(List.of());
        when(scheduleRepository.findById_GameTableIdInAndStatus(any(), any()))
                .thenReturn(List.of(new TableSchedule("committed", Weekday.Tuesday, LocalTime.of(20, 0))));

        CommittedTable clash =
                scheduleConflictService.findClash("master-1", "new-table", List.of(interval(Weekday.Tuesday, "22:00", "02:00")));

        assertThat(clash).isNotNull();
        assertThat(clash.id()).isEqualTo("committed");
    }

    /** Running one table and playing at another weigh the same: it is one person and one Tuesday (#178). */
    @Test
    void aTableSomebodyOnlyPlaysAtCountsAsACommitmentToo() {
        GameTable played = table("played", GameTableStatus.InProgress, LocalTime.of(4, 0));
        when(gameTableRepository.findMasteredByUserInStatuses(anyString(), any())).thenReturn(List.of());
        when(registrationRepository.findTablesPlayedByUserInStatuses(anyString(), any())).thenReturn(List.of(played));
        when(scheduleRepository.findById_GameTableIdInAndStatus(any(), any()))
                .thenReturn(List.of(new TableSchedule("played", Weekday.Saturday, LocalTime.of(18, 0))));

        CommittedTable clash =
                scheduleConflictService.findClash("player-1", null, List.of(interval(Weekday.Saturday, "20:00", "03:00")));

        assertThat(clash).isNotNull();
        assertThat(clash.id()).isEqualTo("played");
    }

    /** A paused table freezes its agenda, so its slot is not reserved (#32, #178). */
    @Test
    void aPausedTableIsNotAmongTheStatusesThatCommitASlot() {
        when(gameTableRepository.findMasteredByUserInStatuses(anyString(), any())).thenReturn(List.of());
        when(registrationRepository.findTablesPlayedByUserInStatuses(anyString(), any())).thenReturn(List.of());

        CommittedTable clash =
                scheduleConflictService.findClash("master-1", null, List.of(interval(Weekday.Tuesday, "20:00", "03:00")));

        assertThat(clash).isNull();
        // The proof is in the argument: Pause is never asked for, so a paused table cannot come back.
        org.mockito.Mockito.verify(gameTableRepository)
                .findMasteredByUserInStatuses(
                        "master-1",
                        List.of(
                                GameTableStatus.Preparation,
                                GameTableStatus.ChangesRequested,
                                GameTableStatus.Opened,
                                GameTableStatus.InProgress,
                                GameTableStatus.PauseRequested));
    }

    /** The table being edited is not one of its own obstacles. */
    @Test
    void aTableDoesNotClashWithItself() {
        GameTable itself = table("table-x", GameTableStatus.Opened, LocalTime.of(3, 0));
        when(gameTableRepository.findMasteredByUserInStatuses(anyString(), any())).thenReturn(List.of(itself));
        when(registrationRepository.findTablesPlayedByUserInStatuses(anyString(), any())).thenReturn(List.of());

        CommittedTable clash =
                scheduleConflictService.findClash("master-1", "table-x", List.of(interval(Weekday.Tuesday, "20:00", "03:00")));

        assertThat(clash).isNull();
    }

    @Test
    void anEmptyAgendaNeverClashes() {
        assertThat(scheduleConflictService.findClash("master-1", null, List.of())).isNull();
    }

    /** The week as the reader's own screen asks for it (#227) - the same commitments, read instead of enforced. */
    @Nested
    class MyWeek {

        @Test
        @DisplayName("la semana junta lo que dirigís y lo que jugás, y dice cuál es cuál")
        void bringsTogetherWhatIsRunAndWhatIsPlayed() {
            GameTable mastered = table("mastered", GameTableStatus.InProgress, LocalTime.of(3, 0));
            GameTable played = table("played", GameTableStatus.Opened, LocalTime.of(4, 0));
            when(gameTableRepository.findMasteredByUserInStatuses(anyString(), any())).thenReturn(List.of(mastered));
            when(registrationRepository.findTablesPlayedByUserInStatuses(anyString(), any())).thenReturn(List.of(played));
            when(scheduleRepository.findById_GameTableIdInAndStatus(any(), any()))
                    .thenReturn(List.of(
                            new TableSchedule("mastered", Weekday.Tuesday, LocalTime.of(20, 0)),
                            new TableSchedule("played", Weekday.Saturday, LocalTime.of(18, 0))));

            List<WeeklyCommitmentResponse> week = scheduleConflictService.weekOf("person-1");

            assertThat(week).extracting(WeeklyCommitmentResponse::tableId, WeeklyCommitmentResponse::role)
                    .containsExactlyInAnyOrder(tuple("mastered", "Master"), tuple("played", "Player"));
            // Tuesday 20:00 UTC is 1 * 1440 + 20 * 60 minutes from Monday 00:00.
            assertThat(week.stream().filter(entry -> entry.tableId().equals("mastered")).findFirst().orElseThrow().blocks())
                    .containsExactly(new WeeklyBlockResponse(1440 + 1200, 180));
        }

        /** Running wins: it is the stronger claim on the same evening, and the label has to pick one. */
        @Test
        @DisplayName("quien dirige y además juega en la misma mesa se lee como que la dirige")
        void runningWinsOverPlaying() {
            GameTable both = table("both", GameTableStatus.InProgress, LocalTime.of(3, 0));
            when(gameTableRepository.findMasteredByUserInStatuses(anyString(), any())).thenReturn(List.of(both));
            when(registrationRepository.findTablesPlayedByUserInStatuses(anyString(), any())).thenReturn(List.of(both));
            when(scheduleRepository.findById_GameTableIdInAndStatus(any(), any()))
                    .thenReturn(List.of(new TableSchedule("both", Weekday.Monday, LocalTime.of(10, 0))));

            List<WeeklyCommitmentResponse> week = scheduleConflictService.weekOf("person-1");

            assertThat(week).singleElement().extracting(WeeklyCommitmentResponse::role).isEqualTo("Master");
        }

        /**
         * A draft with no agenda commits nobody to anything (#178), but it is still something the
         * reader has on - so it comes back with no blocks rather than disappearing.
         */
        @Test
        @DisplayName("una mesa sin agenda viaja sin bloques, no se omite")
        void aTableWithNoAgendaTravelsWithoutBlocks() {
            GameTable draft = table("draft", GameTableStatus.Preparation, null);
            when(gameTableRepository.findMasteredByUserInStatuses(anyString(), any())).thenReturn(List.of(draft));
            when(registrationRepository.findTablesPlayedByUserInStatuses(anyString(), any())).thenReturn(List.of());

            List<WeeklyCommitmentResponse> week = scheduleConflictService.weekOf("person-1");

            // And it costs no query: a table with no duration has no agenda worth reading.
            verify(scheduleRepository, never()).findById_GameTableIdInAndStatus(any(), any());
            assertThat(week).singleElement().satisfies(entry -> {
                assertThat(entry.tableId()).isEqualTo("draft");
                assertThat(entry.blocks()).isEmpty();
            });
        }

        @Test
        @DisplayName("sin mesas, la semana está vacía y no se consulta ninguna agenda")
        void anEmptyWeekAsksForNoAgendas() {
            when(gameTableRepository.findMasteredByUserInStatuses(anyString(), any())).thenReturn(List.of());
            when(registrationRepository.findTablesPlayedByUserInStatuses(anyString(), any())).thenReturn(List.of());

            assertThat(scheduleConflictService.weekOf("person-1")).isEmpty();
            verify(scheduleRepository, never()).findById_GameTableIdInAndStatus(any(), any());
        }
    }

    private static WeeklyInterval interval(Weekday weekday, String start, String duration) {
        return WeeklyInterval.of(weekday, LocalTime.parse(start), LocalTime.parse(duration));
    }

    private static GameTable table(String id, GameTableStatus status, LocalTime duration) {
        User creator = new User("discord-" + id, "name-" + id);
        ReflectionTestUtils.setField(creator, "id", "creator-" + id);
        GameTable table = new GameTable("Table " + id, creator);
        ReflectionTestUtils.setField(table, "id", id);
        table.setStatus(status);
        table.setDuration(duration);
        return table;
    }
}
