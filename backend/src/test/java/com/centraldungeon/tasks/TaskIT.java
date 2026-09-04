package com.centraldungeon.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.files.FileService;
import com.centraldungeon.files.FileType;
import com.centraldungeon.files.dto.FileResponse;
import com.centraldungeon.files.dto.UploadFileRequest;
import com.centraldungeon.notifications.Notification;
import com.centraldungeon.notifications.NotificationRepository;
import com.centraldungeon.notifications.NotificationType;
import com.centraldungeon.registrations.TableRegistration;
import com.centraldungeon.registrations.TableRegistrationRepository;
import com.centraldungeon.registrations.TableRegistrationStatus;
import com.centraldungeon.tables.GameTable;
import com.centraldungeon.tables.GameTableRepository;
import com.centraldungeon.tables.MasterService;
import com.centraldungeon.tasks.dto.ApplicableTaskResponse;
import com.centraldungeon.tasks.dto.CreateSubmissionRequest;
import com.centraldungeon.tasks.dto.CreateTaskRequest;
import com.centraldungeon.tasks.dto.TaskRecipientResponse;
import com.centraldungeon.tasks.dto.TaskResponse;
import com.centraldungeon.tasks.dto.TaskSubmissionsResponse;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * The half of the task story only a real database can answer.
 *
 * <p>Three things a mock structurally cannot check are here. <b>Publishing writes one notification
 * row per recipient</b> (#77) - a unit test can assert the service was called, this counts what is in
 * the table. <b>Two answers from one person are two rows</b> (#76), which is the accumulation rule
 * against a real primary key rather than against a stubbed {@code save}. And <b>the fifth way a file
 * becomes readable</b>: the query behind it joins three tables, so it only means anything against
 * SQL that actually runs.
 *
 * <p>Storage points at a temporary directory rather than the dev root, so a run never leaves blobs in
 * the working tree. Wired with {@code @DynamicPropertySource}, not {@code @ServiceConnection}: see
 * {@code RegistrationServiceIT} for why.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class TaskIT {

    @Container
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @TempDir
    static Path storageRoot;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("app.storage.root", () -> storageRoot.toString());
    }

    @Autowired
    private TableTaskService tableTaskService;

    @Autowired
    private TaskSubmissionService taskSubmissionService;

    @Autowired
    private TaskSubmissionRepository submissionRepository;

    @Autowired
    private FileService fileService;

    @Autowired
    private GameTableRepository gameTableRepository;

    @Autowired
    private TableRegistrationRepository registrationRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private MasterService masterService;

    @Autowired
    private UserRepository userRepository;

    private User master;
    private User firstPlayer;
    private User secondPlayer;
    private GameTable table;

    @BeforeEach
    void setUp() {
        master = userRepository.save(new User(randomDiscordId(), "Task Master"));
        firstPlayer = userRepository.save(new User(randomDiscordId(), "Primera jugadora"));
        secondPlayer = userRepository.save(new User(randomDiscordId(), "Segundo jugador"));
        table = gameTableRepository.save(new GameTable("Mesa de las peticiones", master));
        masterService.createPrimary(table, master);
        enroll(firstPlayer, TableRegistrationStatus.Player);
        enroll(secondPlayer, TableRegistrationStatus.Player);
    }

    /** #77 against the table it writes to: one row per recipient, and none for anybody else. */
    @Test
    void publishingWritesOneNotificationPerRecipient() {
        TaskResponse task = publish(TaskAudience.Players, null);

        assertThat(task.recipientCount()).isEqualTo(2);
        assertThat(taskNotificationsOf(firstPlayer)).hasSize(1);
        assertThat(taskNotificationsOf(secondPlayer)).hasSize(1);
        assertThat(taskNotificationsOf(master)).isEmpty();
    }

    /** The title travels as a parameter so the bell can name the request (#197). */
    @Test
    void theNotificationCarriesTheTaskTitleRatherThanASentence() {
        publish(TaskAudience.Players, null);

        Notification notification = taskNotificationsOf(firstPlayer).getFirst();
        assertThat(notification.getTitle()).isNull();
        assertThat(notification.getParams()).isNotNull();
        assertThat(notification.getParams().taskTitle()).isEqualTo("Ficha de personaje");
        assertThat(notification.getParams().tableName()).isEqualTo("Mesa de las peticiones");
        assertThat(notification.getRelatedEntityId()).isEqualTo(table.getId());
    }

    /**
     * <b>The rule of #76 against a real primary key.</b> Two answers from one person are two rows;
     * neither overwrites the other, and the person leaves the roster of who is missing exactly once.
     */
    @Test
    void twoAnswersFromOnePersonAreTwoRowsAndOneRespondent() {
        TaskResponse task = publish(TaskAudience.Players, null);

        taskSubmissionService.submit(task.taskId(), new CreateSubmissionRequest("primera version", null), firstPlayer.getId());
        taskSubmissionService.submit(task.taskId(), new CreateSubmissionRequest("segunda version", null), firstPlayer.getId());

        assertThat(submissionRepository.findByTask_IdAndDeletedAtIsNullOrderByCreatedAtAsc(task.taskId())).hasSize(2);

        TaskSubmissionsResponse answers = taskSubmissionService.listForTask(task.taskId(), master.getId());
        assertThat(answers.submissions()).hasSize(2);
        assertThat(answers.recipientCount()).isEqualTo(2);
        assertThat(answers.missing())
                .singleElement()
                .extracting(TaskRecipientResponse::userId)
                .isEqualTo(secondPlayer.getId());
    }

    /** The counts on the master's board come from one grouped query, and they mean different things. */
    @Test
    void theBoardCountsAnswersAndPeopleSeparately() {
        TaskResponse task = publish(TaskAudience.Players, null);
        taskSubmissionService.submit(task.taskId(), new CreateSubmissionRequest("una", null), firstPlayer.getId());
        taskSubmissionService.submit(task.taskId(), new CreateSubmissionRequest("otra", null), firstPlayer.getId());
        taskSubmissionService.submit(task.taskId(), new CreateSubmissionRequest("la mia", null), secondPlayer.getId());

        TaskResponse listed = tableTaskService.listForTable(table.getId(), master.getId()).stream()
                .filter(row -> row.taskId().equals(task.taskId()))
                .findFirst()
                .orElseThrow();

        assertThat(listed.submissionCount()).isEqualTo(3);
        assertThat(listed.respondentCount()).isEqualTo(2);
        assertThat(listed.recipientCount()).isEqualTo(2);
    }

    /**
     * <b>The fifth way a file is reachable</b> (#63, #76, #206): the master opens what their player
     * handed in, and somebody with no business there gets a 404 rather than a 403 - confirming a file
     * exists is itself a leak (#9, #29).
     */
    @Test
    void theMasterCanOpenAFileTheirPlayerHandedInAndAStrangerCannot() {
        TaskResponse task = publish(TaskAudience.Players, null);
        FileResponse sheet = fileService.upload(
                pdf("ficha.pdf", "elfa exploradora"), new UploadFileRequest(FileType.Private), firstPlayer.getId());

        taskSubmissionService.submit(
                task.taskId(), new CreateSubmissionRequest(null, List.of(sheet.id())), firstPlayer.getId());

        assertThat(fileService.download(sheet.id(), master.getId()).name()).isEqualTo("ficha.pdf");
        User stranger = userRepository.save(new User(randomDiscordId(), "Nadie"));
        assertThatThrownBy(() -> fileService.download(sheet.id(), stranger.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    /** Handing in a file links it and never stores it again (#65, #79). */
    @Test
    void aSubmittedFileTravelsWithTheAnswerAndIsNotCopied() {
        TaskResponse task = publish(TaskAudience.Players, null);
        FileResponse sheet = fileService.upload(
                pdf("ficha.pdf", "elfa exploradora"), new UploadFileRequest(FileType.Private), firstPlayer.getId());
        taskSubmissionService.submit(
                task.taskId(), new CreateSubmissionRequest("ahi va", List.of(sheet.id())), firstPlayer.getId());

        TaskSubmissionsResponse answers = taskSubmissionService.listForTask(task.taskId(), master.getId());

        assertThat(answers.submissions()).singleElement().satisfies(answer -> {
            assertThat(answer.content()).isEqualTo("ahi va");
            assertThat(answer.files()).singleElement().satisfies(file -> {
                assertThat(file.fileId()).isEqualTo(sheet.id());
                assertThat(file.name()).isEqualTo("ficha.pdf");
            });
        });
    }

    /**
     * The audience against real rows: a candidate sees what is asked of candidates and not what is
     * asked of players, and can answer only the first (#63, #206).
     */
    @Test
    void aCandidateSeesTheCandidatesTaskAndNotThePlayersOne() {
        User candidate = userRepository.save(new User(randomDiscordId(), "Aspirante"));
        enroll(candidate, TableRegistrationStatus.Candidate);
        publish(TaskAudience.Candidates, null);
        publish(TaskAudience.Players, null);

        List<ApplicableTaskResponse> applicable = tableTaskService.listApplicable(table.getId(), candidate.getId());

        assertThat(applicable).singleElement().satisfies(task -> {
            assertThat(task.audience()).isEqualTo("Candidates");
            assertThat(task.canSubmit()).isTrue();
        });
    }

    /** A Single task is only ever seen by its target, even by the table's other players (#76). */
    @Test
    void aSingleTaskReachesOnlyItsTarget() {
        publish(TaskAudience.Single, firstPlayer.getId());

        assertThat(tableTaskService.listApplicable(table.getId(), firstPlayer.getId())).hasSize(1);
        assertThat(tableTaskService.listApplicable(table.getId(), secondPlayer.getId())).isEmpty();
        assertThat(taskNotificationsOf(firstPlayer)).hasSize(1);
        assertThat(taskNotificationsOf(secondPlayer)).isEmpty();
    }

    /** Closing ends the intake and leaves what came in exactly where it was (#76). */
    @Test
    void closingKeepsWhatWasAlreadyHandedIn() {
        TaskResponse task = publish(TaskAudience.Players, null);
        taskSubmissionService.submit(task.taskId(), new CreateSubmissionRequest("llegue a tiempo", null), firstPlayer.getId());

        tableTaskService.close(task.taskId(), master.getId());

        assertThat(taskSubmissionService.listForTask(task.taskId(), master.getId()).submissions()).hasSize(1);
        assertThat(tableTaskService.listApplicable(table.getId(), firstPlayer.getId())).isEmpty();
    }

    // ---------------------------------------------------------------- fixtures

    private TaskResponse publish(TaskAudience audience, String targetUserId) {
        return tableTaskService.publish(
                table.getId(),
                new CreateTaskRequest(
                        "Ficha de personaje", "<p>Nivel 3</p>", audience, targetUserId, null, true, true, false, null),
                master.getId());
    }

    private void enroll(User user, TableRegistrationStatus status) {
        TableRegistration registration = new TableRegistration(table, user, null);
        registration.setStatus(status);
        registrationRepository.save(registration);
    }

    private List<Notification> taskNotificationsOf(User user) {
        return notificationRepository.findByUser_IdOrderByCreatedAtDesc(user.getId(), org.springframework.data.domain.PageRequest.of(0, 50))
                .getContent()
                .stream()
                .filter(notification -> notification.getNotificationType() == NotificationType.TaskPublished)
                .toList();
    }

    private static MockMultipartFile pdf(String filename, String content) {
        return new MockMultipartFile("file", filename, "application/pdf", content.getBytes(StandardCharsets.UTF_8));
    }

    private static String randomDiscordId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
