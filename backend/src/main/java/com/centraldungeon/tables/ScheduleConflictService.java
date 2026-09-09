package com.centraldungeon.tables;

import com.centraldungeon.registrations.TableRegistrationRepository;
import com.centraldungeon.tables.dto.WeeklyBlockResponse;
import com.centraldungeon.tables.dto.WeeklyCommitmentResponse;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Whether a stretch of the week is already taken for somebody. The whole of #178 is decided here,
 * and the four rules that decision names are enforced by the two services that call this one:
 *
 * <ul>
 *   <li><b>R1</b> - a master cannot run two live tables with overlapping agendas. {@code
 *       TableScheduleService} asks before saving an agenda.
 *   <li><b>R2</b> - nobody can apply to a table clashing with one they already play at. {@code
 *       RegistrationService} asks before creating the application.
 *   <li><b>R3</b> - a master cannot accept a candidate who by now plays at a clashing table. The
 *       same question, asked again at accept time, because the answer can have changed in between.
 *   <li><b>R4</b> - accepting somebody <em>notifies</em> them about their other pending
 *       applications that now clash. It does not reject them: until they are accepted somewhere
 *       there is no commitment, and choosing is theirs (#70, #178).
 * </ul>
 *
 * <p><b>Running and playing weigh the same.</b> A person's commitments are their tables as a master
 * and their tables as a player, because they are one person and cannot be in two places at once
 * (#178).
 *
 * <p><b>{@code Pause} does not reserve a slot.</b> A paused table freezes its agenda and its
 * sessions do not happen (#32), so it is left out of {@link #COMMITTING_STATUSES} - and re-checked
 * when the table resumes, which is F1.3's job.
 *
 * <p>A table with no {@code duration} or no live slot never clashes with anything: there is no
 * interval to compare. That is a deliberate answer and not an oversight - a draft that has not said
 * when it plays has not committed anybody to anything.
 */
@Service
public class ScheduleConflictService {

    /**
     * The table statuses in which a slot is actually claimed.
     *
     * <p>Drafts count: two drafts of the same master with the same agenda are a clash the moment
     * they exist, and letting both through only to have an admin approve both would move the problem
     * to a place where nobody is looking for it. {@code Pause} does not count (#32, #178), and
     * neither does anything past the end of a table's life.
     */
    private static final List<GameTableStatus> COMMITTING_STATUSES = List.of(
            GameTableStatus.Preparation,
            GameTableStatus.ChangesRequested,
            GameTableStatus.Opened,
            GameTableStatus.InProgress,
            GameTableStatus.PauseRequested);

    /** The agendas being compared. */
    private final TableScheduleRepository scheduleRepository;

    /** The tables somebody runs - one half of their commitments (#178). */
    private final GameTableRepository gameTableRepository;

    /** The tables somebody plays at - the other half. */
    private final TableRegistrationRepository registrationRepository;

    /**
     * @param scheduleRepository     the weekly agendas being compared
     * @param gameTableRepository    resolves the tables the person runs
     * @param registrationRepository resolves the tables the person plays at
     */
    public ScheduleConflictService(
            TableScheduleRepository scheduleRepository,
            GameTableRepository gameTableRepository,
            TableRegistrationRepository registrationRepository) {
        this.scheduleRepository = scheduleRepository;
        this.gameTableRepository = gameTableRepository;
        this.registrationRepository = registrationRepository;
    }

    /**
     * The stretches of the week a table occupies, from its live agenda and the length of one of its
     * sessions.
     *
     * @param table the table to measure
     * @return one interval per live slot, or an empty list when the table has no duration or no
     *         agenda - such a table never clashes
     */
    @Transactional(readOnly = true)
    public List<WeeklyInterval> intervalsOf(GameTable table) {
        LocalTime duration = table.getDuration();
        if (duration == null) {
            return List.of();
        }
        return scheduleRepository.findById_GameTableIdAndStatus(table.getId(), TableScheduleStatus.Created).stream()
                .map(slot -> WeeklyInterval.of(slot.getWeekday(), slot.getHourtime(), duration))
                .toList();
    }

    /**
     * The first table the person is already committed to that overlaps the given stretches. The
     * question behind R1, R2 and R3.
     *
     * @param userId         whose commitments to check, always from the token (#121)
     * @param excludeTableId the table being edited, left out so a table never clashes with itself.
     *                       Null when what is being checked does not exist yet
     * @param intervals      the stretches being claimed. An empty list never clashes
     * @return the clashing table, named so the caller can say which one it was, or null when the
     *         person is free
     */
    @Transactional(readOnly = true)
    public @Nullable CommittedTable findClash(String userId, @Nullable String excludeTableId, List<WeeklyInterval> intervals) {
        if (intervals.isEmpty()) {
            return null;
        }
        for (Map.Entry<CommittedTable, List<WeeklyInterval>> commitment : commitmentsOf(userId, excludeTableId).entrySet()) {
            if (anyOverlap(intervals, commitment.getValue())) {
                return commitment.getKey();
            }
        }
        return null;
    }

    /**
     * The same question as {@link #findClash}, asked about a table that already exists: does what
     * this table plays overlap what the person is already committed to?
     *
     * @param userId whose commitments to check, always from the token (#121)
     * @param table  the table being applied to or accepted into
     * @return the clashing table, or null when the person is free
     */
    @Transactional(readOnly = true)
    public @Nullable CommittedTable findClashWith(String userId, GameTable table) {
        return findClash(userId, table.getId(), intervalsOf(table));
    }

    /**
     * Which of a page of tables clash with what the person is already committed to - the derived
     * field the explorer's cards show as a warning (#178).
     *
     * <p>It is computed <b>for the actor of the token and for nobody else</b> (#121): there is no
     * parameter here that could name another person, which is what makes the field impossible to ask
     * for on somebody else's behalf.
     *
     * <p>One pass, not one query per card: the person's commitments are loaded once and the page's
     * agendas in a single read.
     *
     * @param userId the actor, from the token
     * @param tables the page of tables being listed
     * @return the ids of the ones that clash. Tables with no agenda or no duration are never in it
     */
    @Transactional(readOnly = true)
    public Set<String> clashingAmong(String userId, List<GameTable> tables) {
        if (tables.isEmpty()) {
            return Set.of();
        }
        Collection<List<WeeklyInterval>> commitments = commitmentsOf(userId, null).values();
        if (commitments.isEmpty()) {
            return Set.of();
        }

        Map<String, List<WeeklyInterval>> byTable = intervalsByTable(tables);
        Set<String> clashing = new HashSet<>();
        for (Map.Entry<String, List<WeeklyInterval>> candidate : byTable.entrySet()) {
            for (List<WeeklyInterval> committed : commitments) {
                if (anyOverlap(candidate.getValue(), committed)) {
                    clashing.add(candidate.getKey());
                    break;
                }
            }
        }
        return clashing;
    }

    /**
     * Whether two tables' agendas overlap. What R4 asks about each of a freshly accepted player's
     * other pending applications.
     *
     * @param first  one table
     * @param second the other
     * @return true when the two share any stretch of the week
     */
    @Transactional(readOnly = true)
    public boolean overlap(GameTable first, GameTable second) {
        return anyOverlap(intervalsOf(first), intervalsOf(second));
    }

    /**
     * Whether two sets of slots of the <b>same</b> table overlap each other - the {@code 400} of
     * #178, checked before anything is saved.
     *
     * <p>It is a different failure from a clash with another table: nobody else is involved, the
     * agenda simply does not describe a week that can be played, so it is malformed input rather
     * than a conflict with the world.
     *
     * @param intervals the slots being saved, already turned into intervals
     * @return true when at least two of them overlap
     */
    public boolean hasSelfOverlap(List<WeeklyInterval> intervals) {
        for (int i = 0; i < intervals.size(); i++) {
            for (int j = i + 1; j < intervals.size(); j++) {
                if (intervals.get(i).overlaps(intervals.get(j))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The reader's whole week, as tables and the stretches they occupy (#227).
     *
     * <p>The same commitments {@link #findClash} measures, read instead of enforced. That is the
     * point: what the grid draws is what the rule compares, so a gap on screen is a gap the server
     * will accept. Building it from a second query would let the two drift, and the first anybody
     * would notice is a refusal over a slot that looked free.
     *
     * <p><b>It answers for the actor and nobody else</b> (#121): the id comes from the token, and
     * there is no parameter here that could name another person - somebody else's week is not
     * public, and #44's visibility rules would have to be answered first.
     *
     * <p>A table with no agenda or no duration comes back with <b>no blocks</b> rather than being
     * dropped: it is still something the reader is committed to, and a screen that silently omitted
     * it would be lying about what they have on.
     *
     * @param userId whose week, always from the token
     * @return every table they run or play at that claims a slot, each with what it occupies
     */
    @Transactional(readOnly = true)
    public List<WeeklyCommitmentResponse> weekOf(String userId) {
        Map<String, GameTable> mastered = byId(gameTableRepository.findMasteredByUserInStatuses(userId, COMMITTING_STATUSES));
        Map<String, GameTable> played = byId(registrationRepository.findTablesPlayedByUserInStatuses(userId, COMMITTING_STATUSES));

        Map<String, GameTable> all = new LinkedHashMap<>(mastered);
        all.putAll(played);
        if (all.isEmpty()) {
            return List.of();
        }

        Map<String, List<WeeklyInterval>> byTable = intervalsByTable(all.values());
        return all.values().stream()
                .map(table -> new WeeklyCommitmentResponse(
                        table.getId(),
                        table.getName(),
                        // Running wins over playing when somebody is both: it is the stronger claim.
                        mastered.containsKey(table.getId()) ? "Master" : "Player",
                        table.getStatus().name(),
                        byTable.getOrDefault(table.getId(), List.of()).stream()
                                .map(interval -> new WeeklyBlockResponse(interval.startMinute(), interval.durationMinutes()))
                                .toList()))
                .toList();
    }

    /** Indexes tables by id, keeping the order the query returned them in. */
    private static Map<String, GameTable> byId(List<GameTable> tables) {
        Map<String, GameTable> indexed = new LinkedHashMap<>();
        for (GameTable table : tables) {
            indexed.put(table.getId(), table);
        }
        return indexed;
    }

    /**
     * Everything the person is committed to, as tables and the intervals they occupy: the ones they
     * run and the ones they play at, in the statuses that actually claim a slot.
     *
     * <p>Kept as a map rather than a flat list of intervals because the answer has to name the table
     * - see {@link CommittedTable}.
     */
    private Map<CommittedTable, List<WeeklyInterval>> commitmentsOf(String userId, @Nullable String excludeTableId) {
        Map<String, GameTable> tables = new LinkedHashMap<>();
        for (GameTable table : gameTableRepository.findMasteredByUserInStatuses(userId, COMMITTING_STATUSES)) {
            tables.put(table.getId(), table);
        }
        for (GameTable table : registrationRepository.findTablesPlayedByUserInStatuses(userId, COMMITTING_STATUSES)) {
            tables.put(table.getId(), table);
        }
        if (excludeTableId != null) {
            tables.remove(excludeTableId);
        }
        if (tables.isEmpty()) {
            return Map.of();
        }

        Map<String, List<WeeklyInterval>> byTable = intervalsByTable(tables.values());
        Map<CommittedTable, List<WeeklyInterval>> commitments = new LinkedHashMap<>();
        byTable.forEach((tableId, intervals) -> {
            GameTable table = tables.get(tableId);
            commitments.put(new CommittedTable(table.getId(), table.getName()), intervals);
        });
        return commitments;
    }

    /** The agendas of several tables in one read, so a page of cards costs one query and not twenty. */
    private Map<String, List<WeeklyInterval>> intervalsByTable(Collection<GameTable> tables) {
        Map<String, GameTable> byId = new LinkedHashMap<>();
        for (GameTable table : tables) {
            if (table.getDuration() != null) {
                byId.put(table.getId(), table);
            }
        }
        if (byId.isEmpty()) {
            return Map.of();
        }

        Map<String, List<WeeklyInterval>> intervals = new HashMap<>();
        for (TableSchedule slot : scheduleRepository.findById_GameTableIdInAndStatus(byId.keySet(), TableScheduleStatus.Created)) {
            GameTable table = byId.get(slot.getId().gameTableId());
            LocalTime duration = table.getDuration();
            if (duration == null) {
                continue;
            }
            intervals
                    .computeIfAbsent(table.getId(), id -> new ArrayList<>())
                    .add(WeeklyInterval.of(slot.getWeekday(), slot.getHourtime(), duration));
        }
        return intervals;
    }

    /** Whether any interval of one side overlaps any interval of the other. */
    private boolean anyOverlap(List<WeeklyInterval> left, List<WeeklyInterval> right) {
        for (WeeklyInterval one : left) {
            for (WeeklyInterval other : right) {
                if (one.overlaps(other)) {
                    return true;
                }
            }
        }
        return false;
    }
}
