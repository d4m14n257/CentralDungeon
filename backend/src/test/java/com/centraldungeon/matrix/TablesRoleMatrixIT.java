package com.centraldungeon.matrix;

import static com.centraldungeon.matrix.MatrixActor.*;
import static com.centraldungeon.matrix.MatrixCell.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.centraldungeon.registrations.TableRegistrationStatus;
import com.centraldungeon.tables.GameTable;
import com.centraldungeon.tables.GameTableStatus;
import com.centraldungeon.tables.MasterType;
import com.centraldungeon.tables.TableSession;
import com.centraldungeon.tables.TableSessionStatus;
import com.centraldungeon.users.PlatformRole;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The role matrix of the {@code tables} feature: the game tables, their life cycle, their masters, the
 * weekly schedule, the sessions and the table types. Thirty-five endpoints.
 */
class TablesRoleMatrixIT extends AbstractRoleMatrixIT {

    /** A body for the endpoints that take only a justification. */
    private static final String JUSTIFICATION = "{\"justification\": \"role matrix\"}";

    /** One main row per endpoint, plus its variants. */
    static final List<MatrixRow> ROWS = List.of(
            blocked(MatrixRow.of("GET /api/v1/game-tables", w -> {
                w.remember("open", w.otherTable("open", GameTableStatus.Opened).getId());
                w.remember("never public", w.otherTable("never public", GameTableStatus.Draft).getId());
                GameTable directed = w.otherTable("directed", GameTableStatus.Opened);
                w.master(directed, w.me(), MasterType.Secondary);
                w.remember("directed", directed.getId());
                GameTable vetoed = w.otherTable("vetoed", GameTableStatus.Opened);
                w.registration(vetoed, w.me(), TableRegistrationStatus.Blocked);
                w.remember("vetoed", vetoed.getId());
                return MatrixRequest.path();
            }))
                    .cell(denied(401), ANONYMOUS)
                    .cell(allowed(200, (w, body) -> assertThat(body).contains(w.recall("open"))
                            .doesNotContain(w.recall("directed"), w.recall("never public"), w.recall("vetoed"))),
                            PLAYER, MASTER, ADMIN, OWNER, PRIMARY, SECONDARY, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER),

            guarded(MatrixRow.of("GET /api/v1/game-tables/admin", w -> {
                w.remember("draft", w.otherTable("draft", GameTableStatus.Draft).getId());
                w.remember("deleted", w.otherTable("deleted", GameTableStatus.Deleted).getId());
                return MatrixRequest.path();
            }))
                    .cell(denied(403), PLAYER, MASTER, PRIMARY)
                    .cell(allowed(200, (w, body) -> assertThat(body).contains(w.recall("draft")).doesNotContain(w.recall("deleted"))),
                            ADMIN, OWNER),

            blocked(MatrixRow.of("GET /api/v1/game-tables/{id}", w -> MatrixRequest.path(w.table().getId())))
                    .cell(denied(401), ANONYMOUS)
                    .cell(allowed(200), PLAYER, MASTER, ADMIN, OWNER, PRIMARY, SECONDARY, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER),

            guarded(MatrixRow.of("GET /api/v1/game-tables/{id}/managed", w -> MatrixRequest.path(w.table().getId())))
                    .cell(denied(403), PLAYER, MASTER, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER)
                    .cell(allowed(200), ADMIN, OWNER, PRIMARY, SECONDARY),

            guarded(MatrixRow.of("GET /api/v1/game-tables/{id}/status-history", w -> {
                GameTable table = w.table();
                w.statusChange(table, GameTableStatus.Preparation, GameTableStatus.Opened, w.user(PRIMARY));
                return MatrixRequest.path(table.getId());
            }))
                    .cell(denied(403), PLAYER, MASTER, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER)
                    .cell(allowed(200), ADMIN, OWNER, PRIMARY, SECONDARY),

            guarded(MatrixRow.of("GET /api/v1/game-tables/mine", w -> {
                GameTable live = w.otherTable("live", GameTableStatus.Opened);
                w.registration(live, w.me(), TableRegistrationStatus.Player);
                w.remember("live", live.getId());
                GameTable paused = w.otherTable("paused", GameTableStatus.PauseRequested);
                w.registration(paused, w.me(), TableRegistrationStatus.Player);
                w.remember("paused", paused.getId());
                GameTable applied = w.otherTable("applied", GameTableStatus.Opened);
                w.registration(applied, w.me(), TableRegistrationStatus.Candidate);
                w.remember("applied", applied.getId());
                GameTable over = w.otherTable("over", GameTableStatus.Finished);
                w.registration(over, w.me(), TableRegistrationStatus.Player);
                w.remember("over", over.getId());
                w.remember("foreign", w.otherTable("foreign", GameTableStatus.Opened).getId());
                return MatrixRequest.path();
            }))
                    .cell(allowed(200, (w, body) -> assertThat(body).contains(w.recall("live"), w.recall("paused"))
                            .doesNotContain(w.recall("applied"), w.recall("over"), w.recall("foreign"))),
                            PLAYER, MASTER, ADMIN, OWNER, TABLE_PLAYER, CANDIDATE),

            guarded(MatrixRow.of("GET /api/v1/game-tables/mine/history", w -> {
                GameTable finished = w.otherTable("finished", GameTableStatus.Finished);
                w.registration(finished, w.me(), TableRegistrationStatus.Player);
                w.remember("finished", finished.getId());
                GameTable canceled = w.otherTable("canceled", GameTableStatus.Canceled);
                w.registration(canceled, w.me(), TableRegistrationStatus.Player);
                w.remember("canceled", canceled.getId());
                GameTable live = w.otherTable("live", GameTableStatus.Opened);
                w.registration(live, w.me(), TableRegistrationStatus.Player);
                w.remember("live", live.getId());
                w.remember("foreign", w.otherTable("foreign", GameTableStatus.Finished).getId());
                return MatrixRequest.path();
            }))
                    .cell(allowed(200, (w, body) -> assertThat(body).contains(w.recall("finished"), w.recall("canceled"))
                            .doesNotContain(w.recall("live"), w.recall("foreign"))),
                            PLAYER, MASTER, ADMIN, OWNER, TABLE_PLAYER),

            guarded(MatrixRow.of("GET /api/v1/game-tables/managed", w -> {
                GameTable mine = w.otherTable("mine", GameTableStatus.Opened);
                w.master(mine, w.me(), MasterType.Secondary);
                w.remember("mine", mine.getId());
                w.remember("theirs", w.otherTable("theirs", GameTableStatus.Opened).getId());
                GameTable gone = w.otherTable("gone", GameTableStatus.Opened);
                w.removedMaster(gone, w.me());
                w.remember("gone", gone.getId());
                return MatrixRequest.path();
            }))
                    .cell(allowed(200, (w, body) -> assertThat(body).contains(w.recall("mine"))
                            .doesNotContain(w.recall("theirs"), w.recall("gone"))),
                            PLAYER, MASTER, ADMIN, OWNER, PRIMARY, SECONDARY, REMOVED_SECONDARY),
            MatrixRow.variant("removed co-master and nothing else", "GET /api/v1/game-tables/managed", w -> {
                GameTable gone = w.otherTable("gone", GameTableStatus.Opened);
                w.removedMaster(gone, w.me());
                w.remember("gone", gone.getId());
                return MatrixRequest.path();
            })
                    .cell(allowed(200, (w, body) -> assertThat(body).doesNotContain(w.recall("gone"))), MASTER),

            guarded(MatrixRow.of("POST /api/v1/game-tables", w -> MatrixRequest.path().json(w.tableJson())))
                    .cell(denied(403), PLAYER, ADMIN, OWNER, PRIMARY, SECONDARY)
                    .cell(allowed(201), MASTER, PLAYER_MASTER, MASTER_ADMIN, MASTER_OWNER),

            guarded(MatrixRow.of("PUT /api/v1/game-tables/{id}",
                    w -> MatrixRequest.path(w.table(GameTableStatus.Draft).getId()).json(w.tableJson())))
                    .cell(denied(403), PLAYER, MASTER, ADMIN, OWNER, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER)
                    .cell(allowed(200), PRIMARY, SECONDARY),
            MatrixRow.variant("table not in Draft", "PUT /api/v1/game-tables/{id}",
                    w -> MatrixRequest.path(w.table(GameTableStatus.Opened).getId()).json(w.tableJson()))
                    .cell(denied(409), PRIMARY),

            guarded(MatrixRow.of("PUT /api/v1/game-tables/{id}/admin-edit",
                    w -> MatrixRequest.path(w.table().getId()).json(w.tableJson())))
                    .cell(denied(403), PLAYER, MASTER, PRIMARY, SECONDARY, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER)
                    .cell(allowed(200), ADMIN, OWNER),
            MatrixRow.variant("capacity below the players", "PUT /api/v1/game-tables/{id}/admin-edit", w -> {
                GameTable table = w.table();
                w.registration(table, w.person("first player"), TableRegistrationStatus.Player);
                w.registration(table, w.person("second player"), TableRegistrationStatus.Player);
                return MatrixRequest.path(table.getId()).json(w.tableJson(1));
            })
                    .cell(denied(409, "CAPACITY_BELOW_PLAYERS"), ADMIN, OWNER),

            guarded(MatrixRow.of("POST /api/v1/game-tables/unassigned",
                    w -> MatrixRequest.path().json("{\"name\": \"" + w.uniqueName("Table") + "\"}")))
                    .cell(denied(403), PLAYER, MASTER)
                    .cell(allowed(201), ADMIN, OWNER),

            guarded(MatrixRow.of("POST /api/v1/game-tables/{id}/assign-masters", w -> {
                GameTable table = w.table(GameTableStatus.Unassigned);
                return MatrixRequest.path(table.getId())
                        .json("{\"primaryUserId\": \"" + w.person("new primary").getId() + "\", \"secondaryUserIds\": []}");
            }))
                    .cell(denied(403), PLAYER, MASTER)
                    .cell(allowed(200), ADMIN, OWNER),

            guarded(MatrixRow.of("POST /api/v1/game-tables/{id}/approve",
                    w -> MatrixRequest.path(w.table(GameTableStatus.Preparation).getId())))
                    .cell(denied(403), PLAYER, MASTER, PRIMARY, SECONDARY, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER)
                    .cell(allowed(200), ADMIN, OWNER),
            MatrixRow.variant("reserved by another admin", "POST /api/v1/game-tables/{id}/approve", w -> {
                GameTable table = w.table(GameTableStatus.Preparation);
                w.claim(table, w.person("other admin", PlatformRole.ADMIN));
                return MatrixRequest.path(table.getId());
            })
                    .cell(denied(409, "ITEM_ALREADY_CLAIMED"), ADMIN, OWNER),

            guarded(MatrixRow.of("POST /api/v1/game-tables/{id}/request-changes",
                    w -> MatrixRequest.path(w.table(GameTableStatus.Preparation).getId()).json(JUSTIFICATION)))
                    .cell(denied(403), PLAYER, MASTER, PRIMARY, SECONDARY, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER)
                    .cell(allowed(200), ADMIN, OWNER),

            guarded(MatrixRow.of("POST /api/v1/game-tables/{id}/submit",
                    w -> MatrixRequest.path(w.table(GameTableStatus.Draft).getId())))
                    .cell(denied(403), PLAYER, MASTER, ADMIN, OWNER, SECONDARY, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER)
                    .cell(allowed(200), PRIMARY),

            guarded(MatrixRow.of("POST /api/v1/game-tables/{id}/resubmit",
                    w -> MatrixRequest.path(w.table(GameTableStatus.ChangesRequested).getId())))
                    .cell(denied(403), PLAYER, MASTER, ADMIN, OWNER, SECONDARY, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER)
                    .cell(allowed(200), PRIMARY),

            guarded(MatrixRow.of("POST /api/v1/game-tables/{id}/start",
                    w -> MatrixRequest.path(w.table(GameTableStatus.Opened).getId())))
                    .cell(denied(403), PLAYER, MASTER, ADMIN, OWNER, SECONDARY, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER)
                    .cell(allowed(200), PRIMARY),

            guarded(MatrixRow.of("POST /api/v1/game-tables/{id}/finish",
                    w -> MatrixRequest.path(w.table(GameTableStatus.InProgress).getId())))
                    .cell(denied(403), PLAYER, MASTER, ADMIN, OWNER, SECONDARY, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER)
                    .cell(allowed(200), PRIMARY),

            guarded(MatrixRow.of("POST /api/v1/game-tables/{id}/cancel",
                    w -> MatrixRequest.path(w.table(GameTableStatus.Opened).getId()).json(JUSTIFICATION)))
                    .cell(denied(403), PLAYER, MASTER, SECONDARY, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER)
                    .cell(allowed(200), ADMIN, OWNER, PRIMARY),

            guarded(MatrixRow.of("POST /api/v1/game-tables/{id}/request-pause",
                    w -> MatrixRequest.path(w.table(GameTableStatus.InProgress).getId()).json(JUSTIFICATION)))
                    .cell(denied(403), PLAYER, MASTER, ADMIN, OWNER, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER)
                    .cell(allowed(200), PRIMARY, SECONDARY),

            guarded(MatrixRow.of("POST /api/v1/game-tables/{id}/pause",
                    w -> MatrixRequest.path(w.table(GameTableStatus.InProgress).getId()).json(JUSTIFICATION)))
                    .cell(denied(403), PLAYER, MASTER, PRIMARY, SECONDARY, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER)
                    .cell(allowed(200), ADMIN, OWNER),

            guarded(MatrixRow.of("POST /api/v1/game-tables/{id}/resume",
                    w -> MatrixRequest.path(w.table(GameTableStatus.Pause).getId())))
                    .cell(denied(403), PLAYER, MASTER, SECONDARY, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER)
                    .cell(denied(403), PRIMARY)
                    .cell(allowed(200), ADMIN, OWNER),

            guarded(MatrixRow.of("DELETE /api/v1/game-tables/{id}", w -> {
                boolean admin = w.actor() == ADMIN || w.actor() == OWNER;
                return MatrixRequest.path(w.table(admin ? GameTableStatus.Unassigned : GameTableStatus.Draft).getId());
            }))
                    .cell(denied(403), PLAYER, MASTER, SECONDARY, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER)
                    .cell(allowed(204), ADMIN, OWNER, PRIMARY),

            guarded(MatrixRow.of("POST /api/v1/game-tables/{id}/masters", w -> MatrixRequest.path(w.table().getId())
                    .json("{\"userId\": \"" + w.person("new co-master").getId() + "\", \"masterType\": \"Secondary\"}")))
                    .cell(denied(403), PLAYER, MASTER, ADMIN, OWNER, SECONDARY, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER)
                    .cell(allowed(200), PRIMARY),

            guarded(MatrixRow.of("DELETE /api/v1/game-tables/{id}/masters/{userId}", w -> {
                GameTable table = w.table();
                String removedId = w.master(table, w.person("co-master"), MasterType.Secondary).getUser().getId();
                w.remember("removed", removedId);
                return MatrixRequest.path(table.getId(), removedId);
            }))
                    .cell(denied(403), PLAYER, MASTER, ADMIN, OWNER, SECONDARY, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER)
                    .cell(allowed(200, (w, body) -> assertThat(body).doesNotContain(w.recall("removed"))), PRIMARY),
            MatrixRow.variant("target is the Primary", "DELETE /api/v1/game-tables/{id}/masters/{userId}",
                    w -> MatrixRequest.path(w.table().getId(), w.user(PRIMARY).getId()))
                    .cell(denied(409), PRIMARY),

            guarded(MatrixRow.of("GET /api/v1/game-tables/{id}/master-candidates", w -> MatrixRequest.path(w.table().getId())))
                    .cell(denied(403), PLAYER, MASTER, ADMIN, OWNER, SECONDARY, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER)
                    .cell(allowed(200), PRIMARY),

            guarded(MatrixRow.of("GET /api/v1/users/me/schedule", w -> {
                GameTable directed = w.otherTable("directed", GameTableStatus.Opened);
                w.master(directed, w.me(), MasterType.Secondary);
                w.remember("directed", directed.getId());
                GameTable played = w.otherTable("played", GameTableStatus.Opened);
                w.registration(played, w.me(), TableRegistrationStatus.Player);
                w.remember("played", played.getId());
                w.remember("foreign", w.otherTable("foreign", GameTableStatus.Opened).getId());
                return MatrixRequest.path();
            }))
                    .cell(allowed(200, (w, body) -> assertThat(body).contains(w.recall("directed"), w.recall("played"))
                            .doesNotContain(w.recall("foreign"))),
                            PLAYER, MASTER, PRIMARY, TABLE_PLAYER)
                    .cell(allowed(200), ADMIN, OWNER),

            guarded(MatrixRow.of("GET /api/v1/game-tables/{tableId}/sessions", w -> {
                GameTable table = w.table();
                w.session(table, TableSessionStatus.Scheduled);
                return MatrixRequest.path(table.getId());
            }))
                    .cell(denied(403), PLAYER, MASTER, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER)
                    .cell(allowed(200), ADMIN, OWNER, PRIMARY, SECONDARY),

            guarded(MatrixRow.of("GET /api/v1/game-tables/{tableId}/sessions/mine", w -> {
                GameTable table = w.table();
                w.session(table, TableSessionStatus.Scheduled);
                return MatrixRequest.path(table.getId());
            }))
                    .cell(denied(403), PLAYER, MASTER, CANDIDATE)
                    .cell(denied(403), ADMIN, OWNER, PRIMARY, SECONDARY)
                    .cell(allowed(200), TABLE_PLAYER),

            guarded(MatrixRow.of("PATCH /api/v1/sessions/{sessionId}", w -> {
                TableSession session = w.session(w.table(), TableSessionStatus.Scheduled);
                return MatrixRequest.path(session.getId()).json("{\"scheduledAt\": \""
                        + LocalDateTime.now().plusMonths(6).withNano(0) + "\", \"notes\": \"Bring dice\"}");
            }))
                    .cell(denied(403), PLAYER, MASTER, ADMIN, OWNER, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER)
                    .cell(allowed(200), PRIMARY, SECONDARY),

            guarded(MatrixRow.of("POST /api/v1/sessions/{sessionId}/hold",
                    w -> MatrixRequest.path(w.session(w.table(), TableSessionStatus.Scheduled).getId())))
                    .cell(denied(403), PLAYER, MASTER, ADMIN, OWNER, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER)
                    .cell(allowed(200), PRIMARY, SECONDARY),

            guarded(MatrixRow.of("POST /api/v1/sessions/{sessionId}/cancel",
                    w -> MatrixRequest.path(w.session(w.table(), TableSessionStatus.Scheduled).getId())))
                    .cell(denied(403), PLAYER, MASTER, ADMIN, OWNER, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER)
                    .cell(allowed(200), PRIMARY, SECONDARY),

            guarded(MatrixRow.of("PUT /api/v1/sessions/{sessionId}/attendance", w -> {
                GameTable table = w.table();
                String player = w.registration(table, w.person("session player"), TableRegistrationStatus.Player).getUser().getId();
                TableSession session = w.session(table, TableSessionStatus.Scheduled);
                return MatrixRequest.path(session.getId())
                        .json("{\"attendance\": [{\"userId\": \"" + player + "\", \"attendance\": \"Present\"}]}");
            }))
                    .cell(denied(403), PLAYER, MASTER, ADMIN, OWNER, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER)
                    .cell(allowed(200), PRIMARY, SECONDARY),

            guarded(MatrixRow.of("GET /api/v1/table-types", w -> MatrixRequest.path()))
                    .cell(allowed(200), PLAYER, MASTER, ADMIN, OWNER));

    @Override
    List<MatrixRow> rows() {
        return ROWS;
    }

    /** The two columns almost every row shares: no token is a 401, and a blocked account does not get in. */
    private static MatrixRow guarded(MatrixRow row) {
        return blocked(row).cell(denied(401), ANONYMOUS);
    }

    /** The blocked column alone, for the rows whose anonymous cell no document fixes. */
    private static MatrixRow blocked(MatrixRow row) {
        return row.cell(denied(401), BLOCKED);
    }
}
