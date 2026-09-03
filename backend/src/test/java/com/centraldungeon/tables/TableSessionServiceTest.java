package com.centraldungeon.tables;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.exception.ForbiddenActionException;
import com.centraldungeon.common.exception.InvalidRequestException;
import com.centraldungeon.notifications.NotificationService;
import com.centraldungeon.registrations.TableRegistration;
import com.centraldungeon.registrations.TableRegistrationRepository;
import com.centraldungeon.registrations.TableRegistrationStatus;
import com.centraldungeon.tables.dto.AttendanceEntryRequest;
import com.centraldungeon.tables.dto.AttendanceSummaryResponse;
import com.centraldungeon.tables.dto.MySessionsResponse;
import com.centraldungeon.tables.dto.RecordAttendanceRequest;
import com.centraldungeon.tables.dto.TableScheduleEntry;
import com.centraldungeon.tables.dto.UpdateSessionRequest;
import com.centraldungeon.users.User;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The calendar: how it is laid out, what a pause does to it, and the three ways a master moves it.
 *
 * <p>The weekly wrap of #22 has its own tests. The community plays at night in America, which is the
 * small hours of the next day in UTC, and getting that backwards is the most likely bug of the whole
 * phase (fase-1-master.md 7).
 */
@ExtendWith(MockitoExtension.class)
class TableSessionServiceTest {

    @Mock
    private TableSessionRepository sessionRepository;

    @Mock
    private SessionAttendanceRepository attendanceRepository;

    @Mock
    private TableScheduleService tableScheduleService;

    @Mock
    private GameTableRepository gameTableRepository;

    @Mock
    private TableRegistrationRepository registrationRepository;

    @Mock
    private MasterService masterService;

    @Mock
    private NotificationService notificationService;

    private final GameTableMapper gameTableMapper = new GameTableMapperImpl();

    private TableSessionService service;

    private TableSessionService service() {
        if (service == null) {
            service = new TableSessionService(
                    sessionRepository, attendanceRepository, tableScheduleService, gameTableRepository, registrationRepository,
                    masterService, notificationService, gameTableMapper);
        }
        return service;
    }

    // ---------------------------------------------------------------- materialization (#26, #33)

    @Test
    void materializesOneSessionPerWeekFromTheStartDate() {
        GameTable table = table("t1", LocalDateTime.parse("2026-09-08T20:00"), 3);
        agenda("t1", entry(Weekday.Tuesday, "20:00"));

        service().materialize(table);

        List<TableSession> saved = savedSessions(3);
        assertThat(saved).extracting(TableSession::getSequenceNumber).containsExactly(1, 2, 3);
        assertThat(saved).extracting(TableSession::getScheduledAt)
                .containsExactly(
                        LocalDateTime.parse("2026-09-08T20:00"),
                        LocalDateTime.parse("2026-09-15T20:00"),
                        LocalDateTime.parse("2026-09-22T20:00"));
    }

    /**
     * The wrap of #22: a table its players call "Tuesday night" is Wednesday in UTC, and the calendar
     * has to land on Wednesday - showing them Tuesday is the frontend's job, once, at the edge.
     */
    @Test
    void materializesAcrossTheWeekendWrapWithoutMovingTheDayBack() {
        // Starts Tuesday 23:00 UTC; the agenda is Wednesday 02:00 UTC - the same evening in America.
        GameTable table = table("t2", LocalDateTime.parse("2026-09-08T23:00"), 2);
        agenda("t2", entry(Weekday.Wednesday, "02:00"));

        service().materialize(table);

        assertThat(savedSessions(2)).extracting(TableSession::getScheduledAt)
                .containsExactly(LocalDateTime.parse("2026-09-09T02:00"), LocalDateTime.parse("2026-09-16T02:00"));
    }

    /** A table that plays twice a week fills its run in half the weeks, in week order. */
    @Test
    void materializesTwoSlotsAWeekInWeekOrder() {
        GameTable table = table("t3", LocalDateTime.parse("2026-09-07T00:00"), 4);
        agenda("t3", entry(Weekday.Tuesday, "20:00"), entry(Weekday.Friday, "21:00"));

        service().materialize(table);

        assertThat(savedSessions(4)).extracting(TableSession::getScheduledAt)
                .containsExactly(
                        LocalDateTime.parse("2026-09-08T20:00"),
                        LocalDateTime.parse("2026-09-11T21:00"),
                        LocalDateTime.parse("2026-09-15T20:00"),
                        LocalDateTime.parse("2026-09-18T21:00"));
    }

    /** A slot earlier in the week than the start date belongs to the next week, not to this one. */
    @Test
    void skipsTheSlotsOfTheFirstWeekThatAreAlreadyBehindTheStartDate() {
        GameTable table = table("t4", LocalDateTime.parse("2026-09-10T00:00"), 1);
        agenda("t4", entry(Weekday.Tuesday, "20:00"));

        service().materialize(table);

        assertThat(savedSessions(1)).extracting(TableSession::getScheduledAt)
                .containsExactly(LocalDateTime.parse("2026-09-15T20:00"));
    }

    /** #196, first case: no start date, no calendar - and no refusal. */
    @Test
    void materializesNothingWhenTheTableHasNoStartDate() {
        service().materialize(table("t5", null, 12));
        verify(sessionRepository, never()).save(any());
    }

    /** #196, second case: no session count. */
    @Test
    void materializesNothingWhenTheTableHasNoSessionCount() {
        service().materialize(table("t6", LocalDateTime.parse("2026-09-08T20:00"), null));
        verify(sessionRepository, never()).save(any());
    }

    /** #196, third case: an approved table whose master never wrote an agenda. */
    @Test
    void materializesNothingWhenTheTableHasNoAgenda() {
        GameTable table = table("t7", LocalDateTime.parse("2026-09-08T20:00"), 12);
        agenda("t7");

        service().materialize(table);

        verify(sessionRepository, never()).save(any());
    }

    /** Opening a table twice must not double its calendar. */
    @Test
    void doesNotMaterializeATableThatAlreadyHasSessions() {
        GameTable table = table("t8", LocalDateTime.parse("2026-09-08T20:00"), 12);
        when(sessionRepository.existsByGameTable_Id("t8")).thenReturn(true);

        service().materialize(table);

        verify(sessionRepository, never()).save(any());
        verify(tableScheduleService, never()).findByTable(anyString());
    }

    // ---------------------------------------------------------------- the pause (#32, #33)

    /** A paused table promises no dates, so the pending sessions are not shown - to its master either. */
    @Test
    void hidesThePendingSessionsWhileTheTableIsPaused() {
        GameTable table = table("t9", LocalDateTime.parse("2026-09-08T20:00"), 3);
        table.setStatus(GameTableStatus.Pause);
        TableSession held = session(table, "s1", 1, "2026-09-08T20:00", TableSessionStatus.Held);
        TableSession pending = session(table, "s2", 2, "2026-09-15T20:00", TableSessionStatus.Scheduled);
        when(gameTableRepository.findById("t9")).thenReturn(Optional.of(table));
        when(masterService.isMasterOf("t9", "master-1")).thenReturn(true);
        when(sessionRepository.findByGameTable_IdOrderBySequenceNumberAsc("t9")).thenReturn(List.of(held, pending));
        when(registrationRepository.findByGameTable_IdAndStatusOrderByCreatedAtAsc("t9", TableRegistrationStatus.Player))
                .thenReturn(List.of());
        when(attendanceRepository.findById_TableSessionIdIn(List.of("s1"))).thenReturn(List.of());

        assertThat(service().listForTable("t9", "master-1")).extracting("id").containsExactly("s1");
    }

    /** Resuming re-lays what was still pending, keeping the run's numbering (#33). */
    @Test
    void relaysThePendingSessionsFromTheResumeDate() {
        GameTable table = table("t10", LocalDateTime.parse("2026-09-08T20:00"), 3);
        TableSession fourth = session(table, "s4", 4, "2026-09-29T20:00", TableSessionStatus.Scheduled);
        TableSession fifth = session(table, "s5", 5, "2026-10-06T20:00", TableSessionStatus.Scheduled);
        when(sessionRepository.findByGameTable_IdAndStatusOrderBySequenceNumberAsc("t10", TableSessionStatus.Scheduled))
                .thenReturn(List.of(fourth, fifth));
        agenda("t10", entry(Weekday.Tuesday, "20:00"));
        when(registrationRepository.findByGameTable_Id("t10")).thenReturn(List.of());

        service().rescheduleAfterPause(table, LocalDateTime.parse("2026-11-02T09:00"));

        assertThat(fourth.getScheduledAt()).isEqualTo(LocalDateTime.parse("2026-11-03T20:00"));
        assertThat(fifth.getScheduledAt()).isEqualTo(LocalDateTime.parse("2026-11-10T20:00"));
        assertThat(fourth.getSequenceNumber()).isEqualTo(4);
    }

    /** What was played and what was called off keep their dates: those are facts, not plans. */
    @Test
    void leavesTheHeldAndCancelledSessionsWhereTheyAreWhenResuming() {
        GameTable table = table("t11", LocalDateTime.parse("2026-09-08T20:00"), 3);
        when(sessionRepository.findByGameTable_IdAndStatusOrderBySequenceNumberAsc("t11", TableSessionStatus.Scheduled))
                .thenReturn(List.of());

        service().rescheduleAfterPause(table, LocalDateTime.parse("2026-11-02T09:00"));

        verify(tableScheduleService, never()).findByTable(anyString());
        verify(notificationService, never()).notifySessionScheduled(anyString(), any());
    }

    /** An agenda emptied during the pause leaves the dates alone rather than inventing instants. */
    @Test
    void keepsTheDatesWhenTheAgendaWasEmptiedDuringThePause() {
        GameTable table = table("t12", LocalDateTime.parse("2026-09-08T20:00"), 3);
        TableSession pending = session(table, "s6", 2, "2026-09-15T20:00", TableSessionStatus.Scheduled);
        when(sessionRepository.findByGameTable_IdAndStatusOrderBySequenceNumberAsc("t12", TableSessionStatus.Scheduled))
                .thenReturn(List.of(pending));
        agenda("t12");

        service().rescheduleAfterPause(table, LocalDateTime.parse("2026-11-02T09:00"));

        assertThat(pending.getScheduledAt()).isEqualTo(LocalDateTime.parse("2026-09-15T20:00"));
    }

    // ---------------------------------------------------------------- correcting, holding, cancelling

    @Test
    void movesOneSessionAndTellsThePlayers() {
        GameTable table = table("t13", LocalDateTime.parse("2026-09-08T20:00"), 3);
        TableSession target = lockedSession(table, "s7", 2, "2026-09-15T20:00", TableSessionStatus.Scheduled);
        emptyRoster("t13", "s7");
        when(registrationRepository.findByGameTable_Id("t13"))
                .thenReturn(List.of(registration(table, "player-1", TableRegistrationStatus.Player)));

        service().update("s7", new UpdateSessionRequest(LocalDateTime.parse("2026-09-16T20:00"), "Se movió un día"), "master-1");

        assertThat(target.getScheduledAt()).isEqualTo(LocalDateTime.parse("2026-09-16T20:00"));
        assertThat(target.getNotes()).isEqualTo("Se movió un día");
        verify(notificationService).notifySessionScheduled("player-1", table);
    }

    /** Writing a note is not moving a date, and does not wake anybody's notification bell. */
    @Test
    void doesNotNotifyWhenOnlyTheNotesChange() {
        GameTable table = table("t14", LocalDateTime.parse("2026-09-08T20:00"), 3);
        lockedSession(table, "s8", 2, "2026-09-15T20:00", TableSessionStatus.Scheduled);
        emptyRoster("t14", "s8");

        service().update("s8", new UpdateSessionRequest(null, "Cerramos el arco"), "master-1");

        verify(notificationService, never()).notifySessionScheduled(anyString(), any());
    }

    @Test
    void refusesToCorrectASessionThatWasAlreadyPlayed() {
        GameTable table = table("t15", LocalDateTime.parse("2026-09-08T20:00"), 3);
        lockedSession(table, "s9", 1, "2026-09-08T20:00", TableSessionStatus.Held);

        assertThatThrownBy(() -> service().update("s9", new UpdateSessionRequest(LocalDateTime.parse("2026-09-09T20:00"), null), "master-1"))
                .isInstanceOf(ConflictException.class);
    }

    /** Pertenencia and not role: somebody who does not run this table cannot touch its calendar (#17, #135). */
    @Test
    void refusesToCorrectASessionOfATableTheActorDoesNotRun() {
        GameTable table = table("t16", LocalDateTime.parse("2026-09-08T20:00"), 3);
        TableSession target = session(table, "s10", 1, "2026-09-08T20:00", TableSessionStatus.Scheduled);
        when(sessionRepository.findByIdForUpdate("s10")).thenReturn(Optional.of(target));
        when(masterService.isMasterOf("t16", "stranger")).thenReturn(false);

        assertThatThrownBy(() -> service().update("s10", new UpdateSessionRequest(null, "hola"), "stranger"))
                .isInstanceOf(ForbiddenActionException.class);
    }

    /** #195: marking a session played is its own action, and it starts from Scheduled. */
    @Test
    void marksASessionAsHeld() {
        GameTable table = table("t17", LocalDateTime.parse("2026-09-08T20:00"), 3);
        TableSession target = lockedSession(table, "s11", 1, "2026-09-08T20:00", TableSessionStatus.Scheduled);
        emptyRoster("t17", "s11");

        service().hold("s11", "master-1");

        assertThat(target.getStatus()).isEqualTo(TableSessionStatus.Held);
    }

    @Test
    void refusesToHoldASessionThatWasCancelled() {
        GameTable table = table("t18", LocalDateTime.parse("2026-09-08T20:00"), 3);
        lockedSession(table, "s12", 1, "2026-09-08T20:00", TableSessionStatus.Cancelled);

        assertThatThrownBy(() -> service().hold("s12", "master-1")).isInstanceOf(ConflictException.class);
    }

    /** #194: the row stays as a record and the table gets the session back at the end. */
    @Test
    void cancelsASessionAndAppendsAReplacementAtTheEndOfTheRun() {
        GameTable table = table("t19", LocalDateTime.parse("2026-09-08T20:00"), 3);
        TableSession second = lockedSession(table, "s13", 2, "2026-09-15T20:00", TableSessionStatus.Scheduled);
        TableSession third = session(table, "s14", 3, "2026-09-22T20:00", TableSessionStatus.Scheduled);
        agenda("t19", entry(Weekday.Tuesday, "20:00"));
        when(sessionRepository.findByGameTable_IdOrderBySequenceNumberAsc("t19"))
                .thenReturn(List.of(second, third))
                .thenReturn(List.of(second, third));
        when(sessionRepository.findMaxSequenceNumber("t19")).thenReturn(3);
        when(registrationRepository.findByGameTable_Id("t19")).thenReturn(List.of());
        when(registrationRepository.findByGameTable_IdAndStatusOrderByCreatedAtAsc("t19", TableRegistrationStatus.Player))
                .thenReturn(List.of());
        when(attendanceRepository.findById_TableSessionIdIn(any())).thenReturn(List.of());

        service().cancel("s13", "master-1");

        assertThat(second.getStatus()).isEqualTo(TableSessionStatus.Cancelled);
        assertThat(second.getSequenceNumber()).isEqualTo(2);
        TableSession replacement = savedSessions(1).getFirst();
        assertThat(replacement.getSequenceNumber()).isEqualTo(4);
        assertThat(replacement.getScheduledAt()).isEqualTo(LocalDateTime.parse("2026-09-29T20:00"));
    }

    /** No agenda, no slot to put a replacement on - a made-up date would be worse than a shorter run. */
    @Test
    void cancelsWithoutAReplacementWhenTheTableHasNoAgendaLeft() {
        GameTable table = table("t20", LocalDateTime.parse("2026-09-08T20:00"), 3);
        TableSession second = lockedSession(table, "s15", 2, "2026-09-15T20:00", TableSessionStatus.Scheduled);
        agenda("t20");
        when(sessionRepository.findByGameTable_IdOrderBySequenceNumberAsc("t20")).thenReturn(List.of(second));
        when(registrationRepository.findByGameTable_Id("t20")).thenReturn(List.of());
        when(registrationRepository.findByGameTable_IdAndStatusOrderByCreatedAtAsc("t20", TableRegistrationStatus.Player))
                .thenReturn(List.of());
        when(attendanceRepository.findById_TableSessionIdIn(any())).thenReturn(List.of());

        service().cancel("s15", "master-1");

        verify(sessionRepository, never()).save(any());
    }

    @Test
    void tellsThePlayersWhenASessionIsCalledOff() {
        GameTable table = table("t21", LocalDateTime.parse("2026-09-08T20:00"), 3);
        TableSession second = lockedSession(table, "s16", 2, "2026-09-15T20:00", TableSessionStatus.Scheduled);
        agenda("t21");
        when(sessionRepository.findByGameTable_IdOrderBySequenceNumberAsc("t21")).thenReturn(List.of(second));
        when(registrationRepository.findByGameTable_Id("t21"))
                .thenReturn(List.of(registration(table, "player-1", TableRegistrationStatus.Player)));
        when(registrationRepository.findByGameTable_IdAndStatusOrderByCreatedAtAsc("t21", TableRegistrationStatus.Player))
                .thenReturn(List.of());
        when(attendanceRepository.findById_TableSessionIdIn(any())).thenReturn(List.of());

        service().cancel("s16", "master-1");

        verify(notificationService).notifySessionCanceled("player-1", table);
    }

    // ---------------------------------------------------------------- attendance (#36, #137)

    @Test
    void recordsAttendanceForThePlayersOfTheTable() {
        GameTable table = table("t22", LocalDateTime.parse("2026-09-08T20:00"), 3);
        TableSession target = lockedSession(table, "s17", 1, "2026-09-08T20:00", TableSessionStatus.Scheduled);
        roster("t22", "s17", registration(table, "player-1", TableRegistrationStatus.Player));
        when(attendanceRepository.findById(new SessionAttendanceId("s17", "player-1"))).thenReturn(Optional.empty());

        service().recordAttendance(
                "s17", new RecordAttendanceRequest(List.of(new AttendanceEntryRequest("player-1", AttendanceStatus.Present))), "master-1");

        ArgumentCaptor<SessionAttendance> saved = ArgumentCaptor.forClass(SessionAttendance.class);
        verify(attendanceRepository).save(saved.capture());
        assertThat(saved.getValue().getAttendance()).isEqualTo(AttendanceStatus.Present);
        // Recording who came does not say the session happened - that is a separate action (#195).
        assertThat(target.getStatus()).isEqualTo(TableSessionStatus.Scheduled);
    }

    /** The roster is the server's, not the caller's: a stranger's id is malformed input (#121). */
    @Test
    void refusesToRecordAttendanceForSomebodyWhoDoesNotPlayAtTheTable() {
        GameTable table = table("t23", LocalDateTime.parse("2026-09-08T20:00"), 3);
        lockedSession(table, "s18", 1, "2026-09-08T20:00", TableSessionStatus.Scheduled);
        when(registrationRepository.findByGameTable_IdAndStatusOrderByCreatedAtAsc("t23", TableRegistrationStatus.Player))
                .thenReturn(List.of());

        RecordAttendanceRequest request =
                new RecordAttendanceRequest(List.of(new AttendanceEntryRequest("stranger", AttendanceStatus.Present)));

        assertThatThrownBy(() -> service().recordAttendance("s18", request, "master-1")).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void refusesToRecordAttendanceForASessionThatWasCancelled() {
        GameTable table = table("t24", LocalDateTime.parse("2026-09-08T20:00"), 3);
        lockedSession(table, "s19", 1, "2026-09-08T20:00", TableSessionStatus.Cancelled);

        RecordAttendanceRequest request =
                new RecordAttendanceRequest(List.of(new AttendanceEntryRequest("player-1", AttendanceStatus.Present)));

        assertThatThrownBy(() -> service().recordAttendance("s19", request, "master-1")).isInstanceOf(ConflictException.class);
    }

    /** Everybody who plays is on the roster, whether or not anything was recorded for them. */
    @Test
    void listsEveryActivePlayerOnTheRosterEvenWithNothingRecorded() {
        GameTable table = table("t25", LocalDateTime.parse("2026-09-08T20:00"), 3);
        TableSession first = session(table, "s20", 1, "2026-09-08T20:00", TableSessionStatus.Scheduled);
        when(gameTableRepository.findById("t25")).thenReturn(Optional.of(table));
        when(masterService.isMasterOf("t25", "master-1")).thenReturn(true);
        when(sessionRepository.findByGameTable_IdOrderBySequenceNumberAsc("t25")).thenReturn(List.of(first));
        when(registrationRepository.findByGameTable_IdAndStatusOrderByCreatedAtAsc("t25", TableRegistrationStatus.Player))
                .thenReturn(List.of(registration(table, "player-1", TableRegistrationStatus.Player)));
        when(attendanceRepository.findById_TableSessionIdIn(List.of("s20"))).thenReturn(List.of());

        assertThat(service().listForTable("t25", "master-1").getFirst().attendance())
                .singleElement()
                .satisfies(line -> {
                    assertThat(line.userId()).isEqualTo("player-1");
                    assertThat(line.attendance()).isEqualTo(AttendanceStatus.Unknown);
                });
    }

    /** #137: three numbers, and the denominator is what was actually recorded. */
    @Test
    void summarizesAttendanceWithUnknownOutOfTheDenominator() {
        when(attendanceRepository.countByTableAndUser("t26", "player-1"))
                .thenReturn(List.of(
                        new AttendanceCount(AttendanceStatus.Present, 8),
                        new AttendanceCount(AttendanceStatus.Excused, 2),
                        new AttendanceCount(AttendanceStatus.Absent, 1)));

        AttendanceSummaryResponse summary = service().summarize("t26", "player-1");

        assertThat(summary.present()).isEqualTo(8);
        assertThat(summary.excused()).isEqualTo(2);
        assertThat(summary.absent()).isEqualTo(1);
        // 11, not 12: the twelfth session has nothing recorded and stays out (#137).
        assertThat(summary.registered()).isEqualTo(11);
    }

    /** A table that just started is all Unknown, and nobody reads as an absentee (#137). */
    @Test
    void summarizesATableWithNothingRecordedAsAllZeros() {
        when(attendanceRepository.countByTableAndUser("t27", "player-1")).thenReturn(List.of());

        AttendanceSummaryResponse summary = service().summarize("t27", "player-1");

        assertThat(summary).isEqualTo(new AttendanceSummaryResponse(0, 0, 0, 0));
    }

    // ---------------------------------------------------------------- the player's own view (#121)

    @Test
    void givesAPlayerTheirOwnCalendarAndTheirOwnAttendance() {
        GameTable table = table("t28", LocalDateTime.parse("2026-09-08T20:00"), 2);
        TableSession first = session(table, "s21", 1, "2026-09-08T20:00", TableSessionStatus.Held);
        when(gameTableRepository.findById("t28")).thenReturn(Optional.of(table));
        when(registrationRepository.existsByGameTable_IdAndUser_IdAndStatusIn("t28", "player-1", List.of(TableRegistrationStatus.Player)))
                .thenReturn(true);
        when(sessionRepository.findByGameTable_IdOrderBySequenceNumberAsc("t28")).thenReturn(List.of(first));
        SessionAttendance row = new SessionAttendance(first, user("player-1"), AttendanceStatus.Present);
        when(attendanceRepository.findByTableAndUser("t28", "player-1")).thenReturn(List.of(row));
        when(attendanceRepository.countByTableAndUser("t28", "player-1"))
                .thenReturn(List.of(new AttendanceCount(AttendanceStatus.Present, 1)));

        MySessionsResponse response = service().listMine("t28", "player-1");

        assertThat(response.sessions()).singleElement().satisfies(session -> {
            assertThat(session.id()).isEqualTo("s21");
            assertThat(session.myAttendance()).isEqualTo(AttendanceStatus.Present);
        });
        assertThat(response.summary().registered()).isEqualTo(1);
    }

    @Test
    void refusesTheOwnCalendarToSomebodyWhoDoesNotPlayAtTheTable() {
        GameTable table = table("t29", LocalDateTime.parse("2026-09-08T20:00"), 2);
        when(gameTableRepository.findById("t29")).thenReturn(Optional.of(table));
        when(registrationRepository.existsByGameTable_IdAndUser_IdAndStatusIn("t29", "stranger", List.of(TableRegistrationStatus.Player)))
                .thenReturn(false);

        assertThatThrownBy(() -> service().listMine("t29", "stranger")).isInstanceOf(ForbiddenActionException.class);
    }

    // ---------------------------------------------------------------- helpers

    private void agenda(String tableId, TableScheduleEntry... entries) {
        when(tableScheduleService.findByTable(tableId)).thenReturn(List.of(entries));
    }

    /** Locks the session and grants pertenencia to "master-1" - the setup every mutation shares. */
    private TableSession lockedSession(GameTable table, String id, int sequence, String at, TableSessionStatus status) {
        TableSession session = session(table, id, sequence, at, status);
        when(sessionRepository.findByIdForUpdate(id)).thenReturn(Optional.of(session));
        when(masterService.isMasterOf(table.getId(), "master-1")).thenReturn(true);
        return session;
    }

    /** A table with nobody signed up: what the response mapping needs when the roster is beside the point. */
    private void emptyRoster(String tableId, String sessionId) {
        when(registrationRepository.findByGameTable_IdAndStatusOrderByCreatedAtAsc(tableId, TableRegistrationStatus.Player))
                .thenReturn(List.of());
        when(attendanceRepository.findById_TableSessionIdIn(List.of(sessionId))).thenReturn(List.of());
    }

    private void roster(String tableId, String sessionId, TableRegistration... players) {
        when(registrationRepository.findByGameTable_IdAndStatusOrderByCreatedAtAsc(tableId, TableRegistrationStatus.Player))
                .thenReturn(List.of(players));
        when(attendanceRepository.findById_TableSessionIdIn(List.of(sessionId))).thenReturn(List.of());
    }

    private List<TableSession> savedSessions(int expected) {
        ArgumentCaptor<TableSession> captor = ArgumentCaptor.forClass(TableSession.class);
        verify(sessionRepository, org.mockito.Mockito.times(expected)).save(captor.capture());
        return new ArrayList<>(captor.getAllValues());
    }

    private static TableScheduleEntry entry(Weekday weekday, String hourtime) {
        return new TableScheduleEntry(weekday, LocalTime.parse(hourtime));
    }

    private static GameTable table(String id, java.time.LocalDateTime startDate, Integer totalSessions) {
        GameTable table = new GameTable("Mesa " + id, user("creator-" + id));
        ReflectionTestUtils.setField(table, "id", id);
        table.setStartDate(startDate);
        table.setTotalSessions(totalSessions);
        table.setDuration(LocalTime.of(3, 0));
        table.setStatus(GameTableStatus.Opened);
        return table;
    }

    private static TableSession session(GameTable table, String id, int sequence, String at, TableSessionStatus status) {
        TableSession session = new TableSession(table, sequence, LocalDateTime.parse(at));
        ReflectionTestUtils.setField(session, "id", id);
        session.setStatus(status);
        return session;
    }

    private static TableRegistration registration(GameTable table, String userId, TableRegistrationStatus status) {
        TableRegistration registration = new TableRegistration(table, user(userId), null);
        registration.setStatus(status);
        return registration;
    }

    private static User user(String id) {
        User user = new User("discord-" + id, "name-" + id);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
