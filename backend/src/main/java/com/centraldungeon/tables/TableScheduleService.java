package com.centraldungeon.tables;

import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.exception.InvalidRequestException;
import com.centraldungeon.notifications.NotificationService;
import com.centraldungeon.registrations.TableRegistration;
import com.centraldungeon.registrations.TableRegistrationRepository;
import com.centraldungeon.registrations.TableRegistrationStatus;
import com.centraldungeon.tables.dto.TableScheduleEntry;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A table's weekly agenda: reading it, and replacing it as a whole.
 *
 * <p><b>Replace, not add and remove one slot at a time.</b> The agenda is edited as a unit on
 * screen - a master looks at the week and says what it is - and the clash rules of #178 are about
 * the whole agenda anyway: checking a slot in isolation would let a pair that is fine one at a time
 * and broken together through the door. So the caller sends the week it wants and this decides what
 * that means for the rows.
 *
 * <p>Two rules apply in order, and the order matters. First the agenda has to make sense on its own:
 * two slots of the <b>same</b> table whose sessions overlap are a {@code 400}, because that is input
 * that does not describe a playable week (#178). Only then is it compared against the rest of the
 * master's life, which is R1 and a {@code 409}.
 *
 * <p>Editing the agenda of a table that already has players <b>warns and does not expel</b> (#70,
 * #178): the people it now clashes with are notified, and what to do about it is theirs and their
 * master's to sort out. The system says what happened; it does not decide for them.
 */
@Service
public class TableScheduleService {

    /** How a week reads: by day first, then by time. The order every agenda is handed out in. */
    private static final Comparator<TableScheduleEntry> WEEK_ORDER = Comparator
            .comparingInt((TableScheduleEntry entry) -> entry.weekday().ordinalFromMonday())
            .thenComparing(TableScheduleEntry::hourtime);

    /** The players a schedule change can affect - the ones already in, and the ones still waiting. */
    private static final List<TableRegistrationStatus> AFFECTED_STATUSES =
            List.of(TableRegistrationStatus.Candidate, TableRegistrationStatus.Player);

    /** The {@code table_schedules} rows this service owns. */
    private final TableScheduleRepository scheduleRepository;

    /** Answers R1, and tells the service who a new agenda now clashes with. */
    private final ScheduleConflictService scheduleConflictService;

    /** Who is signed up to the table whose agenda is changing. */
    private final TableRegistrationRepository registrationRepository;

    /** Tells the affected people their commitments now overlap (#178, R4's sibling). */
    private final NotificationService notificationService;

    /**
     * @param scheduleRepository      the {@code table_schedules} rows
     * @param scheduleConflictService answers whether the new agenda clashes with anything
     * @param registrationRepository  who is signed up to the table
     * @param notificationService     warns the people a schedule change now clashes for
     */
    public TableScheduleService(
            TableScheduleRepository scheduleRepository,
            ScheduleConflictService scheduleConflictService,
            TableRegistrationRepository registrationRepository,
            NotificationService notificationService) {
        this.scheduleRepository = scheduleRepository;
        this.scheduleConflictService = scheduleConflictService;
        this.registrationRepository = registrationRepository;
        this.notificationService = notificationService;
    }

    /**
     * A table's agenda as it stands, in UTC and in reading order.
     *
     * @param gameTableId the table
     * @return its live slots, ordered by day and then by time - which is how a week reads
     */
    @Transactional(readOnly = true)
    public List<TableScheduleEntry> findByTable(String gameTableId) {
        return scheduleRepository.findById_GameTableIdAndStatus(gameTableId, TableScheduleStatus.Created).stream()
                .map(slot -> new TableScheduleEntry(slot.getWeekday(), slot.getHourtime()))
                .sorted(WEEK_ORDER)
                .toList();
    }

    /**
     * The agendas of a whole page of tables, in one read.
     *
     * <p>The explorer shows every card's agenda, and asking per card would be twenty queries for one
     * screen. A table with no agenda is absent from the map rather than mapped to an empty list -
     * callers ask for it with a default.
     *
     * @param gameTableIds the tables on the page
     * @return their live agendas, keyed by table and ordered as a week reads
     */
    @Transactional(readOnly = true)
    public Map<String, List<TableScheduleEntry>> findByTables(Collection<String> gameTableIds) {
        if (gameTableIds.isEmpty()) {
            return Map.of();
        }
        Map<String, List<TableScheduleEntry>> byTable = new LinkedHashMap<>();
        for (TableSchedule slot : scheduleRepository.findById_GameTableIdInAndStatus(gameTableIds, TableScheduleStatus.Created)) {
            byTable
                    .computeIfAbsent(slot.getId().gameTableId(), id -> new ArrayList<>())
                    .add(new TableScheduleEntry(slot.getWeekday(), slot.getHourtime()));
        }
        byTable.values().forEach(entries -> entries.sort(WEEK_ORDER));
        return byTable;
    }

    /**
     * Replaces a table's whole agenda with the one given, after checking it holds up.
     *
     * <p>Slots that leave the agenda are marked, never dropped: the primary key is
     * {@code (table, weekday, hourtime)}, so a master who removes Tuesday 20:00 and puts it back
     * would otherwise collide with the row they just deleted (see {@link TableScheduleStatus}).
     *
     * @param table    the table whose agenda is being set. Its {@code duration} is what turns a slot
     *                 into an interval, so a table without one can hold an agenda and still never
     *                 clash with anything (#178)
     * @param entries  the week the master wants. Empty clears the agenda
     * @param actorId  the master, from the token. Their <em>other</em> commitments are what R1
     *                 compares against, so it is theirs and never an id from the URL (#121). Null
     *                 when the table has no master yet - an admin creating an Unassigned one (#72) -
     *                 in which case only the agenda's own coherence is checked and R1 is deferred to
     *                 the moment masters are assigned
     * @throws InvalidRequestException if two of the given slots overlap each other (#178)
     * @throws ConflictException       if the agenda overlaps another live table the actor runs or
     *                                 plays at - R1 of #178
     */
    @Transactional
    public void replace(GameTable table, List<TableScheduleEntry> entries, @Nullable String actorId) {
        List<TableScheduleEntry> normalized = normalize(entries);
        List<WeeklyInterval> intervals = intervalsOf(normalized, table.getDuration());

        if (scheduleConflictService.hasSelfOverlap(intervals)) {
            throw new InvalidRequestException("Two slots of this table's agenda overlap each other");
        }
        if (actorId != null) {
            CommittedTable clash = scheduleConflictService.findClash(actorId, table.getId(), intervals);
            if (clash != null) {
                // English, and for a log: the sentence the master reads is rendered by the
                // frontend from the code and the table name below, in their language (#197).
                throw new ConflictException(
                        "Agenda overlaps table " + clash.name() + ", which the actor is already committed to",
                        ConflictException.SCHEDULE_CONFLICT,
                        Map.of(ConflictException.PARAM_OTHER_TABLE_NAME, clash.name()));
            }
        }

        applyTo(table.getId(), normalized);
        warnAffectedPeople(table);
    }

    /**
     * Turns the requested week into rows: everything asked for becomes live, everything else that
     * existed is marked as removed, and a slot that comes back is revived rather than re-inserted.
     */
    private void applyTo(String gameTableId, List<TableScheduleEntry> entries) {
        Map<TableScheduleId, TableSchedule> existing = new LinkedHashMap<>();
        for (TableSchedule slot : scheduleRepository.findById_GameTableId(gameTableId)) {
            existing.put(slot.getId(), slot);
        }

        LocalDateTime now = LocalDateTime.now();
        for (TableScheduleEntry entry : entries) {
            TableScheduleId id = new TableScheduleId(gameTableId, entry.weekday(), entry.hourtime());
            TableSchedule slot = existing.remove(id);
            if (slot == null) {
                scheduleRepository.save(new TableSchedule(gameTableId, entry.weekday(), entry.hourtime()));
            } else {
                slot.setStatus(TableScheduleStatus.Created);
                slot.setDeletedAt(null);
            }
        }
        for (TableSchedule leftover : existing.values()) {
            if (leftover.getStatus() == TableScheduleStatus.Created) {
                leftover.setStatus(TableScheduleStatus.Deleted);
                leftover.setDeletedAt(now);
            }
        }
    }

    /**
     * Tells the people signed up to the table whose own commitments the new agenda now clashes with.
     * Nobody is removed and nothing is refused: the notice is the whole of the action (#70, #178).
     */
    private void warnAffectedPeople(GameTable table) {
        for (TableRegistration registration : registrationRepository.findByGameTable_Id(table.getId())) {
            if (!AFFECTED_STATUSES.contains(registration.getStatus())) {
                continue;
            }
            String userId = registration.getUser().getId();
            CommittedTable clash = scheduleConflictService.findClashWith(userId, table);
            if (clash != null) {
                notificationService.notifyScheduleConflict(userId, table, clash.name());
            }
        }
    }

    /**
     * Drops the seconds and removes duplicates, so what is compared and what is stored are the same
     * thing. The column is part of the primary key, and 20:00:00 and 20:00:30 are one slot as far as
     * an agenda written in minutes is concerned.
     */
    private List<TableScheduleEntry> normalize(List<TableScheduleEntry> entries) {
        return entries.stream()
                .map(entry -> new TableScheduleEntry(entry.weekday(), entry.hourtime().truncatedTo(ChronoUnit.MINUTES)))
                .distinct()
                .toList();
    }

    /** The stretches of the week the requested agenda would occupy. Empty when the table has no duration. */
    private List<WeeklyInterval> intervalsOf(List<TableScheduleEntry> entries, @Nullable LocalTime duration) {
        if (duration == null) {
            return List.of();
        }
        return entries.stream().map(entry -> WeeklyInterval.of(entry.weekday(), entry.hourtime(), duration)).toList();
    }
}
