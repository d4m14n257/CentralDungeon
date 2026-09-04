package com.centraldungeon.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.exception.ForbiddenActionException;
import com.centraldungeon.common.exception.InvalidRequestException;
import com.centraldungeon.common.text.RichTextSanitizer;
import com.centraldungeon.files.FileService;
import com.centraldungeon.files.FileType;
import com.centraldungeon.files.StoredFile;
import com.centraldungeon.files.StoredFileRepository;
import com.centraldungeon.tables.GameTable;
import com.centraldungeon.tables.MasterService;
import com.centraldungeon.tasks.dto.CreateSubmissionRequest;
import com.centraldungeon.tasks.dto.TaskRecipientResponse;
import com.centraldungeon.tasks.dto.TaskSubmissionResponse;
import com.centraldungeon.tasks.dto.TaskSubmissionsResponse;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * What handing in an answer does, and - just as much - what it deliberately does not do.
 *
 * <p>The two properties worth defending here are both refusals to be clever: answers <b>accumulate
 * and never overwrite</b> (#76), and a missing one <b>changes nothing</b> (#70). Both are easy to
 * "improve" into a bug by somebody reading only the code.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskSubmissionServiceTest {

    @Mock
    private TaskSubmissionRepository submissionRepository;

    @Mock
    private SubmissionFileRepository submissionFileRepository;

    @Mock
    private TableTaskService tableTaskService;

    @Mock
    private FileService fileService;

    @Mock
    private StoredFileRepository fileRepository;

    @Mock
    private MasterService masterService;

    @Mock
    private UserService userService;

    private final TaskMapper taskMapper = org.mapstruct.factory.Mappers.getMapper(TaskMapper.class);

    private TaskSubmissionService service() {
        return new TaskSubmissionService(
                submissionRepository,
                submissionFileRepository,
                tableTaskService,
                fileService,
                fileRepository,
                masterService,
                userService,
                new RichTextSanitizer(),
                taskMapper);
    }

    // ---------------------------------------------------------------- who may answer

    /** Being addressed by the task is what authorizes answering it - not a role, and not the table. */
    @Test
    void refusesAnAnswerFromSomebodyTheTaskIsNotAddressedTo() {
        TableTask task = givenOpenTask("task-1", true, true);
        when(tableTaskService.isRecipient(task, "outsider-1")).thenReturn(false);

        assertThatThrownBy(() -> service().submit("task-1", new CreateSubmissionRequest("hola", null), "outsider-1"))
                .isInstanceOf(ForbiddenActionException.class);
        verify(submissionRepository, never()).save(any());
    }

    /**
     * Closing ends the intake, and the refusal carries its own code: "could not save" is the wrong
     * thing to tell somebody who just wrote an answer, because retrying will not help (#188, #197).
     */
    @Test
    void refusesAnAnswerToAClosedTaskWithACodeOfItsOwn() {
        TableTask task = givenOpenTask("task-1", true, true);
        task.setStatus(TaskStatus.Closed);
        when(tableTaskService.isRecipient(task, "player-1")).thenReturn(true);

        assertThatThrownBy(() -> service().submit("task-1", new CreateSubmissionRequest("hola", null), "player-1"))
                .isInstanceOf(ConflictException.class)
                .extracting(error -> ((ConflictException) error).getErrorCode())
                .isEqualTo("TASK_CLOSED");
    }

    // ---------------------------------------------------------------- the shape of an answer

    @Test
    void refusesTextOnATaskThatDoesNotTakeText() {
        TableTask task = givenOpenTask("task-1", false, true);
        when(tableTaskService.isRecipient(task, "player-1")).thenReturn(true);

        assertThatThrownBy(() -> service().submit("task-1", new CreateSubmissionRequest("hola", null), "player-1"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("written answer");
    }

    @Test
    void refusesFilesOnATaskThatDoesNotTakeFiles() {
        TableTask task = givenOpenTask("task-1", true, false);
        when(tableTaskService.isRecipient(task, "player-1")).thenReturn(true);

        assertThatThrownBy(() -> service().submit("task-1", new CreateSubmissionRequest(null, List.of("file-1")), "player-1"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("files");
    }

    /**
     * An answer empty on both counts would take its author off the roster of who is still missing -
     * a row claiming somebody responded when they did not.
     */
    @Test
    void refusesAnAnswerThatCarriesNothing() {
        TableTask task = givenOpenTask("task-1", true, true);
        when(tableTaskService.isRecipient(task, "player-1")).thenReturn(true);

        assertThatThrownBy(() -> service().submit("task-1", new CreateSubmissionRequest("   ", List.of()), "player-1"))
                .isInstanceOf(InvalidRequestException.class);
        verify(submissionRepository, never()).save(any());
    }

    /** The same XSS surface as everything else written in the editor (#62). */
    @Test
    void sanitizesTheWrittenAnswer() {
        TableTask task = givenOpenTask("task-1", true, true);
        when(tableTaskService.isRecipient(task, "player-1")).thenReturn(true);
        givenSubmitter("player-1");
        when(submissionRepository.save(any(TaskSubmission.class))).thenAnswer(persistSubmission("submission-1"));

        TaskSubmissionResponse response = service()
                .submit("task-1", new CreateSubmissionRequest("<p>Elfa</p><script>alert(1)</script>", null), "player-1");

        assertThat(response.content()).isEqualTo("<p>Elfa</p>");
    }

    // ---------------------------------------------------------------- #76: answers accumulate

    /**
     * <b>The rule this whole class exists for.</b> A second answer is a second row: nothing is
     * updated, nothing is marked superseded, and the first one stays exactly where it was. Deciding
     * which of two character sheets counts is a judgement the system does not have (#76).
     */
    @Test
    void asecondAnswerInsertsARowAndOverwritesNothing() {
        TableTask task = givenOpenTask("task-1", true, true);
        when(tableTaskService.isRecipient(task, "player-1")).thenReturn(true);
        givenSubmitter("player-1");
        when(submissionRepository.save(any(TaskSubmission.class))).thenAnswer(persistSubmission("submission-x"));
        TaskSubmissionService service = service();

        service.submit("task-1", new CreateSubmissionRequest("primera version", null), "player-1");
        service.submit("task-1", new CreateSubmissionRequest("segunda version", null), "player-1");

        verify(submissionRepository, times(2)).save(any(TaskSubmission.class));
        verify(submissionRepository, never()).delete(any());
    }

    // ---------------------------------------------------------------- files

    /** The same gate as attaching to a table: the actor's own, or one the platform published (#79). */
    @Test
    void refusesAFileThatBelongsToSomebodyElse() {
        TableTask task = givenOpenTask("task-1", true, true);
        when(tableTaskService.isRecipient(task, "player-1")).thenReturn(true);
        givenSubmitter("player-1");
        when(submissionRepository.save(any(TaskSubmission.class))).thenAnswer(persistSubmission("submission-1"));
        when(fileService.requireAttachable("file-of-somebody-else", "player-1"))
                .thenThrow(new ForbiddenActionException("File belongs to somebody else"));

        assertThatThrownBy(() -> service()
                        .submit("task-1", new CreateSubmissionRequest(null, List.of("file-of-somebody-else")), "player-1"))
                .isInstanceOf(ForbiddenActionException.class);
        verify(submissionFileRepository, never()).save(any());
    }

    /** The file is linked, never stored again - the reuse #65 exists for. */
    @Test
    void linksTheFileWithoutCopyingIt() {
        TableTask task = givenOpenTask("task-1", true, true);
        when(tableTaskService.isRecipient(task, "player-1")).thenReturn(true);
        User player = givenSubmitter("player-1");
        when(submissionRepository.save(any(TaskSubmission.class))).thenAnswer(persistSubmission("submission-1"));
        when(fileService.requireAttachable("file-1", "player-1")).thenReturn(persistedFile("file-1", player));

        TaskSubmissionResponse response =
                service().submit("task-1", new CreateSubmissionRequest(null, List.of("file-1")), "player-1");

        assertThat(response.files()).extracting(file -> file.fileId()).containsExactly("file-1");
        verify(submissionFileRepository).save(any(SubmissionFile.class));
        verify(fileRepository, never()).save(any(StoredFile.class));
    }

    // ---------------------------------------------------------------- what the master reads

    /** Reading everything that came in is a question about running the table (#17, #121, #135). */
    @Test
    void refusesTheFullListToSomebodyWhoDoesNotRunTheTable() {
        givenOpenTask("task-1", true, true);
        when(masterService.isMasterOf("table-1", "outsider-1")).thenReturn(false);

        assertThatThrownBy(() -> service().listForTask("task-1", "outsider-1"))
                .isInstanceOf(ForbiddenActionException.class);
    }

    /**
     * The roster and the answers are one thought - "one of three" - and the people who did not answer
     * are a list to talk to, not a list to act on (#70).
     */
    @Test
    void tellsTheMasterWhoAnsweredAndWhoHasNot() {
        TableTask task = givenOpenTask("task-1", true, true);
        when(masterService.isMasterOf("table-1", "master-1")).thenReturn(true);
        when(tableTaskService.recipientsOf(task)).thenReturn(List.of(
                new TaskRecipientResponse("player-1", "discord-player-1"),
                new TaskRecipientResponse("player-2", "discord-player-2"),
                new TaskRecipientResponse("player-3", "discord-player-3")));
        when(submissionRepository.findByTask_IdAndDeletedAtIsNullOrderByCreatedAtAsc("task-1"))
                .thenReturn(List.of(submission(task, persistedUser("player-1"), "ahi va")));

        TaskSubmissionsResponse response = service().listForTask("task-1", "master-1");

        assertThat(response.submissions()).hasSize(1);
        assertThat(response.recipientCount()).isEqualTo(3);
        assertThat(response.missing()).extracting(TaskRecipientResponse::userId).containsExactly("player-2", "player-3");
    }

    /** Two versions from the same person are two answers and one person - they leave the roster once. */
    @Test
    void somebodyWhoAnsweredTwiceIsMissingZeroTimes() {
        TableTask task = givenOpenTask("task-1", true, true);
        when(masterService.isMasterOf("table-1", "master-1")).thenReturn(true);
        User player = persistedUser("player-1");
        when(tableTaskService.recipientsOf(task))
                .thenReturn(List.of(new TaskRecipientResponse("player-1", "discord-player-1")));
        when(submissionRepository.findByTask_IdAndDeletedAtIsNullOrderByCreatedAtAsc("task-1"))
                .thenReturn(List.of(submission(task, player, "primera"), submission(task, player, "segunda")));

        TaskSubmissionsResponse response = service().listForTask("task-1", "master-1");

        assertThat(response.submissions()).hasSize(2);
        assertThat(response.missing()).isEmpty();
    }

    /** Nobody needs a membership check to read their own answers: the query is keyed on the token (#121). */
    @Test
    void anybodyCanReadTheirOwnAnswersWithoutRunningTheTable() {
        TableTask task = givenOpenTask("task-1", true, true);
        when(submissionRepository.findByTask_IdAndUser_IdAndDeletedAtIsNullOrderByCreatedAtAsc("task-1", "player-1"))
                .thenReturn(List.of(submission(task, persistedUser("player-1"), "ahi va")));

        List<TaskSubmissionResponse> mine = service().listMine("task-1", "player-1");

        assertThat(mine).hasSize(1);
        verify(masterService, never()).isMasterOf(anyString(), anyString());
    }

    // ---------------------------------------------------------------- fixtures

    private TableTask givenOpenTask(String taskId, boolean acceptsText, boolean acceptsFiles) {
        GameTable table = new GameTable("Mesa E2E", persistedUser("master-1"));
        ReflectionTestUtils.setField(table, "id", "table-1");
        TableTask task = new TableTask(table, TaskAudience.Players, "Ficha de personaje");
        task.setAcceptsText(acceptsText);
        task.setAcceptsFiles(acceptsFiles);
        ReflectionTestUtils.setField(task, "id", taskId);
        ReflectionTestUtils.setField(task, "createdAt", LocalDateTime.now());
        when(tableTaskService.getLiveTask(taskId)).thenReturn(task);
        return task;
    }

    private User givenSubmitter(String userId) {
        User user = persistedUser(userId);
        when(userService.getById(userId)).thenReturn(user);
        return user;
    }

    private static TaskSubmission submission(TableTask task, User user, String content) {
        TaskSubmission submission = new TaskSubmission(task, user, content);
        ReflectionTestUtils.setField(submission, "id", "submission-" + content.hashCode());
        ReflectionTestUtils.setField(submission, "createdAt", LocalDateTime.now());
        return submission;
    }

    private static org.mockito.stubbing.Answer<TaskSubmission> persistSubmission(String id) {
        return invocation -> {
            TaskSubmission submission = invocation.getArgument(0);
            ReflectionTestUtils.setField(submission, "id", id);
            ReflectionTestUtils.setField(submission, "createdAt", LocalDateTime.now());
            return submission;
        };
    }

    private static User persistedUser(String id) {
        User user = new User("discord-" + id, id);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static StoredFile persistedFile(String id, User owner) {
        StoredFile file = new StoredFile("ficha.pdf", "key-" + id, "hash-" + id, "application/pdf", 120, FileType.Private, owner);
        ReflectionTestUtils.setField(file, "id", id);
        ReflectionTestUtils.setField(file, "createdAt", LocalDateTime.now());
        return file;
    }
}
