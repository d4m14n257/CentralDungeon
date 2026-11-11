package com.centraldungeon.matrix;

import static com.centraldungeon.matrix.MatrixActor.*;
import static com.centraldungeon.matrix.MatrixCell.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.centraldungeon.approvals.ApprovalRequestType;
import com.centraldungeon.registrations.TableRegistrationStatus;
import com.centraldungeon.tables.GameTableStatus;
import com.centraldungeon.tasks.TableTask;
import com.centraldungeon.tasks.TaskAudience;
import java.util.List;

/**
 * The role matrix of the {@code registrations} and {@code tasks} features: applications, the veto and
 * its requests, and the requests a table makes with their answers. Twenty-one endpoints.
 */
class RegistrationsTasksRoleMatrixIT extends AbstractRoleMatrixIT {

    private static final String JUSTIFICATION = "{\"justification\": \"Because the table asked\"}";

    private static final String RESOLUTION = "{\"resolutionNote\": \"Decided by the master\"}";

    private static final String TASK_BODY = "{\"title\": \"Bring a sheet\", \"audience\": \"Players\", \"acceptsText\": true,"
            + " \"acceptsFiles\": false, \"isMandatory\": false, \"fileIds\": []}";

    /** One main row per endpoint, plus its variants. */
    static final List<MatrixRow> ROWS = List.of(
            // ---------------------------------------------------------------- registrations
            MatrixRow.of("POST /api/v1/game-tables/{tableId}/registrations/{registrationId}/block",
                    w -> MatrixRequest.path(w.table().getId(), applicantRegistration(w, TableRegistrationStatus.Candidate))
                            .json(JUSTIFICATION))
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER, ADMIN, OWNER, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER, RESOURCE_OWNER)
                    .cell(denied(403, "NOT_PRIMARY_MASTER"), SECONDARY)
                    .cell(allowed(200), PRIMARY),
            MatrixRow.of("POST /api/v1/game-tables/{tableId}/registrations/{registrationId}/unblock",
                    w -> MatrixRequest.path(w.table().getId(), applicantRegistration(w, TableRegistrationStatus.Blocked))
                            .json(JUSTIFICATION))
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER, ADMIN, OWNER, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER)
                    .cell(denied(403, "NOT_PRIMARY_MASTER"), SECONDARY)
                    .cell(allowed(200), PRIMARY),
            MatrixRow.of("POST /api/v1/game-tables/{tableId}/registrations/{registrationId}/request-block",
                    w -> MatrixRequest.path(w.table().getId(), applicantRegistration(w, TableRegistrationStatus.Candidate))
                            .json(JUSTIFICATION))
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER, ADMIN, OWNER, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER)
                    .cell(allowed(200), PRIMARY)
                    .cell(allowed(200), SECONDARY),
            MatrixRow.of("GET /api/v1/game-tables/{tableId}/ban-requests", w -> {
                w.remember("own", w.approval(ApprovalRequestType.PlayerBan,
                        w.registration(w.table(), w.person("banned"), TableRegistrationStatus.Candidate).getId(),
                        w.person("requester")).getId());
                w.remember("foreign", w.approval(ApprovalRequestType.PlayerBan,
                        w.registration(w.otherTable("other", GameTableStatus.Opened), w.person("elsewhere"),
                                TableRegistrationStatus.Candidate).getId(),
                        w.person("requester")).getId());
                return MatrixRequest.path(w.table().getId());
            })
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER)
                    .cell(allowed(200, (w, body) -> assertThat(body).contains(w.recall("own")).doesNotContain(w.recall("foreign"))),
                            ADMIN, OWNER, PRIMARY, SECONDARY),
            MatrixRow.of("POST /api/v1/game-tables/{tableId}/ban-requests/{requestId}/approve",
                    w -> MatrixRequest.path(w.table().getId(), banRequest(w)).json(RESOLUTION))
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER, ADMIN, OWNER, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER)
                    .cell(denied(403, "NOT_PRIMARY_MASTER"), SECONDARY)
                    .cell(allowed(200), PRIMARY),
            MatrixRow.of("POST /api/v1/game-tables/{tableId}/ban-requests/{requestId}/reject",
                    w -> MatrixRequest.path(w.table().getId(), banRequest(w)).json(RESOLUTION))
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER, ADMIN, OWNER, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER)
                    .cell(denied(403, "NOT_PRIMARY_MASTER"), SECONDARY)
                    .cell(allowed(200), PRIMARY),
            MatrixRow.of("POST /api/v1/game-tables/{tableId}/registrations",
                    w -> MatrixRequest.path(w.table().getId()).json("{\"description\": \"I want to play\", \"fileIds\": []}"))
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(allowed(201), PLAYER, REMOVED_SECONDARY, PLAYER_MASTER, PLAYER_ADMIN, PLAYER_OWNER)
                    .cell(denied(403), MASTER, ADMIN, OWNER, PRIMARY, SECONDARY)
                    .cell(denied(409), CANDIDATE, TABLE_PLAYER),
            MatrixRow.of("GET /api/v1/game-tables/{tableId}/registrations", w -> {
                w.remember("own", w.registration(w.table(), w.person("applicant"), TableRegistrationStatus.Candidate).getId());
                w.remember("foreign", w.registration(w.otherTable("other", GameTableStatus.Opened), w.person("elsewhere"),
                        TableRegistrationStatus.Candidate).getId());
                return MatrixRequest.path(w.table().getId());
            })
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER)
                    .cell(allowed(200, (w, body) -> assertThat(body).contains(w.recall("own")).doesNotContain(w.recall("foreign"))),
                            ADMIN, OWNER, PRIMARY, SECONDARY),
            MatrixRow.of("GET /api/v1/game-tables/{tableId}/players", w -> {
                w.remember("player", w.registration(w.table(), w.person("seated"), TableRegistrationStatus.Player).getId());
                w.remember("blocked", w.registration(w.table(), w.person("vetoed"), TableRegistrationStatus.Blocked).getId());
                return MatrixRequest.path(w.table().getId());
            })
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER)
                    .cell(allowed(200, (w, body) -> assertThat(body).contains(w.recall("player")).contains(w.recall("blocked"))),
                            ADMIN, OWNER, PRIMARY, SECONDARY),
            MatrixRow.of("POST /api/v1/registrations/{registrationId}/accept",
                    w -> MatrixRequest.path(applicantRegistration(w, TableRegistrationStatus.Candidate)))
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER, ADMIN, OWNER, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER, RESOURCE_OWNER)
                    .cell(allowed(200), PRIMARY, SECONDARY),
            MatrixRow.of("POST /api/v1/registrations/{registrationId}/reject",
                    w -> MatrixRequest.path(applicantRegistration(w, TableRegistrationStatus.Candidate)).json(JUSTIFICATION))
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER, ADMIN, OWNER, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER, RESOURCE_OWNER)
                    .cell(allowed(200), PRIMARY, SECONDARY),
            MatrixRow.of("DELETE /api/v1/registrations/{registrationId}",
                    w -> MatrixRequest.path(applicantRegistration(w, TableRegistrationStatus.Candidate)))
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER, ADMIN, OWNER, PRIMARY, SECONDARY)
                    .cell(allowed(204), RESOURCE_OWNER),
            MatrixRow.variant("registration already accepted", "DELETE /api/v1/registrations/{registrationId}",
                    w -> MatrixRequest.path(applicantRegistration(w, TableRegistrationStatus.Player)))
                    .cell(denied(409), RESOURCE_OWNER),
            MatrixRow.of("GET /api/v1/registrations/mine", w -> {
                w.remember("candidate", w.registration(w.otherTable("t1", GameTableStatus.Opened), w.me(),
                        TableRegistrationStatus.Candidate).getId());
                w.remember("player", w.registration(w.otherTable("t2", GameTableStatus.Opened), w.me(),
                        TableRegistrationStatus.Player).getId());
                w.remember("rejected", w.registration(w.otherTable("t3", GameTableStatus.Opened), w.me(),
                        TableRegistrationStatus.Rejected).getId());
                w.remember("blocked", w.registration(w.otherTable("t4", GameTableStatus.Opened), w.me(),
                        TableRegistrationStatus.Blocked).getId());
                w.remember("deleted", w.registration(w.otherTable("t5", GameTableStatus.Opened), w.me(),
                        TableRegistrationStatus.Deleted).getId());
                w.remember("foreign", w.registration(w.otherTable("t6", GameTableStatus.Opened), w.person("someone else"),
                        TableRegistrationStatus.Candidate).getId());
                return MatrixRequest.path();
            })
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(allowed(200, (w, body) -> assertThat(body)
                            .contains(w.recall("candidate"), w.recall("player"), w.recall("rejected"))
                            .doesNotContain(w.recall("blocked"), w.recall("deleted"), w.recall("foreign"))),
                            PLAYER, MASTER, ADMIN, OWNER, CANDIDATE, TABLE_PLAYER, RESOURCE_OWNER),

            // ---------------------------------------------------------------- tasks
            MatrixRow.of("GET /api/v1/game-tables/{tableId}/tasks", w -> {
                w.remember("own", w.task(w.table(), TaskAudience.Players, null).getId());
                w.remember("foreign", w.task(w.otherTable("other", GameTableStatus.Opened), TaskAudience.Players, null).getId());
                return MatrixRequest.path(w.table().getId());
            })
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER)
                    .cell(allowed(200, (w, body) -> assertThat(body).contains(w.recall("own")).doesNotContain(w.recall("foreign"))),
                            ADMIN, OWNER, PRIMARY, SECONDARY),
            MatrixRow.of("POST /api/v1/game-tables/{tableId}/tasks",
                    w -> MatrixRequest.path(w.table().getId()).json(TASK_BODY))
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER, ADMIN, OWNER, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER)
                    .cell(allowed(201), PRIMARY, SECONDARY),
            MatrixRow.of("GET /api/v1/game-tables/{tableId}/tasks/applicable", w -> {
                w.remember("candidates", w.task(w.table(), TaskAudience.Candidates, null).getId());
                w.remember("players", w.task(w.table(), TaskAudience.Players, null).getId());
                w.remember("single", w.task(w.table(), TaskAudience.Single, w.person("target")).getId());
                return MatrixRequest.path(w.table().getId());
            })
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(allowed(200, (w, body) -> assertThat(body).contains(w.recall("candidates"))
                            .doesNotContain(w.recall("players"), w.recall("single"))),
                            PLAYER, MASTER, ADMIN, OWNER, PRIMARY, SECONDARY, REMOVED_SECONDARY, CANDIDATE)
                    .cell(allowed(200, (w, body) -> assertThat(body).contains(w.recall("candidates"), w.recall("players"))
                            .doesNotContain(w.recall("single"))),
                            TABLE_PLAYER),
            MatrixRow.variant("single task of the player", "GET /api/v1/game-tables/{tableId}/tasks/applicable", w -> {
                w.remember("single", w.task(w.table(), TaskAudience.Single, w.user(TABLE_PLAYER)).getId());
                return MatrixRequest.path(w.table().getId());
            })
                    .cell(allowed(200, (w, body) -> assertThat(body).contains(w.recall("single"))), TABLE_PLAYER)
                    .cell(allowed(200, (w, body) -> assertThat(body).doesNotContain(w.recall("single"))), PLAYER, CANDIDATE),
            MatrixRow.of("PATCH /api/v1/tasks/{taskId}",
                    w -> MatrixRequest.path(w.task(w.table(), TaskAudience.Players, null).getId()).json(TASK_BODY))
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER, ADMIN, OWNER, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER)
                    .cell(allowed(200), PRIMARY, SECONDARY),
            MatrixRow.of("POST /api/v1/tasks/{taskId}/close",
                    w -> MatrixRequest.path(w.task(w.table(), TaskAudience.Players, null).getId()))
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER, ADMIN, OWNER, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER)
                    .cell(allowed(200), PRIMARY, SECONDARY),
            MatrixRow.of("GET /api/v1/tasks/{taskId}/submissions", w -> {
                TableTask task = w.task(w.table(), TaskAudience.Players, null);
                w.remember("own", w.submission(task, w.user(RESOURCE_OWNER)).getId());
                return MatrixRequest.path(task.getId());
            })
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER, RESOURCE_OWNER)
                    .cell(allowed(200, (w, body) -> assertThat(body).contains(w.recall("own"))), ADMIN, OWNER, PRIMARY, SECONDARY),
            MatrixRow.of("POST /api/v1/tasks/{taskId}/submissions",
                    w -> MatrixRequest.path(w.task(w.table(), TaskAudience.Players, null).getId()).json("{\"content\": \"My answer\"}"))
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER, ADMIN, OWNER, CANDIDATE, PRIMARY, SECONDARY)
                    .cell(allowed(201), TABLE_PLAYER),
            MatrixRow.variant("task for candidates", "POST /api/v1/tasks/{taskId}/submissions",
                    w -> MatrixRequest.path(w.task(w.table(), TaskAudience.Candidates, null).getId())
                            .json("{\"content\": \"My answer\"}"))
                    .cell(allowed(201), CANDIDATE),
            MatrixRow.variant("second submission", "POST /api/v1/tasks/{taskId}/submissions", w -> {
                TableTask task = w.task(w.table(), TaskAudience.Players, null);
                w.submission(task, w.user(TABLE_PLAYER));
                return MatrixRequest.path(task.getId()).json("{\"content\": \"My answer, again\"}");
            })
                    .cell(allowed(201), TABLE_PLAYER),
            MatrixRow.of("GET /api/v1/tasks/{taskId}/submissions/mine", w -> {
                TableTask task = w.task(w.table(), TaskAudience.Players, null);
                w.remember("own", w.submission(task, w.me()).getId());
                w.remember("foreign", w.submission(task, w.person("someone else")).getId());
                return MatrixRequest.path(task.getId());
            })
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(allowed(200), PLAYER, MASTER, ADMIN, OWNER, PRIMARY)
                    .cell(allowed(200, (w, body) -> assertThat(body).contains(w.recall("own")).doesNotContain(w.recall("foreign"))),
                            TABLE_PLAYER, RESOURCE_OWNER));

    /** A registration of the account the row hands the resource to, on the main table, in the given status. */
    private static String applicantRegistration(MatrixFixtures w, TableRegistrationStatus status) {
        return w.registration(w.table(), w.user(RESOURCE_OWNER), status).getId();
    }

    /** A pending veto request about a candidate of the main table. */
    private static String banRequest(MatrixFixtures w) {
        String registrationId = w.registration(w.table(), w.person("banned"), TableRegistrationStatus.Candidate).getId();
        return w.approval(ApprovalRequestType.PlayerBan, registrationId, w.person("requester")).getId();
    }

    @Override
    List<MatrixRow> rows() {
        return ROWS;
    }
}
