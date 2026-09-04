package com.centraldungeon.tasks;

import com.centraldungeon.common.security.CurrentUser;
import com.centraldungeon.tasks.dto.CreateSubmissionRequest;
import com.centraldungeon.tasks.dto.TaskSubmissionResponse;
import com.centraldungeon.tasks.dto.TaskSubmissionsResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Answers to a task: handing one in, and reading what came in.
 *
 * <p>A controller of its own rather than more methods on {@code TableTaskController}, because the
 * two speak to different people about different things - one publishes what a table wants, this one
 * carries what somebody sent back. They also answer to two different authorization questions, and
 * keeping them apart is what stops those from being mixed up.
 *
 * <p><b>There is no update and no delete here, and that is the design</b> (#76): answers accumulate,
 * a second version is a second {@code POST}, and nothing overwrites what somebody already sent.
 * There is no accept or reject either - the system delivers, the people judge.
 *
 * <p>Every method is {@code isAuthenticated()} and none names a role: handing in is authorized by
 * being <b>addressed</b> by the task, and reading everything by <b>running the table</b> (#17, #121,
 * #135). The annotation is written out on each method and never inherited (regla dura 4,
 * CVE-2025-41248).
 */
@RestController
@RequestMapping("/api/v1/tasks/{taskId}/submissions")
public class TaskSubmissionController {

    /** The only collaborator: a controller never reaches a repository (regla dura 1). */
    private final TaskSubmissionService taskSubmissionService;

    /**
     * @param taskSubmissionService the service that owns answers and their rules
     */
    public TaskSubmissionController(TaskSubmissionService taskSubmissionService) {
        this.taskSubmissionService = taskSubmissionService;
    }

    /**
     * What came in and who has not answered, for the people running the table.
     *
     * @param taskId      the task
     * @param currentUser the actor, from the token; the service checks they run the table
     * @return 200 with every answer, oldest first, plus the roster of who is still missing. 403 when
     *         the actor does not run the table, 404 when the task is not there
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public TaskSubmissionsResponse listForTask(
            @PathVariable String taskId, @AuthenticationPrincipal CurrentUser currentUser) {
        return taskSubmissionService.listForTask(taskId, currentUser.userId());
    }

    /**
     * Handing in an answer.
     *
     * <p>Every call inserts a new one (#76). Sending a second version leaves the first exactly where
     * it was, which is why this is a {@code POST} to a collection and not a {@code PUT} on a resource.
     *
     * @param taskId      the task being answered
     * @param request     the written answer, the files, or both
     * @param currentUser the actor, from the token - the submitter is never named in the body (#121)
     * @return 201 with the recorded answer. 403 when the task is not addressed to the actor or a file
     *         is somebody else's, 409 {@code TASK_CLOSED} when the master closed the intake, 400 when
     *         the answer uses a channel the task does not take or is empty on both
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TaskSubmissionResponse> submit(
            @PathVariable String taskId,
            @Valid @RequestBody CreateSubmissionRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {
        TaskSubmissionResponse submitted = taskSubmissionService.submit(taskId, request, currentUser.userId());
        return ResponseEntity.created(
                        URI.create("/api/v1/tasks/" + taskId + "/submissions/" + submitted.submissionId()))
                .body(submitted);
    }

    /**
     * The actor's own answers to a task - what they see of what they handed in.
     *
     * @param taskId      the task
     * @param currentUser the actor, from the token. The query is keyed on them, so there is no
     *                    parameter that could point at somebody else's answers (#121)
     * @return 200 with their answers, oldest first. Empty when they have not answered
     */
    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    public List<TaskSubmissionResponse> listMine(
            @PathVariable String taskId, @AuthenticationPrincipal CurrentUser currentUser) {
        return taskSubmissionService.listMine(taskId, currentUser.userId());
    }
}
