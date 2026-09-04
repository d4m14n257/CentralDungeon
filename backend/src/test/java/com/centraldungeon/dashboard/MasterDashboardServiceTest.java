package com.centraldungeon.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.centraldungeon.dashboard.dto.MasterDashboardResponse;
import com.centraldungeon.dashboard.dto.MasterWorkItem;
import com.centraldungeon.registrations.PendingCandidateCount;
import com.centraldungeon.registrations.TableRegistrationRepository;
import com.centraldungeon.registrations.TableRegistrationStatus;
import com.centraldungeon.tables.GameTable;
import com.centraldungeon.tables.GameTableStatus;
import com.centraldungeon.tables.Master;
import com.centraldungeon.tables.MasterService;
import com.centraldungeon.tables.MasterType;
import com.centraldungeon.tables.TableSessionRepository;
import com.centraldungeon.tables.TableSessionStatus;
import com.centraldungeon.tables.UnrecordedSessionCount;
import com.centraldungeon.tasks.TableTask;
import com.centraldungeon.tasks.TableTaskRepository;
import com.centraldungeon.tasks.TableTaskService;
import com.centraldungeon.tasks.TaskAudience;
import com.centraldungeon.tasks.TaskStatus;
import com.centraldungeon.tasks.TaskSubmissionCount;
import com.centraldungeon.tasks.TaskSubmissionRepository;
import com.centraldungeon.tasks.dto.TaskRecipientResponse;
import com.centraldungeon.users.User;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The master's tray (#136): what it reports, what it deliberately does not, and the order.
 *
 * <p>The order is the part worth testing hardest. A tray sorted by anything other than time waited
 * stops answering the question it exists for - who has been waiting longest for an answer.
 */
@ExtendWith(MockitoExtension.class)
class MasterDashboardServiceTest {

    private static final LocalDateTime LONG_AGO = LocalDateTime.now().minusDays(9);
    private static final LocalDateTime A_WHILE_AGO = LocalDateTime.now().minusDays(5);
    private static final LocalDateTime RECENTLY = LocalDateTime.now().minusHours(2);

    @Mock
    private MasterService masterService;

    @Mock
    private TableRegistrationRepository registrationRepository;

    @Mock
    private TableSessionRepository sessionRepository;

    @Mock
    private TableTaskRepository taskRepository;

    @Mock
    private TaskSubmissionRepository submissionRepository;

    @Mock
    private TableTaskService tableTaskService;

    private MasterDashboardService service;

    @BeforeEach
    void setUp() {
        service = new MasterDashboardService(
                masterService, registrationRepository, sessionRepository, taskRepository, submissionRepository, tableTaskService);
        noCandidates();
        noSessions();
        noOverdueTasks();
    }

    @Test
    void reportsNothingWhenThePersonRunsNoTable() {
        when(masterService.findLiveByUser("master-1")).thenReturn(List.of());

        assertThat(service.forMaster("master-1").items()).isEmpty();
    }

    /** An empty tray is a success, not an absence: every table is up to date. */
    @Test
    void reportsNothingWhenEveryTableIsUpToDate() {
        runs(table("table-1", "La Cripta", GameTableStatus.InProgress));

        assertThat(service.forMaster("master-1").items()).isEmpty();
    }

    /** A finished table cannot be waiting on anybody, so it is not even probed. */
    @Test
    void leavesClosedTablesOut() {
        runs(table("table-1", "El Rio Negro", GameTableStatus.Finished));

        assertThat(service.forMaster("master-1").items()).isEmpty();
    }

    @Test
    void reportsPeopleWaitingForAnAnswer() {
        runs(table("table-1", "La Cripta", GameTableStatus.Opened));
        when(registrationRepository.countPendingByTables(anyCollection(), any())).thenReturn(
                List.of(new PendingCandidateCount("table-1", 3, A_WHILE_AGO)));

        MasterWorkItem item = only(service.forMaster("master-1"));

        assertThat(item.kind()).isEqualTo(MasterWorkItemKind.CandidatesWaiting);
        assertThat(item.tableName()).isEqualTo("La Cripta");
        assertThat(item.count()).isEqualTo(3);
        assertThat(item.since()).isEqualTo(A_WHILE_AGO);
    }

    @Test
    void reportsSessionsWhoseDateWentByWithoutBeingClosed() {
        runs(table("table-1", "Hijos del Vacio", GameTableStatus.InProgress));
        when(sessionRepository.countUnrecordedByTables(anyCollection(), any(), any())).thenReturn(
                List.of(new UnrecordedSessionCount("table-1", 2, A_WHILE_AGO)));

        MasterWorkItem item = only(service.forMaster("master-1"));

        assertThat(item.kind()).isEqualTo(MasterWorkItemKind.SessionToRecord);
        assertThat(item.count()).isEqualTo(2);
    }

    @Test
    void reportsAnOverdueTaskWithTheNumberOfPeopleWhoDidNotAnswer() {
        GameTable table = table("table-1", "Hijos del Vacio", GameTableStatus.InProgress);
        runs(table);
        overdue(task(table, "task-1", "Ficha de personaje", A_WHILE_AGO));
        when(tableTaskService.recipientsOf(any())).thenReturn(recipients(4));
        when(submissionRepository.countByTaskIds(anyCollection())).thenReturn(List.of(new TaskSubmissionCount("task-1", 5, 1)));

        MasterWorkItem item = only(service.forMaster("master-1"));

        assertThat(item.kind()).isEqualTo(MasterWorkItemKind.OverdueTaskMissing);
        assertThat(item.subject()).isEqualTo("Ficha de personaje");
        // Four asked, one person answered - the five rows are that person's accumulated answers (#76).
        assertThat(item.count()).isEqualTo(3);
        assertThat(item.since()).isEqualTo(A_WHILE_AGO);
    }

    /** The deadline going by is not by itself work: everybody answered, there is nothing to do. */
    @Test
    void leavesAnOverdueTaskOutWhenEverybodyAnswered() {
        GameTable table = table("table-1", "Hijos del Vacio", GameTableStatus.InProgress);
        runs(table);
        overdue(task(table, "task-1", "Ficha de personaje", A_WHILE_AGO));
        when(tableTaskService.recipientsOf(any())).thenReturn(recipients(2));
        when(submissionRepository.countByTaskIds(anyCollection())).thenReturn(List.of(new TaskSubmissionCount("task-1", 2, 2)));

        assertThat(service.forMaster("master-1").items()).isEmpty();
    }

    @Test
    void reportsADraftAnAdminSentBack() {
        GameTable table = table("table-1", "El Jardin de Hierro", GameTableStatus.ChangesRequested);
        ReflectionTestUtils.setField(table, "updatedAt", A_WHILE_AGO);
        runs(table);

        MasterWorkItem item = only(service.forMaster("master-1"));

        assertThat(item.kind()).isEqualTo(MasterWorkItemKind.ChangesRequested);
        assertThat(item.count()).isEqualTo(1);
        assertThat(item.since()).isEqualTo(A_WHILE_AGO);
    }

    @Test
    void reportsAnOpenTableWhoseStartDateWentBy() {
        GameTable table = table("table-1", "La Cripta", GameTableStatus.Opened);
        table.setStartDate(A_WHILE_AGO);
        runs(table);

        MasterWorkItem item = only(service.forMaster("master-1"));

        assertThat(item.kind()).isEqualTo(MasterWorkItemKind.ReadyToStart);
        assertThat(item.since()).isEqualTo(A_WHILE_AGO);
    }

    @Test
    void leavesAnOpenTableOutWhileItsStartDateIsStillAhead() {
        GameTable table = table("table-1", "La Cripta", GameTableStatus.Opened);
        table.setStartDate(LocalDateTime.now().plusDays(3));
        runs(table);

        assertThat(service.forMaster("master-1").items()).isEmpty();
    }

    /** Urgency is time waited, not volume: one person waiting nine days outranks three waiting two hours. */
    @Test
    void ordersTheTrayByHowLongEachThingHasBeenWaiting() {
        runs(
                table("table-1", "La Cripta", GameTableStatus.Opened),
                table("table-2", "Hijos del Vacio", GameTableStatus.InProgress));
        when(registrationRepository.countPendingByTables(anyCollection(), any())).thenReturn(
                List.of(new PendingCandidateCount("table-1", 3, RECENTLY)));
        when(sessionRepository.countUnrecordedByTables(anyCollection(), any(), any())).thenReturn(
                List.of(new UnrecordedSessionCount("table-2", 1, LONG_AGO)));

        List<MasterWorkItem> items = service.forMaster("master-1").items();

        assertThat(items).extracting(MasterWorkItem::kind)
                .containsExactly(MasterWorkItemKind.SessionToRecord, MasterWorkItemKind.CandidatesWaiting);
    }

    private void runs(GameTable... tables) {
        when(masterService.findLiveByUser("master-1")).thenReturn(
                java.util.Arrays.stream(tables).map(this::masterRowOf).toList());
    }

    private Master masterRowOf(GameTable table) {
        return new Master(table, persistedUser("master-1"), MasterType.Primary);
    }

    private void overdue(TableTask task) {
        when(taskRepository.findByGameTable_IdInAndStatusAndDueAtBeforeOrderByDueAtAsc(anyCollection(), any(), any()))
                .thenReturn(List.of(task));
    }

    private void noCandidates() {
        lenient()
                .when(registrationRepository.countPendingByTables(anyCollection(), any(TableRegistrationStatus.class)))
                .thenReturn(List.of());
    }

    private void noSessions() {
        lenient()
                .when(sessionRepository.countUnrecordedByTables(anyCollection(), any(TableSessionStatus.class), any()))
                .thenReturn(List.of());
    }

    private void noOverdueTasks() {
        lenient()
                .when(taskRepository.findByGameTable_IdInAndStatusAndDueAtBeforeOrderByDueAtAsc(
                        anyCollection(), any(TaskStatus.class), any()))
                .thenReturn(List.of());
    }

    private static List<TaskRecipientResponse> recipients(int howMany) {
        return java.util.stream.IntStream.range(0, howMany)
                .mapToObj(i -> new TaskRecipientResponse("player-" + i, "Player " + i))
                .toList();
    }

    private static MasterWorkItem only(MasterDashboardResponse response) {
        assertThat(response.items()).hasSize(1);
        return response.items().getFirst();
    }

    private GameTable table(String id, String name, GameTableStatus status) {
        GameTable table = new GameTable(name, persistedUser("creator-of-" + id));
        ReflectionTestUtils.setField(table, "id", id);
        table.setStatus(status);
        return table;
    }

    private TableTask task(GameTable table, String id, String title, LocalDateTime dueAt) {
        TableTask task = new TableTask(table, TaskAudience.Players, title);
        ReflectionTestUtils.setField(task, "id", id);
        task.setDueAt(dueAt);
        return task;
    }

    private User persistedUser(String id) {
        User user = new User("discord-" + id, "name-" + id);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
