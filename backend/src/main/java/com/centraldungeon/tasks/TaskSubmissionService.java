package com.centraldungeon.tasks;

import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.exception.ForbiddenActionException;
import com.centraldungeon.common.exception.InvalidRequestException;
import com.centraldungeon.common.text.RichTextSanitizer;
import com.centraldungeon.files.FileService;
import com.centraldungeon.files.FileStatus;
import com.centraldungeon.files.StoredFile;
import com.centraldungeon.files.StoredFileRepository;
import com.centraldungeon.tables.MasterService;
import com.centraldungeon.tasks.dto.CreateSubmissionRequest;
import com.centraldungeon.tasks.dto.SubmittedFileResponse;
import com.centraldungeon.tasks.dto.TaskRecipientResponse;
import com.centraldungeon.tasks.dto.TaskSubmissionResponse;
import com.centraldungeon.tasks.dto.TaskSubmissionsResponse;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handing in an answer to a task, and reading what came in.
 *
 * <p><b>Answers accumulate and nothing is ever overwritten</b> (#76). Every submission is an insert;
 * there is no update endpoint and this class has no method that edits one. Sending a second version
 * leaves the first untouched, because deciding which of two character sheets counts takes a
 * judgement the system does not have - and silently replacing would be the system making it anyway.
 *
 * <p><b>There is no review.</b> No accept, no reject, no {@code reviewed_by} (#76). The master reads
 * what came in and talks to people; the software's job ends at delivering it.
 *
 * <p><b>And nothing here punishes a missing answer</b> (#70). The roster of who has not handed
 * anything in is a list of names on a screen: no endpoint acts on it, and {@code isMandatory} changes
 * none of the answers below.
 *
 * <p>Two different authorization questions live here and they are not the same one. Handing in is
 * about being <b>addressed</b> by the task ({@link TableTaskService#isRecipient}); reading everything
 * that came in is about <b>running the table</b> (#17, #121, #135).
 */
@Service
public class TaskSubmissionService {

    /** The {@code task_submissions} rows. */
    private final TaskSubmissionRepository submissionRepository;

    /** The files handed in with an answer - linked, never copied (#65, #79). */
    private final SubmissionFileRepository submissionFileRepository;

    /** The task being answered, its audience, and its roster. */
    private final TableTaskService tableTaskService;

    /** Resolves and authorizes each file being handed in, and stamps its last use (#75). */
    private final FileService fileService;

    /** The files themselves, for the metadata each answer is shown with. */
    private final StoredFileRepository fileRepository;

    /** Answers pertenencia: a row in {@code masters}, never the platform role (#135). */
    private final MasterService masterService;

    /** Resolves the submitter, who is always the actor of the token (#121). */
    private final UserService userService;

    /** The whitelist of #62, applied on the way in and on the way out. */
    private final RichTextSanitizer richTextSanitizer;

    /** Entity to DTO. */
    private final TaskMapper taskMapper;

    /**
     * @param submissionRepository     the {@code task_submissions} rows
     * @param submissionFileRepository the files handed in with each answer
     * @param tableTaskService         the task, its audience and its roster
     * @param fileService              resolves and authorizes each file handed in (#79)
     * @param fileRepository           the files each link points at
     * @param masterService            answers pertenencia (#17, #121, #135)
     * @param userService              resolves the submitter from the token
     * @param richTextSanitizer        the whitelist of #62
     * @param taskMapper               entity to DTO
     */
    public TaskSubmissionService(
            TaskSubmissionRepository submissionRepository,
            SubmissionFileRepository submissionFileRepository,
            TableTaskService tableTaskService,
            FileService fileService,
            StoredFileRepository fileRepository,
            MasterService masterService,
            UserService userService,
            RichTextSanitizer richTextSanitizer,
            TaskMapper taskMapper) {
        this.submissionRepository = submissionRepository;
        this.submissionFileRepository = submissionFileRepository;
        this.tableTaskService = tableTaskService;
        this.fileService = fileService;
        this.fileRepository = fileRepository;
        this.masterService = masterService;
        this.userService = userService;
        this.richTextSanitizer = richTextSanitizer;
        this.taskMapper = taskMapper;
    }

    /**
     * Hands in an answer.
     *
     * @param taskId  the task being answered
     * @param request the written answer, the files, or both
     * @param actorId the actor, from the token - the submitter is never named in the request (#121)
     * @return the answer as it was recorded
     * @throws ForbiddenActionException if the task is not addressed to the actor, or a file is
     *                                  somebody else's and not published (#79)
     * @throws ConflictException        if the task is no longer taking answers
     * @throws InvalidRequestException  if the answer uses a channel the task does not take, or is
     *                                  empty on both
     * @throws com.centraldungeon.common.exception.NotFoundException if the task or a file is not there
     */
    @Transactional
    public TaskSubmissionResponse submit(String taskId, CreateSubmissionRequest request, String actorId) {
        TableTask task = tableTaskService.getLiveTask(taskId);
        if (!tableTaskService.isRecipient(task, actorId)) {
            throw new ForbiddenActionException("Task " + taskId + " is not addressed to user " + actorId);
        }
        if (task.getStatus() != TaskStatus.Open) {
            // Its own code, because "could not save" is the wrong thing to tell somebody who just
            // wrote an answer: what they need to know is that the master closed the request, which is
            // not something they can fix by retrying (#188, #197).
            throw new ConflictException("Task " + taskId + " is closed and no longer takes answers", "TASK_CLOSED");
        }

        String content = richTextSanitizer.sanitize(request.content());
        List<String> fileIds = request.fileIds() == null ? List.of() : request.fileIds();
        requireAnswerFitsTask(task, content, fileIds);

        User submitter = userService.getById(actorId);
        TaskSubmission submission = submissionRepository.save(new TaskSubmission(task, submitter, content));

        List<SubmittedFileResponse> files = new ArrayList<>();
        for (String fileId : fileIds) {
            // The same gate attaching a file to a table goes through: the actor's own, or one the
            // platform published. Somebody else's private upload never gets here (#79).
            StoredFile file = fileService.requireAttachable(fileId, actorId);
            submissionFileRepository.save(new SubmissionFile(submission.getId(), file.getId()));
            files.add(toSubmittedFile(file));
        }

        return taskMapper.toSubmissionResponse(submission, content, files);
    }

    /**
     * What came in and who has not answered, for the people running the table.
     *
     * <p>The two halves travel together because they are read as one thought - "seven of nine" - and
     * splitting them across two requests would let the screen show a roster that disagrees with the
     * answers above it.
     *
     * @param taskId  the task
     * @param actorId the actor, from the token
     * @return every answer, oldest first, plus the people still missing
     * @throws ForbiddenActionException if the actor does not run the table
     * @throws com.centraldungeon.common.exception.NotFoundException if the task is not there
     */
    @Transactional(readOnly = true)
    public TaskSubmissionsResponse listForTask(String taskId, String actorId) {
        TableTask task = tableTaskService.getLiveTask(taskId);
        requireMasterOf(task.getGameTable().getId(), actorId);

        List<TaskSubmission> submissions = submissionRepository.findByTask_IdAndDeletedAtIsNullOrderByCreatedAtAsc(taskId);
        List<TaskRecipientResponse> recipients = tableTaskService.recipientsOf(task);

        Set<String> answered = new HashSet<>();
        for (TaskSubmission submission : submissions) {
            answered.add(submission.getUser().getId());
        }
        List<TaskRecipientResponse> missing = recipients.stream()
                .filter(recipient -> !answered.contains(recipient.userId()))
                .toList();

        return new TaskSubmissionsResponse(taskId, describe(submissions), missing, recipients.size());
    }

    /**
     * Somebody's own answers to a task - what they see of what they handed in.
     *
     * <p>No membership check and none needed: the actor comes from the token and the query is keyed
     * on them, so there is no parameter that could point at anybody else's answers (#121).
     *
     * @param taskId  the task
     * @param actorId the actor, from the token
     * @return their answers, oldest first. Empty when they have not answered
     * @throws com.centraldungeon.common.exception.NotFoundException if the task is not there
     */
    @Transactional(readOnly = true)
    public List<TaskSubmissionResponse> listMine(String taskId, String actorId) {
        tableTaskService.getLiveTask(taskId);
        return describe(submissionRepository.findByTask_IdAndUser_IdAndDeletedAtIsNullOrderByCreatedAtAsc(taskId, actorId));
    }

    /** Turns answers into responses, loading every attached file in one round trip. */
    private List<TaskSubmissionResponse> describe(List<TaskSubmission> submissions) {
        if (submissions.isEmpty()) {
            return List.of();
        }
        Map<String, List<SubmittedFileResponse>> filesBySubmission = filesOf(submissions);
        return submissions.stream()
                .map(submission -> taskMapper.toSubmissionResponse(
                        submission,
                        // Sanitized on the way out as well as on the way in (#62): a row written
                        // before this gate existed still reaches the browser through here. The clean
                        // string is passed to the mapper and not written back - a read stays a read.
                        richTextSanitizer.sanitize(submission.getContent()),
                        filesBySubmission.getOrDefault(submission.getId(), List.of())))
                .toList();
    }

    /**
     * The files of a whole page of answers, in one query plus one.
     *
     * <p>A file its owner has since deleted is left out rather than shown as a broken row: the same
     * thing {@code TableFileService} does for a table's attachments, and for the same reason - an
     * owner removing a file removes it from what shows it, without those links having to be found and
     * rewritten (#25).
     */
    private Map<String, List<SubmittedFileResponse>> filesOf(List<TaskSubmission> submissions) {
        List<SubmissionFile> links = submissionFileRepository.findById_SubmissionIdInAndStatus(
                submissions.stream().map(TaskSubmission::getId).toList(), SubmissionFileStatus.Current);
        if (links.isEmpty()) {
            return Map.of();
        }
        Map<String, StoredFile> files =
                fileRepository.findAllById(links.stream().map(link -> link.getId().fileId()).distinct().toList()).stream()
                        .filter(file -> file.getStatus() == FileStatus.Current)
                        .collect(Collectors.toMap(StoredFile::getId, Function.identity()));

        Map<String, List<SubmittedFileResponse>> bySubmission = new HashMap<>();
        for (SubmissionFile link : links) {
            StoredFile file = files.get(link.getId().fileId());
            if (file != null) {
                bySubmission.computeIfAbsent(link.getId().submissionId(), key -> new ArrayList<>()).add(toSubmittedFile(file));
            }
        }
        return bySubmission;
    }

    private SubmittedFileResponse toSubmittedFile(StoredFile file) {
        return new SubmittedFileResponse(file.getId(), file.getName(), file.getMimeType(), file.getSizeBytes());
    }

    /**
     * An answer has to use a channel the task actually opened, and has to say something.
     *
     * <p>Both refusals are about the same thing: an answer that goes nowhere. Text handed to a task
     * that only takes files would be stored and never shown next to what the master asked for, and an
     * answer empty on both counts is a row claiming somebody responded when they did not - which
     * would take them off the roster of who is still missing.
     */
    private void requireAnswerFitsTask(TableTask task, @Nullable String content, List<String> fileIds) {
        boolean hasText = content != null && !content.isBlank();
        boolean hasFiles = !fileIds.isEmpty();
        if (hasText && !task.isAcceptsText()) {
            throw new InvalidRequestException("Task " + task.getId() + " does not take a written answer");
        }
        if (hasFiles && !task.isAcceptsFiles()) {
            throw new InvalidRequestException("Task " + task.getId() + " does not take files");
        }
        if (!hasText && !hasFiles) {
            throw new InvalidRequestException("An answer has to carry text, files, or both");
        }
    }

    /**
     * The membership gate (#17, #121, #135): a row in {@code masters}, not the {@code Master} role.
     *
     * @throws ForbiddenActionException if the actor does not run this table
     */
    private void requireMasterOf(String gameTableId, String actorId) {
        if (!masterService.isMasterOf(gameTableId, actorId)) {
            throw new ForbiddenActionException("User " + actorId + " does not run table " + gameTableId);
        }
    }
}
