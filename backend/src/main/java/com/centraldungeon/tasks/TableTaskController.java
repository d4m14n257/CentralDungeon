package com.centraldungeon.tasks;

import com.centraldungeon.common.security.CurrentUser;
import com.centraldungeon.tasks.dto.ApplicableTaskResponse;
import com.centraldungeon.tasks.dto.CreateTaskRequest;
import com.centraldungeon.tasks.dto.TaskResponse;
import com.centraldungeon.tasks.dto.UpdateTaskRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * What a table asks, from both ends: the master publishes and manages, the reader sees what applies
 * to them.
 *
 * <p>No class-level {@code @RequestMapping}, same as {@code TableSessionController}: a table's tasks
 * hang off the table ({@code /game-tables/{id}/tasks}), while acting on one addresses the task itself
 * ({@code /tasks/{id}/close}).
 *
 * <p>Every method is {@code isAuthenticated()} and none names a role. Who may publish on a concrete
 * table is <b>pertenencia</b> - a row in {@code masters} - which a {@code @PreAuthorize} cannot see,
 * so the service checks it before touching anything (#17, #121, #135). The annotation is still
 * written out on each method and never inherited (regla dura 4, CVE-2025-41248).
 */
@RestController
public class TableTaskController {

    /** The only collaborator: a controller never reaches a repository (regla dura 1). */
    private final TableTaskService tableTaskService;

    /**
     * @param tableTaskService the service that owns tasks and their rules
     */
    public TableTaskController(TableTaskService tableTaskService) {
        this.tableTaskService = tableTaskService;
    }

    /**
     * Everything the table has asked, as the people running it see it - the Peticiones tab.
     *
     * <p>A list and not a page: it is bounded by what the table's masters chose to ask, and read as
     * one board - the same criterion the calendar and the attachments use.
     *
     * @param tableId     the table
     * @param currentUser the actor, from the token; the service checks they run the table
     * @return 200 with its tasks, oldest first. 403 when the actor does not run it
     */
    @GetMapping("/api/v1/game-tables/{tableId}/tasks")
    @PreAuthorize("isAuthenticated()")
    public List<TaskResponse> listForTable(
            @PathVariable String tableId, @AuthenticationPrincipal CurrentUser currentUser) {
        return tableTaskService.listForTable(tableId, currentUser.userId());
    }

    /**
     * Publishing a task, which is also what notifies the people it is addressed to (#77).
     *
     * @param tableId     the table doing the asking
     * @param request     what is being asked, of whom
     * @param currentUser the actor, from the token
     * @return 201 with the published task. 403 when the actor does not run the table, 400 when the
     *         audience and the target contradict each other or neither answer channel is open
     */
    @PostMapping("/api/v1/game-tables/{tableId}/tasks")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TaskResponse> publish(
            @PathVariable String tableId,
            @Valid @RequestBody CreateTaskRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {
        TaskResponse published = tableTaskService.publish(tableId, request, currentUser.userId());
        return ResponseEntity.created(URI.create("/api/v1/tasks/" + published.taskId())).body(published);
    }

    /**
     * What this table is asking of <b>this</b> reader - the read-only list on {@code /tables/:id} and
     * {@code /my/tables/:id}.
     *
     * <p>Its own endpoint rather than a field of the table's detail, which is where the calendar
     * (F1.3) and the shared files (F1.4) travel. Those are the same for everybody who may see the
     * table; this list is not - it depends on whether the reader plays there and on who a
     * {@code Single} task names. Keeping an actor-shaped answer out of a response that is also served
     * without an actor is what stops the two from being confused for one another (#121).
     *
     * @param tableId     the table
     * @param currentUser the actor, from the token. The whole answer is about them and there is no
     *                    parameter that could name anybody else (#121)
     * @return 200 with the tasks that apply to them, oldest first. Somebody who has not applied still
     *         gets the {@code Candidates} ones: what will be asked of you is half of deciding whether
     *         to apply (#206). 404 when the table is not there
     */
    @GetMapping("/api/v1/game-tables/{tableId}/tasks/applicable")
    @PreAuthorize("isAuthenticated()")
    public List<ApplicableTaskResponse> listApplicable(
            @PathVariable String tableId, @AuthenticationPrincipal CurrentUser currentUser) {
        return tableTaskService.listApplicable(tableId, currentUser.userId());
    }

    /**
     * A master correcting a task.
     *
     * <p>It does not notify again: #77 puts the notification at publication, and a task fixed three
     * times ringing three times is how people learn to ignore the bell.
     *
     * @param taskId      the task
     * @param request     the whole state it should end in (#189)
     * @param currentUser the actor, from the token
     * @return 200 with the task after the correction. 403 when the actor does not run the table, 400
     *         on the same three incoherences publishing refuses, 404 when the task is not there
     */
    @PatchMapping("/api/v1/tasks/{taskId}")
    @PreAuthorize("isAuthenticated()")
    public TaskResponse update(
            @PathVariable String taskId,
            @Valid @RequestBody UpdateTaskRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return tableTaskService.update(taskId, request, currentUser.userId());
    }

    /**
     * A master closing the intake.
     *
     * <p>What was already handed in stays readable - closing ends the intake, it does not erase the
     * history (#76).
     *
     * @param taskId      the task
     * @param currentUser the actor, from the token
     * @return 200 with the task, now closed. 403 when the actor does not run the table, 404 when the
     *         task is not there
     */
    @PostMapping("/api/v1/tasks/{taskId}/close")
    @PreAuthorize("isAuthenticated()")
    public TaskResponse close(@PathVariable String taskId, @AuthenticationPrincipal CurrentUser currentUser) {
        return tableTaskService.close(taskId, currentUser.userId());
    }
}
