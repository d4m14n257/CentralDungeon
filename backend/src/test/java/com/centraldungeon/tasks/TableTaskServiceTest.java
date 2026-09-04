package com.centraldungeon.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.centraldungeon.common.exception.ForbiddenActionException;
import com.centraldungeon.common.exception.InvalidRequestException;
import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.common.text.RichTextSanitizer;
import com.centraldungeon.notifications.NotificationService;
import com.centraldungeon.registrations.TableRegistration;
import com.centraldungeon.registrations.TableRegistrationRepository;
import com.centraldungeon.registrations.TableRegistrationStatus;
import com.centraldungeon.tables.GameTable;
import com.centraldungeon.tables.GameTableRepository;
import com.centraldungeon.tables.GameTableStatus;
import com.centraldungeon.tables.MasterService;
import com.centraldungeon.tables.TableSession;
import com.centraldungeon.tables.TableSessionRepository;
import com.centraldungeon.tasks.dto.ApplicableTaskResponse;
import com.centraldungeon.tasks.dto.CreateTaskRequest;
import com.centraldungeon.tasks.dto.TaskResponse;
import com.centraldungeon.tasks.dto.UpdateTaskRequest;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The rules of what a table asks: who may ask, of whom, in what shape, and who is told about it.
 *
 * <p>Two of them are about the system <b>not</b> doing something, and those are the ones worth
 * writing down: a mandatory task blocks nothing (#70), and correcting one does not notify again.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TableTaskServiceTest {

    @Mock
    private TableTaskRepository taskRepository;

    @Mock
    private TaskSubmissionRepository submissionRepository;

    @Mock
    private GameTableRepository gameTableRepository;

    @Mock
    private TableSessionRepository sessionRepository;

    @Mock
    private TableRegistrationRepository registrationRepository;

    @Mock
    private MasterService masterService;

    @Mock
    private UserService userService;

    @Mock
    private NotificationService notificationService;

    private final TaskMapper taskMapper = org.mapstruct.factory.Mappers.getMapper(TaskMapper.class);

    private TableTaskService service() {
        return new TableTaskService(
                taskRepository,
                submissionRepository,
                gameTableRepository,
                sessionRepository,
                registrationRepository,
                masterService,
                userService,
                notificationService,
                new RichTextSanitizer(),
                taskMapper);
    }

    // ---------------------------------------------------------------- pertenencia

    /** The role is not the membership (#135): only a row in masters opens this door. */
    @Test
    void refusesEveryMutationToSomebodyWhoDoesNotRunTheTable() {
        givenTable("table-1");
        givenTask("task-1", "table-1", TaskAudience.Players);
        when(masterService.isMasterOf(eq("table-1"), anyString())).thenReturn(false);
        TableTaskService service = service();

        assertThatThrownBy(() -> service.publish("table-1", createRequest(TaskAudience.Players, null), "outsider-1"))
                .isInstanceOf(ForbiddenActionException.class);
        assertThatThrownBy(() -> service.update("task-1", updateRequest(TaskAudience.Players, null), "outsider-1"))
                .isInstanceOf(ForbiddenActionException.class);
        assertThatThrownBy(() -> service.close("task-1", "outsider-1")).isInstanceOf(ForbiddenActionException.class);
        assertThatThrownBy(() -> service.listForTable("table-1", "outsider-1")).isInstanceOf(ForbiddenActionException.class);

        verify(taskRepository, never()).save(any());
        verify(notificationService, never()).notifyTaskPublished(anyString(), any(), anyString());
    }

    // ---------------------------------------------------------------- publishing notifies (#77)

    /** #77: a task nobody heard about cannot be answered, so publishing tells every recipient. */
    @Test
    void publishingToThePlayersNotifiesEveryPlayer() {
        GameTable table = givenTable("table-1");
        when(masterService.isMasterOf("table-1", "master-1")).thenReturn(true);
        when(taskRepository.save(any(TableTask.class))).thenAnswer(persistTask("task-1"));
        givenRoster("table-1", TableRegistrationStatus.Player, "player-1", "player-2");

        TaskResponse response = service().publish("table-1", createRequest(TaskAudience.Players, null), "master-1");

        assertThat(response.recipientCount()).isEqualTo(2);
        assertThat(response.submissionCount()).isZero();
        verify(notificationService).notifyTaskPublished("player-1", table, "Ficha de personaje");
        verify(notificationService).notifyTaskPublished("player-2", table, "Ficha de personaje");
    }

    /** The audience decides who is told: the candidate queue for a Candidates task, and nobody else. */
    @Test
    void publishingToTheCandidatesNotifiesTheQueueAndNotThePlayers() {
        givenTable("table-1");
        when(masterService.isMasterOf("table-1", "master-1")).thenReturn(true);
        when(taskRepository.save(any(TableTask.class))).thenAnswer(persistTask("task-1"));
        givenRoster("table-1", TableRegistrationStatus.Candidate, "candidate-1");
        givenRoster("table-1", TableRegistrationStatus.Player, "player-1");

        service().publish("table-1", createRequest(TaskAudience.Candidates, null), "master-1");

        verify(notificationService).notifyTaskPublished(eq("candidate-1"), any(), anyString());
        verify(notificationService, never()).notifyTaskPublished(eq("player-1"), any(), anyString());
    }

    /**
     * A table with nobody in it yet is not refused: writing what will be asked of applicants is part
     * of preparing a table. It simply has no recipients, so nothing is sent.
     */
    @Test
    void publishingOnAnEmptyTableNotifiesNobodyAndStillSaves() {
        givenTable("table-1");
        when(masterService.isMasterOf("table-1", "master-1")).thenReturn(true);
        when(taskRepository.save(any(TableTask.class))).thenAnswer(persistTask("task-1"));

        TaskResponse response = service().publish("table-1", createRequest(TaskAudience.Candidates, null), "master-1");

        assertThat(response.recipientCount()).isZero();
        verify(taskRepository).save(any(TableTask.class));
        verify(notificationService, never()).notifyTaskPublished(anyString(), any(), anyString());
    }

    // ---------------------------------------------------------------- the audience/target contract

    /** A Single task addressing nobody is a row nothing on screen could explain. */
    @Test
    void refusesASingleTaskWithoutATarget() {
        givenTable("table-1");
        when(masterService.isMasterOf("table-1", "master-1")).thenReturn(true);

        assertThatThrownBy(() -> service().publish("table-1", createRequest(TaskAudience.Single, null), "master-1"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Single");
        verify(taskRepository, never()).save(any());
    }

    /** And the other direction: a target on an audience that does not use one is a value nothing reads. */
    @Test
    void refusesATargetOnAnAudienceThatDoesNotUseOne() {
        givenTable("table-1");
        when(masterService.isMasterOf("table-1", "master-1")).thenReturn(true);

        assertThatThrownBy(() -> service().publish("table-1", createRequest(TaskAudience.Players, "player-1"), "master-1"))
                .isInstanceOf(InvalidRequestException.class);
        verify(taskRepository, never()).save(any());
    }

    /** Addressing material to somebody who does not play there sends it to an inbox with no screen behind it. */
    @Test
    void refusesASingleTaskAimedAtSomebodyWhoDoesNotPlayThere() {
        givenTable("table-1");
        when(masterService.isMasterOf("table-1", "master-1")).thenReturn(true);
        when(registrationRepository.existsByGameTable_IdAndUser_IdAndStatusIn(
                        "table-1", "stranger-1", List.of(TableRegistrationStatus.Player)))
                .thenReturn(false);

        assertThatThrownBy(() -> service().publish("table-1", createRequest(TaskAudience.Single, "stranger-1"), "master-1"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("does not play");
    }

    @Test
    void publishesASingleTaskToAPlayerOfTheTable() {
        GameTable table = givenTable("table-1");
        when(masterService.isMasterOf("table-1", "master-1")).thenReturn(true);
        when(taskRepository.save(any(TableTask.class))).thenAnswer(persistTask("task-1"));
        givenPlays("table-1", "player-1");
        when(userService.getById("player-1")).thenReturn(persistedUser("player-1"));

        TaskResponse response = service().publish("table-1", createRequest(TaskAudience.Single, "player-1"), "master-1");

        assertThat(response.targetUserId()).isEqualTo("player-1");
        assertThat(response.recipientCount()).isEqualTo(1);
        verify(notificationService).notifyTaskPublished("player-1", table, "Ficha de personaje");
    }

    // ---------------------------------------------------------------- shape of the ask

    /** A task that takes neither text nor files asks for something nobody can hand in. */
    @Test
    void refusesATaskThatAcceptsNeitherTextNorFiles() {
        givenTable("table-1");
        when(masterService.isMasterOf("table-1", "master-1")).thenReturn(true);
        CreateTaskRequest neither = new CreateTaskRequest(
                "Ficha de personaje", null, TaskAudience.Players, null, null, false, false, false, null);

        assertThatThrownBy(() -> service().publish("table-1", neither, "master-1"))
                .isInstanceOf(InvalidRequestException.class);
        verify(taskRepository, never()).save(any());
    }

    /** A task can only be tied to an evening of its own table. */
    @Test
    void refusesASessionThatBelongsToAnotherTable() {
        givenTable("table-1");
        GameTable other = givenTable("table-2");
        when(masterService.isMasterOf("table-1", "master-1")).thenReturn(true);
        TableSession foreign = new TableSession(other, 1, LocalDateTime.parse("2026-09-15T20:00"));
        ReflectionTestUtils.setField(foreign, "id", "session-9");
        when(sessionRepository.findById("session-9")).thenReturn(Optional.of(foreign));

        CreateTaskRequest tied = new CreateTaskRequest(
                "Ficha de personaje", null, TaskAudience.Players, null, "session-9", true, true, false, null);

        assertThatThrownBy(() -> service().publish("table-1", tied, "master-1"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("another table");
    }

    /** The rich text is cleaned on the way in (#62): this is the same XSS surface as the table's own body. */
    @Test
    void sanitizesTheDescriptionBeforeStoringIt() {
        givenTable("table-1");
        when(masterService.isMasterOf("table-1", "master-1")).thenReturn(true);
        when(taskRepository.save(any(TableTask.class))).thenAnswer(persistTask("task-1"));
        CreateTaskRequest withScript = new CreateTaskRequest(
                "Ficha de personaje",
                "<p>Nivel 3</p><script>alert(1)</script>",
                TaskAudience.Players,
                null,
                null,
                true,
                true,
                false,
                null);

        TaskResponse response = service().publish("table-1", withScript, "master-1");

        assertThat(response.description()).isEqualTo("<p>Nivel 3</p>");
    }

    // ---------------------------------------------------------------- #70: mandatory blocks nothing

    /**
     * The decision that is easiest to lose: {@code isMandatory} is carried to the screen and read by
     * no rule (#70). A mandatory task publishes, closes and reads exactly like any other.
     */
    @Test
    void aMandatoryTaskBehavesExactlyLikeAnyOther() {
        givenTable("table-1");
        when(masterService.isMasterOf("table-1", "master-1")).thenReturn(true);
        when(taskRepository.save(any(TableTask.class))).thenAnswer(persistTask("task-1"));
        givenRoster("table-1", TableRegistrationStatus.Player, "player-1");
        CreateTaskRequest mandatory = new CreateTaskRequest(
                "Ficha de personaje", null, TaskAudience.Players, null, null, true, true, true, null);

        TaskResponse response = service().publish("table-1", mandatory, "master-1");

        assertThat(response.isMandatory()).isTrue();
        assertThat(response.status()).isEqualTo("Open");
        assertThat(response.recipientCount()).isEqualTo(1);
        verify(notificationService).notifyTaskPublished(eq("player-1"), any(), eq("Ficha de personaje"));
    }

    // ---------------------------------------------------------------- correcting and closing

    /** #77 puts the notification at publication: a task fixed three times must not ring three times. */
    @Test
    void correctingATaskDoesNotNotifyAgain() {
        givenTable("table-1");
        givenTask("task-1", "table-1", TaskAudience.Players);
        when(masterService.isMasterOf("table-1", "master-1")).thenReturn(true);
        givenRoster("table-1", TableRegistrationStatus.Player, "player-1");

        TaskResponse response = service().update("task-1", updateRequest(TaskAudience.Players, null), "master-1");

        assertThat(response.title()).isEqualTo("Ficha corregida");
        verify(notificationService, never()).notifyTaskPublished(anyString(), any(), anyString());
    }

    @Test
    void closingStopsTheIntakeAndLeavesWhatCameInAlone() {
        givenTable("table-1");
        TableTask task = givenTask("task-1", "table-1", TaskAudience.Players);
        when(masterService.isMasterOf("table-1", "master-1")).thenReturn(true);

        TaskResponse response = service().close("task-1", "master-1");

        assertThat(response.status()).isEqualTo("Closed");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.Closed);
        verify(submissionRepository, never()).delete(any());
    }

    /** A marked task stops existing, and 404 rather than 403: "it was deleted" is not a caller's business (#25). */
    @Test
    void aDeletedTaskIsNotFound() {
        TableTask task = givenTask("task-1", "table-1", TaskAudience.Players);
        task.setStatus(TaskStatus.Deleted);

        assertThatThrownBy(() -> service().getLiveTask("task-1")).isInstanceOf(NotFoundException.class);
    }

    // ---------------------------------------------------------------- what a reader sees

    /**
     * Somebody who has not applied still sees what will be asked of them - which is half of deciding
     * whether to apply (#206). They cannot answer it yet, and the flag is what lets the button say so.
     */
    @Test
    void aStrangerSeesTheCandidatesTasksButCannotAnswerThem() {
        givenTable("table-1");
        givenApplicable("table-1", List.of(task("task-1", "table-1", TaskAudience.Candidates, null)));

        List<ApplicableTaskResponse> applicable = service().listApplicable("table-1", "stranger-1");

        assertThat(applicable).hasSize(1);
        assertThat(applicable.getFirst().canSubmit()).isFalse();
    }

    /** A candidate sees the same task and may answer it: that is what the audience means. */
    @Test
    void aCandidateMayAnswerACandidatesTask() {
        givenTable("table-1");
        givenApplicable("table-1", List.of(task("task-1", "table-1", TaskAudience.Candidates, null)));
        givenHolds("table-1", "candidate-1", TableRegistrationStatus.Candidate);

        assertThat(service().listApplicable("table-1", "candidate-1").getFirst().canSubmit()).isTrue();
    }

    /** A Single task is its target's business and nobody else's, even among the table's own players. */
    @Test
    void aSingleTaskIsInvisibleToEveryPlayerButItsTarget() {
        givenTable("table-1");
        TableTask single = task("task-1", "table-1", TaskAudience.Single, persistedUser("player-1"));
        givenApplicable("table-1", List.of(single));
        givenHolds("table-1", "player-2", TableRegistrationStatus.Player);

        assertThat(service().listApplicable("table-1", "player-2")).isEmpty();

        givenHolds("table-1", "player-1", TableRegistrationStatus.Player);
        assertThat(service().listApplicable("table-1", "player-1")).hasSize(1);
    }

    /** Answers accumulate (#76), so what the reader is shown is a count and not a "done" flag. */
    @Test
    void tellsTheReaderHowManyTimesTheyAlreadyAnswered() {
        givenTable("table-1");
        givenApplicable("table-1", List.of(task("task-1", "table-1", TaskAudience.Players, null)));
        givenHolds("table-1", "player-1", TableRegistrationStatus.Player);
        TableTask answered = task("task-1", "table-1", TaskAudience.Players, null);
        User player = persistedUser("player-1");
        when(submissionRepository.findByTask_IdAndUser_IdAndDeletedAtIsNullOrderByCreatedAtAsc("task-1", "player-1"))
                .thenReturn(List.of(
                        new TaskSubmission(answered, player, "primera version"),
                        new TaskSubmission(answered, player, "segunda version")));

        assertThat(service().listApplicable("table-1", "player-1").getFirst().mySubmissionCount()).isEqualTo(2);
    }

    // ---------------------------------------------------------------- fixtures

    private GameTable givenTable(String id) {
        GameTable table = new GameTable("Mesa " + id, persistedUser("master-1"));
        ReflectionTestUtils.setField(table, "id", id);
        ReflectionTestUtils.setField(table, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(table, "status", GameTableStatus.Opened);
        when(gameTableRepository.findById(id)).thenReturn(Optional.of(table));
        return table;
    }

    private TableTask givenTask(String taskId, String tableId, TaskAudience audience) {
        TableTask task = task(taskId, tableId, audience, null);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        return task;
    }

    private TableTask task(String taskId, String tableId, TaskAudience audience, User target) {
        GameTable table = new GameTable("Mesa " + tableId, persistedUser("master-1"));
        ReflectionTestUtils.setField(table, "id", tableId);
        TableTask task = new TableTask(table, audience, "Ficha de personaje");
        task.setTargetUser(target);
        ReflectionTestUtils.setField(task, "id", taskId);
        ReflectionTestUtils.setField(task, "createdAt", LocalDateTime.now());
        return task;
    }

    private void givenApplicable(String tableId, List<TableTask> tasks) {
        when(taskRepository.findByGameTable_IdAndAudienceInAndStatusOrderByCreatedAtAsc(
                        eq(tableId), any(), eq(TaskStatus.Open)))
                .thenReturn(tasks);
    }

    private void givenRoster(String tableId, TableRegistrationStatus status, String... userIds) {
        GameTable table = new GameTable("Mesa " + tableId, persistedUser("master-1"));
        ReflectionTestUtils.setField(table, "id", tableId);
        List<TableRegistration> rows = java.util.Arrays.stream(userIds)
                .map(userId -> new TableRegistration(table, persistedUser(userId), null))
                .toList();
        when(registrationRepository.findByGameTable_IdAndStatusOrderByCreatedAtAsc(tableId, status)).thenReturn(rows);
    }

    private void givenPlays(String tableId, String userId) {
        givenHolds(tableId, userId, TableRegistrationStatus.Player);
    }

    private void givenHolds(String tableId, String userId, TableRegistrationStatus status) {
        when(registrationRepository.existsByGameTable_IdAndUser_IdAndStatusIn(tableId, userId, List.of(status)))
                .thenReturn(true);
    }

    private static CreateTaskRequest createRequest(TaskAudience audience, String targetUserId) {
        return new CreateTaskRequest(
                "Ficha de personaje", null, audience, targetUserId, null, true, true, false, null);
    }

    private static UpdateTaskRequest updateRequest(TaskAudience audience, String targetUserId) {
        return new UpdateTaskRequest("Ficha corregida", null, audience, targetUserId, null, true, true, false, null);
    }

    private static org.mockito.stubbing.Answer<TableTask> persistTask(String id) {
        return invocation -> {
            TableTask task = invocation.getArgument(0);
            ReflectionTestUtils.setField(task, "id", id);
            ReflectionTestUtils.setField(task, "createdAt", LocalDateTime.now());
            return task;
        };
    }

    private static User persistedUser(String id) {
        User user = new User("discord-" + id, id);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
