package com.centraldungeon.tables;

import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.exception.ForbiddenActionException;
import com.centraldungeon.common.exception.InvalidRequestException;
import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.notifications.NotificationService;
import com.centraldungeon.registrations.TableRegistration;
import com.centraldungeon.registrations.TableRegistrationRepository;
import com.centraldungeon.registrations.TableRegistrationStatus;
import com.centraldungeon.tables.dto.AttendanceEntryRequest;
import com.centraldungeon.tables.dto.AttendanceSummaryResponse;
import com.centraldungeon.tables.dto.MySessionsResponse;
import com.centraldungeon.tables.dto.PlayerSessionResponse;
import com.centraldungeon.tables.dto.PublicSessionResponse;
import com.centraldungeon.tables.dto.RecordAttendanceRequest;
import com.centraldungeon.tables.dto.SessionAttendanceEntry;
import com.centraldungeon.tables.dto.TableScheduleEntry;
import com.centraldungeon.tables.dto.TableSessionResponse;
import com.centraldungeon.tables.dto.UpdateSessionRequest;
import com.centraldungeon.users.User;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A table's calendar: turning its agenda into sessions, moving them, and recording who came.
 *
 * <p><b>Sessions are rows, not a calculation</b> (#33). The weekly agenda plus a start date plus a
 * count would describe the calendar exactly once - the first time - and {@code Pause} breaks that
 * immediately: resuming has to re-lay what was still pending, which needs to know what was actually
 * played. Materializing is also what makes correcting one date and cancelling one evening possible
 * at all.
 *
 * <p><b>The unit of time is an instant in UTC</b> (#22), unlike {@link ScheduleConflictService},
 * whose unit is a stretch of an abstract week. That is why a corrected date is never checked against
 * the clash rules of #178: those compare weeks, and moving one session is not changing the week.
 *
 * <p><b>A pause hides the pending sessions rather than marking them</b> (#32, #33). A pause is
 * reversible and its whole point is that no date is being promised while it lasts; writing a status
 * onto every pending row would be a change that resuming then has to undo, and the rows would carry
 * the pause's history instead of the session's. So it is a read filter, in one place -
 * {@link #visibleSessionsOf}.
 *
 * <p>Every mutation here answers to <b>pertenencia and not to a role</b> (#17, #121, #135): running
 * this table is what authorizes touching its calendar, which no {@code @PreAuthorize} can see.
 */
@Service
public class TableSessionService {

    /** The statuses in which somebody is affected by a change to the calendar - they are in, or waiting to be. */
    private static final List<TableRegistrationStatus> NOTIFIED_STATUSES =
            List.of(TableRegistrationStatus.Candidate, TableRegistrationStatus.Player);

    /** The materialized calendar. */
    private final TableSessionRepository sessionRepository;

    /** Who was at each session (#36). */
    private final SessionAttendanceRepository attendanceRepository;

    /** The weekly agenda the calendar is laid out on. */
    private final TableScheduleService tableScheduleService;

    /** Resolves the table a session hangs off, for the reads that start from a table id. */
    private final GameTableRepository gameTableRepository;

    /** The roster, and who gets told when a date moves. */
    private final TableRegistrationRepository registrationRepository;

    /** Answers pertenencia: a row in {@code masters}, never the platform role (#135). */
    private final MasterService masterService;

    /** Tells the players a session moved or was called off (#77's sibling for sessions). */
    private final NotificationService notificationService;

    /** Entity to DTO. */
    private final GameTableMapper gameTableMapper;

    /**
     * @param sessionRepository      the {@code table_sessions} rows
     * @param attendanceRepository   the {@code session_attendance} rows, and the count of #137
     * @param tableScheduleService   the weekly agenda a calendar is laid out on
     * @param gameTableRepository    resolves the table the reads start from
     * @param registrationRepository the table's players - the roster, and who is notified
     * @param masterService          answers pertenencia (#17, #121, #135)
     * @param notificationService    warns the players when a session moves or is called off
     * @param gameTableMapper        entity to DTO
     */
    public TableSessionService(
            TableSessionRepository sessionRepository,
            SessionAttendanceRepository attendanceRepository,
            TableScheduleService tableScheduleService,
            GameTableRepository gameTableRepository,
            TableRegistrationRepository registrationRepository,
            MasterService masterService,
            NotificationService notificationService,
            GameTableMapper gameTableMapper) {
        this.sessionRepository = sessionRepository;
        this.attendanceRepository = attendanceRepository;
        this.tableScheduleService = tableScheduleService;
        this.gameTableRepository = gameTableRepository;
        this.registrationRepository = registrationRepository;
        this.masterService = masterService;
        this.notificationService = notificationService;
        this.gameTableMapper = gameTableMapper;
    }

    /**
     * Turns the table's agenda into its calendar. Called once, when the table reaches {@code Opened}
     * (#26, #33).
     *
     * <p><b>A table missing any of the three inputs opens with no sessions and is not refused</b>
     * (#196): {@code start_date}, the agenda and {@code total_sessions} are all nullable, and turning
     * them into a precondition of approval would be a new gate on the admin's flow rather than the
     * consequence of opening that this is. The master fills them in and the sessions appear when they
     * do.
     *
     * <p>Idempotent: a table that already has a calendar keeps the one it has.
     *
     * @param table the table being opened
     */
    @Transactional
    public void materialize(GameTable table) {
        if (sessionRepository.existsByGameTable_Id(table.getId())) {
            return;
        }
        LocalDateTime startDate = table.getStartDate();
        Integer totalSessions = table.getTotalSessions();
        if (startDate == null || totalSessions == null || totalSessions <= 0) {
            return;
        }
        List<TableScheduleEntry> agenda = tableScheduleService.findByTable(table.getId());
        if (agenda.isEmpty()) {
            return;
        }

        int sequenceNumber = 1;
        for (LocalDateTime instant : occurrencesFrom(startDate, agenda, totalSessions, true)) {
            sessionRepository.save(new TableSession(table, sequenceNumber++, instant));
        }
    }

    /**
     * Re-lays the sessions a pause left pending, from the moment the table comes back (#32, #33).
     *
     * <p>Only the pending ones move. What was played and what was called off are facts with dates of
     * their own, and the run's numbering is untouched: the fifth session is still the fifth, it just
     * happens later than it was going to.
     *
     * <p>A table whose agenda was emptied while it was paused keeps the dates it had - there is
     * nothing to lay the sessions on, and inventing instants would be worse than leaving stale ones a
     * master can correct.
     *
     * @param table    the table coming back
     * @param resumeAt the instant it resumes, in UTC. The first pending session lands on the first
     *                 agenda slot at or after it
     */
    @Transactional
    public void rescheduleAfterPause(GameTable table, LocalDateTime resumeAt) {
        List<TableSession> pending =
                sessionRepository.findByGameTable_IdAndStatusOrderBySequenceNumberAsc(table.getId(), TableSessionStatus.Scheduled);
        if (pending.isEmpty()) {
            return;
        }
        List<TableScheduleEntry> agenda = tableScheduleService.findByTable(table.getId());
        if (agenda.isEmpty()) {
            return;
        }

        List<LocalDateTime> instants = occurrencesFrom(resumeAt, agenda, pending.size(), true);
        for (int index = 0; index < pending.size() && index < instants.size(); index++) {
            pending.get(index).setScheduledAt(instants.get(index));
        }
        notifyPlayers(table, true);
    }

    /**
     * The whole calendar as the people running the table see it, with notes and rosters.
     *
     * @param gameTableId the table
     * @param actorId     the actor, from the token (#121)
     * @return its sessions, first to last. While the table is paused the pending ones are not in it
     * @throws NotFoundException        if the table does not exist
     * @throws ForbiddenActionException if the actor does not run the table
     */
    @Transactional(readOnly = true)
    public List<TableSessionResponse> listForTable(String gameTableId, String actorId) {
        GameTable table = getTable(gameTableId);
        requireMasterOf(gameTableId, actorId);
        return toResponses(table, visibleSessionsOf(table));
    }

    /**
     * The calendar as anybody looking at the table sees it: dates and outcomes, no notes and no
     * roster.
     *
     * <p>It has no endpoint of its own. It travels inside the table's detail, which is what already
     * decides whether the reader may see the table at all - a vetoed one gets a {@code 404} there
     * (#29) - so the calendar inherits that answer instead of repeating the check somewhere it could
     * drift out of step.
     *
     * @param table the table
     * @return its sessions, first to last. Empty until the table opens; while it is paused, only
     *         what already happened (#32, #33)
     */
    @Transactional(readOnly = true)
    public List<PublicSessionResponse> findPublicSessions(GameTable table) {
        return visibleSessionsOf(table).stream().map(gameTableMapper::toPublicSessionResponse).toList();
    }

    /**
     * A player's own calendar and their own attendance, for {@code /my/tables/:id}.
     *
     * <p>Both are about the actor of the token and there is no parameter that could name anybody
     * else, which is what makes them impossible to ask for on somebody's behalf (#121).
     *
     * @param gameTableId the table
     * @param actorId     the actor, from the token
     * @return their sessions and the three numbers of #137
     * @throws NotFoundException        if the table does not exist
     * @throws ForbiddenActionException if the actor does not play at the table
     */
    @Transactional(readOnly = true)
    public MySessionsResponse listMine(String gameTableId, String actorId) {
        GameTable table = getTable(gameTableId);
        if (!registrationRepository.existsByGameTable_IdAndUser_IdAndStatusIn(
                gameTableId, actorId, List.of(TableRegistrationStatus.Player))) {
            throw new ForbiddenActionException("Only a player of this table can see their sessions");
        }

        List<TableSession> sessions = visibleSessionsOf(table);
        Map<String, AttendanceStatus> mine = new HashMap<>();
        for (SessionAttendance row : attendanceRepository.findByTableAndUser(gameTableId, actorId)) {
            mine.put(row.getId().tableSessionId(), row.getAttendance());
        }

        List<PlayerSessionResponse> responses = sessions.stream()
                .map(session -> gameTableMapper.toPlayerSessionResponse(
                        session, mine.getOrDefault(session.getId(), AttendanceStatus.Unknown)))
                .toList();
        return new MySessionsResponse(responses, summarize(gameTableId, actorId));
    }

    /**
     * A master correcting one session: its date, its notes, or both.
     *
     * <p>Only a session that has not happened yet can be corrected. Once it is {@code Held} or
     * {@code Cancelled} the row is a record of something that did or did not happen, and moving it
     * would rewrite that.
     *
     * <p>Moving a date tells the players (#77's principle applied to sessions): a calendar that
     * changes silently is a calendar nobody can rely on.
     *
     * @param sessionId the session
     * @param request   the new date and notes. Absent notes clear them (#189)
     * @param actorId   the actor, from the token; has to run the table
     * @return the session after the correction
     * @throws ForbiddenActionException if the actor does not run the table
     * @throws ConflictException        if the session was already played or called off
     */
    @Transactional
    public TableSessionResponse update(String sessionId, UpdateSessionRequest request, String actorId) {
        TableSession session = lockSession(sessionId);
        GameTable table = session.getGameTable();
        requireMasterOf(table.getId(), actorId);
        requireScheduled(session, "corrected");

        LocalDateTime newDate = request.scheduledAt();
        boolean dateMoved = newDate != null && !newDate.isEqual(session.getScheduledAt());
        if (newDate != null) {
            session.setScheduledAt(newDate);
        }
        session.setNotes(request.notes());

        if (dateMoved) {
            notifyPlayers(table, true);
        }
        return toResponse(table, session);
    }

    /**
     * A master declaring that a session was played (#195).
     *
     * <p>It is a separate action from recording attendance on purpose: "we played" and "these people
     * came" are two facts, and inferring one from the other would let a master who fills in a roster
     * for a session that got called off silently mark it as held.
     *
     * @param sessionId the session
     * @param actorId   the actor, from the token; has to run the table
     * @return the session, now held
     * @throws ForbiddenActionException if the actor does not run the table
     * @throws ConflictException        if the session was already played or called off
     */
    @Transactional
    public TableSessionResponse hold(String sessionId, String actorId) {
        TableSession session = lockSession(sessionId);
        requireMasterOf(session.getGameTable().getId(), actorId);
        requireScheduled(session, "held");

        session.setStatus(TableSessionStatus.Held);
        return toResponse(session.getGameTable(), session);
    }

    /**
     * A master calling off one session - <b>and the table getting it back at the end</b> (#194).
     *
     * <p>The cancelled row stays with its number, as the record that the evening was planned and did
     * not happen, and a new session is added on the first agenda slot after the last one still
     * standing. The table plays the number of sessions it promised; what moved is when it finishes.
     *
     * <p>A table with no agenda left gets no replacement: there is no slot to put it on, and a
     * made-up date would be worse than a shorter run the master can see.
     *
     * @param sessionId the session to call off
     * @param actorId   the actor, from the token; has to run the table
     * @return the whole calendar afterwards - the cancellation and its replacement are one change,
     *         and the screen re-renders the list
     * @throws ForbiddenActionException if the actor does not run the table
     * @throws ConflictException        if the session was already played or called off
     */
    @Transactional
    public List<TableSessionResponse> cancel(String sessionId, String actorId) {
        TableSession session = lockSession(sessionId);
        GameTable table = session.getGameTable();
        requireMasterOf(table.getId(), actorId);
        requireScheduled(session, "cancelled");

        session.setStatus(TableSessionStatus.Cancelled);
        addReplacementSession(table);
        notifyPlayers(table, false);

        return toResponses(table, visibleSessionsOf(table));
    }

    /**
     * A master recording who was at a session (#36).
     *
     * <p>The roster is the table's active players, resolved here: a {@code userId} that does not play
     * at this table is a {@code 400} and never a silently created row. Recording does <b>not</b> mark
     * the session as played - that is {@link #hold} and a separate decision (#195).
     *
     * @param sessionId the session
     * @param request   one line per player being recorded; a player left out is left alone
     * @param actorId   the actor, from the token; has to run the table
     * @return the session with its roster after the change
     * @throws ForbiddenActionException if the actor does not run the table
     * @throws ConflictException        if the session was called off - nobody attends what did not happen
     * @throws InvalidRequestException  if a line names somebody who does not play at this table
     */
    @Transactional
    public TableSessionResponse recordAttendance(String sessionId, RecordAttendanceRequest request, String actorId) {
        TableSession session = lockSession(sessionId);
        GameTable table = session.getGameTable();
        requireMasterOf(table.getId(), actorId);
        if (session.getStatus() == TableSessionStatus.Cancelled) {
            throw new ConflictException("Cannot record attendance for a session that was cancelled");
        }

        Map<String, User> players = activePlayersOf(table.getId());
        for (AttendanceEntryRequest entry : request.attendance()) {
            User player = players.get(entry.userId());
            if (player == null) {
                throw new InvalidRequestException("User " + entry.userId() + " is not a player of this table");
            }
            SessionAttendance row = attendanceRepository
                    .findById(new SessionAttendanceId(session.getId(), player.getId()))
                    .orElseGet(() -> new SessionAttendance(session, player, entry.attendance()));
            row.setAttendance(entry.attendance());
            attendanceRepository.save(row);
        }
        return toResponse(table, session);
    }

    /**
     * Somebody's historical attendance on one table, as the three numbers of #137.
     *
     * <p>Derived with a {@code GROUP BY} and never cached, following the precedent of #11: a
     * maintained counter is a class of inconsistency, not a saving. {@code Unknown} is out of the
     * denominator, which is what keeps a table that started yesterday from making everybody look
     * like an absentee.
     *
     * @param gameTableId the table the count is scoped to
     * @param userId      the person, always the actor from the token (#121)
     * @return their present, excused and absent counts, and the denominator they share
     */
    @Transactional(readOnly = true)
    public AttendanceSummaryResponse summarize(String gameTableId, String userId) {
        int present = 0;
        int excused = 0;
        int absent = 0;
        for (AttendanceCount count : attendanceRepository.countByTableAndUser(gameTableId, userId)) {
            switch (count.attendance()) {
                case Present -> present = (int) count.total();
                case Excused -> excused = (int) count.total();
                case Absent -> absent = (int) count.total();
                // Unknown never comes back: the query leaves it out, which is what makes the
                // denominator below the count of sessions with something actually recorded (#137).
                case Unknown -> { }
            }
        }
        return new AttendanceSummaryResponse(present, excused, absent, present + excused + absent);
    }

    /**
     * The instants an agenda falls on, starting from a moment in time.
     *
     * <p>The whole of the calendar's arithmetic, and the one place the weekly wrap of #22 is handled:
     * a Tuesday 23:00 table lasting three hours ends on Wednesday, and the community plays at night
     * in America, which is the small hours of the next day in UTC. Written against
     * {@code java.time} directly - there is no calendar arithmetic here beyond "the same weekday next
     * week", the same reason {@code lib/date.ts} has no date library.
     *
     * @param from      where to start looking, in UTC
     * @param agenda    the weekly slots, already in week order
     * @param count     how many instants are wanted
     * @param inclusive whether an instant exactly equal to {@code from} counts
     * @return {@code count} instants in chronological order, or fewer if the agenda is empty
     */
    private static List<LocalDateTime> occurrencesFrom(
            LocalDateTime from, List<TableScheduleEntry> agenda, int count, boolean inclusive) {
        List<LocalDateTime> instants = new ArrayList<>();
        if (agenda.isEmpty() || count <= 0) {
            return instants;
        }

        // The Monday of the week `from` falls in: every slot is an offset from there, so a week is
        // one addition and the day never has to be reasoned about twice.
        LocalDateTime weekStart = from.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
        // At worst the whole first week is behind `from`, so count+1 weeks always yield count slots.
        for (int week = 0; week <= count && instants.size() < count; week++) {
            for (TableScheduleEntry slot : agenda) {
                LocalDateTime candidate = weekStart
                        .plusWeeks(week)
                        .plusDays(slot.weekday().ordinalFromMonday())
                        .plusHours(slot.hourtime().getHour())
                        .plusMinutes(slot.hourtime().getMinute());
                if (candidate.isBefore(from) || (!inclusive && candidate.isEqual(from))) {
                    continue;
                }
                instants.add(candidate);
                if (instants.size() == count) {
                    break;
                }
            }
        }
        return instants;
    }

    /** Adds the session that replaces a cancelled one, at the end of the run (#194). */
    private void addReplacementSession(GameTable table) {
        List<TableScheduleEntry> agenda = tableScheduleService.findByTable(table.getId());
        if (agenda.isEmpty()) {
            return;
        }

        // Anchored on the last session that still stands, so the replacement lands after the run and
        // not in the middle of it. With every session cancelled there is no run left to append to and
        // the anchor is now.
        LocalDateTime anchor = sessionRepository.findByGameTable_IdOrderBySequenceNumberAsc(table.getId()).stream()
                .filter(session -> session.getStatus() != TableSessionStatus.Cancelled)
                .map(TableSession::getScheduledAt)
                .max(Comparator.naturalOrder())
                .orElseGet(LocalDateTime::now);

        List<LocalDateTime> next = occurrencesFrom(anchor, agenda, 1, false);
        if (next.isEmpty()) {
            return;
        }
        Integer maxSequence = sessionRepository.findMaxSequenceNumber(table.getId());
        sessionRepository.save(new TableSession(table, (maxSequence == null ? 0 : maxSequence) + 1, next.getFirst()));
    }

    /**
     * The sessions a table shows right now.
     *
     * <p>The one place the pause of #32 and #33 is applied: while a table is paused it promises no
     * dates, so the pending sessions are not shown - to its master either. What was played and what
     * was called off stay visible, because those already happened.
     */
    private List<TableSession> visibleSessionsOf(GameTable table) {
        List<TableSession> sessions = sessionRepository.findByGameTable_IdOrderBySequenceNumberAsc(table.getId());
        if (table.getStatus() != GameTableStatus.Pause) {
            return sessions;
        }
        return sessions.stream().filter(session -> session.getStatus() != TableSessionStatus.Scheduled).toList();
    }

    /** The table's active players, by id - the roster, and the only ids attendance may name. */
    private Map<String, User> activePlayersOf(String gameTableId) {
        Map<String, User> players = new LinkedHashMap<>();
        for (TableRegistration registration :
                registrationRepository.findByGameTable_IdAndStatusOrderByCreatedAtAsc(gameTableId, TableRegistrationStatus.Player)) {
            players.put(registration.getUser().getId(), registration.getUser());
        }
        return players;
    }

    /** One session with its roster. Used by the mutations, which answer with the row they changed. */
    private TableSessionResponse toResponse(GameTable table, TableSession session) {
        return toResponses(table, List.of(session)).getFirst();
    }

    /**
     * A calendar with every roster filled in, in two reads: the players once and the attendance rows
     * once. Asking per session would be the N+1 that #137 names as the real risk.
     */
    private List<TableSessionResponse> toResponses(GameTable table, List<TableSession> sessions) {
        if (sessions.isEmpty()) {
            return List.of();
        }
        Map<String, User> players = activePlayersOf(table.getId());
        List<String> sessionIds = sessions.stream().map(TableSession::getId).toList();

        Map<String, Map<String, AttendanceStatus>> recorded = new HashMap<>();
        for (SessionAttendance row : attendanceRepository.findById_TableSessionIdIn(sessionIds)) {
            recorded
                    .computeIfAbsent(row.getId().tableSessionId(), id -> new HashMap<>())
                    .put(row.getId().userId(), row.getAttendance());
        }

        return sessions.stream()
                .map(session -> {
                    Map<String, AttendanceStatus> forSession = recorded.getOrDefault(session.getId(), Map.of());
                    List<SessionAttendanceEntry> roster = players.values().stream()
                            .map(player -> gameTableMapper.toAttendanceEntry(
                                    player, forSession.getOrDefault(player.getId(), AttendanceStatus.Unknown)))
                            .toList();
                    return gameTableMapper.toSessionResponse(session, roster);
                })
                .toList();
    }

    /** Tells everybody signed up that the calendar moved. Nobody is removed and nothing is refused (#70). */
    private void notifyPlayers(GameTable table, boolean scheduled) {
        for (TableRegistration registration : registrationRepository.findByGameTable_Id(table.getId())) {
            if (!NOTIFIED_STATUSES.contains(registration.getStatus())) {
                continue;
            }
            String userId = registration.getUser().getId();
            if (scheduled) {
                notificationService.notifySessionScheduled(userId, table);
            } else {
                notificationService.notifySessionCanceled(userId, table);
            }
        }
    }

    private GameTable getTable(String gameTableId) {
        GameTable table =
                gameTableRepository.findById(gameTableId).orElseThrow(() -> new NotFoundException("Table not found: " + gameTableId));
        if (table.getStatus() == GameTableStatus.Deleted) {
            throw new NotFoundException("Table not found: " + gameTableId);
        }
        return table;
    }

    private TableSession lockSession(String sessionId) {
        return sessionRepository.findByIdForUpdate(sessionId).orElseThrow(() -> new NotFoundException("Session not found: " + sessionId));
    }

    private void requireMasterOf(String gameTableId, String actorId) {
        if (!masterService.isMasterOf(gameTableId, actorId)) {
            throw new ForbiddenActionException("Only a master of this table can manage its sessions");
        }
    }

    /** The one precondition the three mutations share: a session that already happened is a record, not a plan. */
    private void requireScheduled(TableSession session, String action) {
        if (session.getStatus() != TableSessionStatus.Scheduled) {
            throw new ConflictException("A session in status " + session.getStatus() + " cannot be " + action);
        }
    }
}
