package com.centraldungeon.dashboard;

import com.centraldungeon.dashboard.dto.MasterDashboardResponse;
import com.centraldungeon.dashboard.dto.MasterWorkItem;
import com.centraldungeon.registrations.PendingCandidateCount;
import com.centraldungeon.registrations.TableRegistrationRepository;
import com.centraldungeon.registrations.TableRegistrationStatus;
import com.centraldungeon.tables.GameTable;
import com.centraldungeon.tables.GameTableStatus;
import com.centraldungeon.tables.Master;
import com.centraldungeon.tables.MasterService;
import com.centraldungeon.tables.TableSessionRepository;
import com.centraldungeon.tables.TableSessionStatus;
import com.centraldungeon.tables.UnrecordedSessionCount;
import com.centraldungeon.tasks.TableTask;
import com.centraldungeon.tasks.TableTaskRepository;
import com.centraldungeon.tasks.TableTaskService;
import com.centraldungeon.tasks.TaskStatus;
import com.centraldungeon.tasks.TaskSubmissionCount;
import com.centraldungeon.tasks.TaskSubmissionRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The master's tray: everything waiting for an answer, across every table they run (#136).
 *
 * <p><b>A tray, not a summary with numbers.</b> A master with three tables does not need to be told
 * how many candidates they have, they need to know <em>who they owe an answer to</em>. Every item
 * here is something that can be acted on; a figure that does not change what to do next is not
 * reported at all.
 *
 * <p>It lives in its own package because it reads from four features at once - tables, sessions,
 * registrations and tasks - and nothing reads from it. Putting it inside {@code tables/} would have
 * made that package depend on {@code tasks/}, which already depends on it.
 *
 * <p><b>There is no reservation</b>, unlike the admin tray of #100: the work of a table has one
 * owner and nobody competes for it.
 */
@Service
public class MasterDashboardService {

    /** The table states where nothing can be waiting on the master any more. */
    private static final Set<GameTableStatus> CLOSED_STATUSES =
            EnumSet.of(GameTableStatus.Finished, GameTableStatus.Canceled, GameTableStatus.Deleted);

    /** Where the tray starts: the tables the actor runs, membership and not role (#135). */
    private final MasterService masterService;

    /** The people waiting for an answer. */
    private final TableRegistrationRepository registrationRepository;

    /** The sessions whose date went by and nobody closed. */
    private final TableSessionRepository sessionRepository;

    /** The tasks whose deadline went by. */
    private final TableTaskRepository taskRepository;

    /** How many different people answered each of those tasks. */
    private final TaskSubmissionRepository submissionRepository;

    /** Resolves who a task was addressed to - the one place that rule lives (#63, #76). */
    private final TableTaskService tableTaskService;

    /**
     * @param masterService          the tables the actor runs
     * @param registrationRepository the people waiting for an answer
     * @param sessionRepository      the sessions still open past their date
     * @param taskRepository         the tasks past their deadline
     * @param submissionRepository   how many people answered each of them
     * @param tableTaskService       resolves each task's recipients
     */
    public MasterDashboardService(
            MasterService masterService,
            TableRegistrationRepository registrationRepository,
            TableSessionRepository sessionRepository,
            TableTaskRepository taskRepository,
            TaskSubmissionRepository submissionRepository,
            TableTaskService tableTaskService) {
        this.masterService = masterService;
        this.registrationRepository = registrationRepository;
        this.sessionRepository = sessionRepository;
        this.taskRepository = taskRepository;
        this.submissionRepository = submissionRepository;
        this.tableTaskService = tableTaskService;
    }

    /**
     * Builds the tray for one person.
     *
     * <p>The actor always comes from the token and never from a parameter of the URL (#121): this
     * answer is about what <em>they</em> owe, and there is no version of it about somebody else.
     *
     * @param actorId the actor, from the token
     * @return their work items, longest wait first. Empty when every table is up to date, which is
     *         a success and the screen says so (#136)
     */
    @Transactional(readOnly = true)
    public MasterDashboardResponse forMaster(String actorId) {
        List<GameTable> tables = masterService.findLiveByUser(actorId).stream()
                .map(Master::getGameTable)
                .filter(table -> !CLOSED_STATUSES.contains(table.getStatus()))
                .toList();
        if (tables.isEmpty()) {
            return new MasterDashboardResponse(List.of());
        }

        Map<String, GameTable> byId = new HashMap<>();
        for (GameTable table : tables) {
            byId.put(table.getId(), table);
        }
        LocalDateTime now = LocalDateTime.now();

        List<MasterWorkItem> items = new ArrayList<>();
        items.addAll(candidatesWaiting(byId));
        items.addAll(sessionsToRecord(byId, now));
        items.addAll(overdueTasks(byId, now));
        items.addAll(tableItself(tables, now));

        // Longest wait first: urgency here is time, not volume. Ties break by table so two items of
        // the same table stay together instead of interleaving with another's.
        items.sort(Comparator.comparing(MasterWorkItem::since).thenComparing(MasterWorkItem::tableId));
        return new MasterDashboardResponse(items);
    }

    /** People who applied and are still waiting, one item per table (#28). */
    private List<MasterWorkItem> candidatesWaiting(Map<String, GameTable> byId) {
        List<MasterWorkItem> items = new ArrayList<>();
        for (PendingCandidateCount count :
                registrationRepository.countPendingByTables(byId.keySet(), TableRegistrationStatus.Candidate)) {
            GameTable table = byId.get(count.gameTableId());
            if (table == null) {
                continue;
            }
            items.add(new MasterWorkItem(
                    table.getId(),
                    table.getName(),
                    MasterWorkItemKind.CandidatesWaiting,
                    null,
                    (int) count.pending(),
                    count.oldest()));
        }
        return items;
    }

    /** Sessions whose date went by while still open, one item per table (#195). */
    private List<MasterWorkItem> sessionsToRecord(Map<String, GameTable> byId, LocalDateTime now) {
        List<MasterWorkItem> items = new ArrayList<>();
        for (UnrecordedSessionCount count :
                sessionRepository.countUnrecordedByTables(byId.keySet(), TableSessionStatus.Scheduled, now)) {
            GameTable table = byId.get(count.gameTableId());
            if (table == null) {
                continue;
            }
            items.add(new MasterWorkItem(
                    table.getId(),
                    table.getName(),
                    MasterWorkItemKind.SessionToRecord,
                    null,
                    (int) count.unrecorded(),
                    count.oldest()));
        }
        return items;
    }

    /**
     * Tasks past their deadline that somebody did not answer, one item per task (#70).
     *
     * <p>The count is recipients minus the people who handed something in - answers accumulate
     * (#76), so three versions from one player are one person. A task everybody answered produces
     * no item: the deadline passing is not by itself something to do.
     */
    private List<MasterWorkItem> overdueTasks(Map<String, GameTable> byId, LocalDateTime now) {
        List<TableTask> overdue =
                taskRepository.findByGameTable_IdInAndStatusAndDueAtBeforeOrderByDueAtAsc(byId.keySet(), TaskStatus.Open, now);
        if (overdue.isEmpty()) {
            return List.of();
        }

        Map<String, Long> answeredBy = new HashMap<>();
        for (TaskSubmissionCount count : submissionRepository.countByTaskIds(overdue.stream().map(TableTask::getId).toList())) {
            answeredBy.put(count.taskId(), count.distinctPeople());
        }

        List<MasterWorkItem> items = new ArrayList<>();
        for (TableTask task : overdue) {
            GameTable table = byId.get(task.getGameTable().getId());
            LocalDateTime dueAt = task.getDueAt();
            if (table == null || dueAt == null) {
                continue;
            }
            // recipientsOf and not a count of registrations: who a task is addressed to depends on
            // its audience, and that rule has one home (#63, #76). The list of overdue tasks is
            // small by construction, so the query per task is not the N+1 the counts above avoid.
            int missing = tableTaskService.recipientsOf(task).size() - answeredBy.getOrDefault(task.getId(), 0L).intValue();
            if (missing <= 0) {
                continue;
            }
            items.add(new MasterWorkItem(
                    table.getId(), table.getName(), MasterWorkItemKind.OverdueTaskMissing, task.getTitle(), missing, dueAt));
        }
        return items;
    }

    /**
     * The two items that are about the table itself rather than rows inside it: a draft an admin
     * sent back, and an open table whose start date went by with nobody declaring play begun.
     */
    private List<MasterWorkItem> tableItself(List<GameTable> tables, LocalDateTime now) {
        List<MasterWorkItem> items = new ArrayList<>();
        for (GameTable table : tables) {
            if (table.getStatus() == GameTableStatus.ChangesRequested) {
                // updatedAt and not the status history: the wait restarts whenever the master
                // touches the draft, which is exactly the behaviour wanted - a table being worked
                // on should not keep climbing the tray as if it were abandoned.
                LocalDateTime since = table.getUpdatedAt() == null ? table.getCreatedAt() : table.getUpdatedAt();
                items.add(new MasterWorkItem(
                        table.getId(), table.getName(), MasterWorkItemKind.ChangesRequested, null, 1, since));
            }
            LocalDateTime startDate = table.getStartDate();
            if (table.getStatus() == GameTableStatus.Opened && startDate != null && startDate.isBefore(now)) {
                items.add(
                        new MasterWorkItem(table.getId(), table.getName(), MasterWorkItemKind.ReadyToStart, null, 1, startDate));
            }
        }
        return items;
    }
}
