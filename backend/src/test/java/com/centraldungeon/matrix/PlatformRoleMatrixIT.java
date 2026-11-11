package com.centraldungeon.matrix;

import static com.centraldungeon.matrix.MatrixActor.*;
import static com.centraldungeon.matrix.MatrixCell.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.centraldungeon.approvals.ApprovalRequestType;
import com.centraldungeon.common.security.JwtService;
import com.centraldungeon.common.security.RefreshCookieFactory;
import com.centraldungeon.notifications.NotificationRepository;
import com.centraldungeon.notifications.ReadStatus;
import com.centraldungeon.registrations.TableRegistrationStatus;
import com.centraldungeon.settings.SettingKey;
import com.centraldungeon.tables.GameTable;
import com.centraldungeon.tables.GameTableStatus;
import com.centraldungeon.users.PlatformRole;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserRoleChangeAction;
import com.centraldungeon.users.UserStatus;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * The role matrix of the platform-wide features: {@code adminqueue}, {@code approvals}, {@code auth},
 * {@code dashboard}, {@code health}, {@code notifications}, {@code profiles}, {@code settings} and
 * {@code users}. Thirty-five endpoints.
 */
class PlatformRoleMatrixIT extends AbstractRoleMatrixIT {

    /** The setting every settings row works on. */
    private static final String SETTING_KEY = "tables.max_players_cap";

    /** One main row per endpoint, plus its variants. */
    static final List<MatrixRow> ROWS = Stream.of(adminQueue(), approvals(), auth(), dashboard(), health(),
                    notifications(), profiles(), settings(), users())
            .flatMap(List::stream)
            .toList();

    @Override
    List<MatrixRow> rows() {
        return ROWS;
    }

    // ---------------------------------------------------------------- adminqueue

    private static List<MatrixRow> adminQueue() {
        return List.of(
                MatrixRow.of("GET /api/v1/admin-queue", w -> {
                    w.remember("free", w.approval(ApprovalRequestType.General, w.person("asker")).getId());
                    w.remember("reserved", w.claim(w.approval(ApprovalRequestType.General, w.person("other asker")),
                            w.person("other admin", PlatformRole.ADMIN)).getId());
                    return MatrixRequest.path();
                })
                        .cell(denied(401), ANONYMOUS)
                        .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                        .cell(denied(403), PLAYER, MASTER)
                        .cell(allowed(200, (w, body) -> assertThat(body)
                                .contains(w.recall("free")).doesNotContain(w.recall("reserved"))), ADMIN, OWNER),
                MatrixRow.of("POST /api/v1/admin-queue/{type}/{id}/claim", w -> MatrixRequest.path("approval_request",
                        w.approval(ApprovalRequestType.General, w.person("asker")).getId()))
                        .cell(denied(401), ANONYMOUS)
                        .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                        .cell(denied(403), PLAYER, MASTER)
                        .cell(allowed(200), ADMIN, OWNER),
                MatrixRow.variant("claimed by the same admin", "POST /api/v1/admin-queue/{type}/{id}/claim",
                        w -> MatrixRequest.path("approval_request",
                                w.claim(w.approval(ApprovalRequestType.General, w.person("asker")), w.me()).getId()))
                        .cell(allowed(200), ADMIN, OWNER),
                MatrixRow.variant("claimed by another admin", "POST /api/v1/admin-queue/{type}/{id}/claim",
                        w -> MatrixRequest.path("approval_request",
                                w.claim(w.approval(ApprovalRequestType.General, w.person("asker")),
                                        w.person("other admin", PlatformRole.ADMIN)).getId()))
                        .cell(denied(409, "ITEM_ALREADY_CLAIMED"), ADMIN, OWNER),
                MatrixRow.of("DELETE /api/v1/admin-queue/{type}/{id}/claim", w -> MatrixRequest.path("approval_request",
                        w.claim(w.approval(ApprovalRequestType.General, w.person("asker")), w.me()).getId()))
                        .cell(denied(401), ANONYMOUS)
                        .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                        .cell(denied(403), PLAYER, MASTER)
                        .cell(allowed(204), ADMIN, OWNER),
                MatrixRow.variant("not reserved", "DELETE /api/v1/admin-queue/{type}/{id}/claim",
                        w -> MatrixRequest.path("approval_request",
                                w.approval(ApprovalRequestType.General, w.person("asker")).getId()))
                        .cell(allowed(204), ADMIN, OWNER),
                MatrixRow.variant("reserved by another admin", "DELETE /api/v1/admin-queue/{type}/{id}/claim",
                        w -> MatrixRequest.path("approval_request",
                                w.claim(w.approval(ApprovalRequestType.General, w.person("asker")),
                                        w.person("other admin", PlatformRole.ADMIN)).getId()))
                        .cell(denied(409, "ITEM_ALREADY_CLAIMED"), ADMIN, OWNER));
    }

    // ---------------------------------------------------------------- approvals

    private static List<MatrixRow> approvals() {
        return List.of(
                MatrixRow.of("GET /api/v1/admin/requests", w -> {
                    w.remember("general", w.approval(ApprovalRequestType.General, w.person("asker")).getId());
                    String registration = w.registration(w.otherTable("banned table", GameTableStatus.Opened),
                            w.person("banned"), TableRegistrationStatus.Player).getId();
                    w.remember("ban", w.approval(ApprovalRequestType.PlayerBan, registration, w.person("vetoer")).getId());
                    return MatrixRequest.path();
                })
                        .cell(denied(401), ANONYMOUS)
                        .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                        .cell(denied(403), PLAYER, MASTER)
                        .cell(allowed(200, (w, body) -> assertThat(body).contains(w.recall("general"), w.recall("ban"))),
                                ADMIN, OWNER),
                MatrixRow.of("GET /api/v1/admin/requests/{id}",
                        w -> MatrixRequest.path(w.approval(ApprovalRequestType.General, w.user(RESOURCE_OWNER)).getId()))
                        .cell(denied(401), ANONYMOUS)
                        .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                        .cell(denied(403), PLAYER, MASTER, RESOURCE_OWNER)
                        .cell(allowed(200), ADMIN, OWNER),
                MatrixRow.of("POST /api/v1/admin/requests/{id}/approve",
                        w -> resolution(w, w.approval(ApprovalRequestType.General, w.user(RESOURCE_OWNER)).getId()))
                        .cell(denied(401), ANONYMOUS)
                        .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                        .cell(denied(403), PLAYER, MASTER, RESOURCE_OWNER)
                        .cell(allowed(200), ADMIN, OWNER),
                MatrixRow.variant("player ban", "POST /api/v1/admin/requests/{id}/approve",
                        w -> resolution(w, playerBan(w)))
                        .cell(denied(403), ADMIN, OWNER),
                MatrixRow.variant("reserved by another admin", "POST /api/v1/admin/requests/{id}/approve",
                        w -> resolution(w, w.claim(w.approval(ApprovalRequestType.General, w.person("asker")),
                                w.person("other admin", PlatformRole.ADMIN)).getId()))
                        .cell(denied(409, "ITEM_ALREADY_CLAIMED"), ADMIN, OWNER),
                MatrixRow.of("POST /api/v1/admin/requests/{id}/reject",
                        w -> resolution(w, w.approval(ApprovalRequestType.General, w.user(RESOURCE_OWNER)).getId()))
                        .cell(denied(401), ANONYMOUS)
                        .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                        .cell(denied(403), PLAYER, MASTER, RESOURCE_OWNER)
                        .cell(allowed(200), ADMIN, OWNER),
                MatrixRow.variant("player ban", "POST /api/v1/admin/requests/{id}/reject",
                        w -> resolution(w, playerBan(w)))
                        .cell(denied(403), ADMIN, OWNER),
                MatrixRow.variant("reserved by another admin", "POST /api/v1/admin/requests/{id}/reject",
                        w -> resolution(w, w.claim(w.approval(ApprovalRequestType.General, w.person("asker")),
                                w.person("other admin", PlatformRole.ADMIN)).getId()))
                        .cell(denied(409, "ITEM_ALREADY_CLAIMED"), ADMIN, OWNER),
                MatrixRow.of("POST /api/v1/requests", w -> MatrixRequest.path()
                        .json("{\"type\": \"General\", \"justification\": \"I need a hand\"}"))
                        .cell(denied(401), ANONYMOUS)
                        .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                        .cell(allowed(201), PLAYER, MASTER, ADMIN, OWNER, PLAYER_MASTER),
                MatrixRow.variant("another General pending", "POST /api/v1/requests", w -> {
                    w.approval(ApprovalRequestType.General, w.me());
                    return MatrixRequest.path().json("{\"type\": \"General\", \"justification\": \"Once more\"}");
                })
                        .cell(denied(409, "REQUEST_ALREADY_PENDING"), RESOURCE_OWNER),
                MatrixRow.variant("MasterGrant", "POST /api/v1/requests", w -> MatrixRequest.path()
                        .json("{\"type\": \"MasterGrant\", \"justification\": \"I want to run tables\"}"))
                        .cell(allowed(201), PLAYER)
                        .cell(denied(409, "MASTER_ROLE_ALREADY_HELD"), MASTER),
                MatrixRow.variant("TablePause", "POST /api/v1/requests", w -> MatrixRequest.path()
                        .json("{\"type\": \"TablePause\", \"justification\": \"Holidays\"}"))
                        .cell(denied(400), PLAYER),
                MatrixRow.variant("PlayerBan", "POST /api/v1/requests", w -> MatrixRequest.path()
                        .json("{\"type\": \"PlayerBan\", \"justification\": \"Trouble\"}"))
                        .cell(denied(400), PLAYER),
                MatrixRow.of("GET /api/v1/requests/mine", w -> {
                    w.remember("own", w.approval(ApprovalRequestType.General, w.me()).getId());
                    w.remember("foreign", w.approval(ApprovalRequestType.General, w.person("another asker")).getId());
                    return MatrixRequest.path();
                })
                        .cell(denied(401), ANONYMOUS)
                        .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                        .cell(allowed(200, (w, body) -> assertThat(body).contains(w.recall("own"))
                                .doesNotContain(w.recall("foreign"))), PLAYER, MASTER, ADMIN, OWNER, RESOURCE_OWNER));
    }

    // ---------------------------------------------------------------- auth

    private static List<MatrixRow> auth() {
        return List.of(
                MatrixRow.of("POST /api/v1/auth/refresh", MatrixFixtures::refreshRequest)
                        .cell(denied(401), ANONYMOUS)
                        .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                        .cell(allowed(200), PLAYER, MASTER, ADMIN, OWNER),
                MatrixRow.variant("bearer without the refresh cookie", "POST /api/v1/auth/refresh", w -> {
                    String csrf = UUID.randomUUID().toString();
                    return MatrixRequest.path().cookie("XSRF-TOKEN", csrf).header("X-XSRF-TOKEN", csrf);
                })
                        .cell(denied(401), PLAYER, MASTER, ADMIN, OWNER),
                MatrixRow.of("POST /api/v1/auth/logout", w -> MatrixRequest.path().cookie(RefreshCookieFactory.COOKIE_NAME,
                        w.bean(JwtService.class).issueRefreshToken(w.me().getId())))
                        .cell(allowed(204), ANONYMOUS, BLOCKED)
                        .cell(allowed(204), PLAYER, MASTER, ADMIN, OWNER));
    }

    // ---------------------------------------------------------------- dashboard

    private static List<MatrixRow> dashboard() {
        return List.of(
                MatrixRow.of("GET /api/v1/master/dashboard", w -> {
                    w.remember("own", w.table().getId());
                    w.registration(w.table(), w.person("waiting"), TableRegistrationStatus.Candidate);
                    w.remember("foreign", w.otherTable("someone else's", GameTableStatus.Opened).getId());
                    w.registration(w.otherTable("someone else's", GameTableStatus.Opened), w.person("waiting elsewhere"),
                            TableRegistrationStatus.Candidate);
                    return MatrixRequest.path();
                })
                        .cell(denied(401), ANONYMOUS)
                        .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                        .cell(allowed(200, (w, body) -> assertThat(body)
                                .doesNotContain(w.recall("own"), w.recall("foreign"))), PLAYER, ADMIN, OWNER, REMOVED_SECONDARY)
                        .cell(allowed(200), MASTER, MASTER_ADMIN)
                        .cell(allowed(200, (w, body) -> assertThat(body).contains(w.recall("own"))
                                .doesNotContain(w.recall("foreign"))), PRIMARY, SECONDARY));
    }

    // ---------------------------------------------------------------- health

    private static List<MatrixRow> health() {
        return List.of(
                MatrixRow.of("GET /api/v1/health", w -> MatrixRequest.path())
                        .cell(allowed(200), ANONYMOUS, PLAYER, MASTER, ADMIN, OWNER)
                        .cell(allowed(200), BLOCKED));
    }

    // ---------------------------------------------------------------- notifications

    private static List<MatrixRow> notifications() {
        return List.of(
                MatrixRow.of("GET /api/v1/notifications", w -> {
                    w.remember("own", w.notification(w.me()).getId());
                    w.remember("foreign", w.notification(w.person("somebody")).getId());
                    return MatrixRequest.path();
                })
                        .cell(denied(401), ANONYMOUS)
                        .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                        .cell(allowed(200, (w, body) -> assertThat(body).contains(w.recall("own"))
                                .doesNotContain(w.recall("foreign"))), PLAYER, MASTER, ADMIN, OWNER, RESOURCE_OWNER),
                MatrixRow.of("PATCH /api/v1/notifications/{id}/read",
                        w -> MatrixRequest.path(w.notification(w.user(RESOURCE_OWNER)).getId()))
                        .cell(denied(401), ANONYMOUS)
                        .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                        .cell(denied(403), PLAYER, MASTER, ADMIN, OWNER)
                        .cell(allowed(204), RESOURCE_OWNER),
                MatrixRow.of("PATCH /api/v1/notifications/read-all", w -> {
                    w.notification(w.me());
                    w.remember("foreign", w.notification(w.person("somebody")).getId());
                    return MatrixRequest.path();
                })
                        .cell(denied(401), ANONYMOUS)
                        .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                        .cell(allowed(204, (w, body) -> assertThat(w.bean(NotificationRepository.class)
                                .findById(w.recall("foreign")).orElseThrow().getReadStatus()).isEqualTo(ReadStatus.Unread)),
                                PLAYER, MASTER, ADMIN, OWNER, RESOURCE_OWNER));
    }

    // ---------------------------------------------------------------- profiles

    private static List<MatrixRow> profiles() {
        return List.of(
                MatrixRow.of("GET /api/v1/users/me/profile", w -> MatrixRequest.path())
                        .cell(denied(401), ANONYMOUS)
                        .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                        .cell(allowed(200, (w, body) -> assertThat(body).doesNotContain("karma").doesNotContain("comments")),
                                PLAYER, MASTER, ADMIN, OWNER),
                MatrixRow.of("GET /api/v1/users/{id}/profile", w -> MatrixRequest.path(profileSubject(w).getId()))
                        .cell(denied(401), ANONYMOUS)
                        .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                        .cell(denied(404), PLAYER, MASTER, REMOVED_SECONDARY, CANDIDATE)
                        .cell(allowed(200), ADMIN, OWNER, PRIMARY, SECONDARY, TABLE_PLAYER)
                        .cell(allowed(200), RESOURCE_OWNER),
                MatrixRow.variant("subject is the Primary of an Opened table", "GET /api/v1/users/{id}/profile", w -> {
                    GameTable subjectTable = w.otherTable("subject table", GameTableStatus.Opened);
                    return MatrixRequest.path(w.primaryOf(subjectTable).getId());
                })
                        .cell(allowed(200), PLAYER, MASTER, CANDIDATE),
                MatrixRow.of("GET /api/v1/users/{id}/tables", w -> {
                    GameTable subjectTable = w.otherTable("subject table", GameTableStatus.Opened);
                    User subject = w.primaryOf(subjectTable);
                    w.remember("own table", subjectTable.getId());
                    w.remember("applied", w.registration(w.otherTable("host table", GameTableStatus.Opened), subject,
                            TableRegistrationStatus.Candidate).getGameTable().getId());
                    return MatrixRequest.path(subject.getId());
                })
                        .cell(denied(401), ANONYMOUS)
                        .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                        .cell(denied(403), PLAYER, MASTER, RESOURCE_OWNER, PRIMARY)
                        .cell(allowed(200, (w, body) -> assertThat(body).contains(w.recall("own table"), w.recall("applied"))),
                                ADMIN, OWNER),
                MatrixRow.of("GET /api/v1/users/{id}/finished-tables", w -> {
                    User subject = profileSubject(w);
                    w.remember("finished", w.registration(w.otherTable("finished table", GameTableStatus.Finished), subject,
                            TableRegistrationStatus.Player).getGameTable().getId());
                    return MatrixRequest.path(subject.getId());
                })
                        .cell(denied(401), ANONYMOUS)
                        .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                        .cell(denied(404), PLAYER, MASTER, REMOVED_SECONDARY, CANDIDATE)
                        .cell(allowed(200, (w, body) -> assertThat(body).contains(w.recall("finished"))),
                                ADMIN, OWNER, PRIMARY, SECONDARY, TABLE_PLAYER)
                        .cell(allowed(200), RESOURCE_OWNER));
    }

    // ---------------------------------------------------------------- settings

    private static List<MatrixRow> settings() {
        return List.of(
                MatrixRow.of("GET /api/v1/admin/settings", w -> MatrixRequest.path())
                        .cell(denied(401), ANONYMOUS)
                        .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                        .cell(denied(403), PLAYER, MASTER)
                        .cell(allowed(200), ADMIN, OWNER),
                MatrixRow.of("PUT /api/v1/admin/settings/{key}", w -> MatrixRequest.path(SETTING_KEY)
                        .json("{\"value\": 20, \"justification\": \"The community grew\"}"))
                        .cell(denied(401), ANONYMOUS)
                        .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                        .cell(denied(403), PLAYER, MASTER)
                        .cell(allowed(200), ADMIN, OWNER),
                MatrixRow.of("GET /api/v1/admin/settings/{key}/history", w -> {
                    w.settingChange(SettingKey.TABLES_MAX_PLAYERS_CAP, "20", w.person("changer", PlatformRole.ADMIN));
                    return MatrixRequest.path(SETTING_KEY);
                })
                        .cell(denied(401), ANONYMOUS)
                        .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                        .cell(denied(403), PLAYER, MASTER)
                        .cell(allowed(200), ADMIN, OWNER),
                MatrixRow.of("GET /api/v1/settings/limits", w -> MatrixRequest.path())
                        .cell(denied(401), ANONYMOUS)
                        .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                        .cell(allowed(200), PLAYER, MASTER, ADMIN, OWNER));
    }

    // ---------------------------------------------------------------- users

    private static List<MatrixRow> users() {
        return List.of(
                MatrixRow.of("GET /api/v1/admin/users", w -> {
                    w.remember("other", w.person("somebody else").getId());
                    return MatrixRequest.path();
                })
                        .cell(denied(401), ANONYMOUS)
                        .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                        .cell(denied(403), PLAYER, MASTER, PLAYER_MASTER, PRIMARY)
                        .cell(allowed(200, (w, body) -> assertThat(body).contains(w.recall("other"))),
                                ADMIN, OWNER, PLAYER_ADMIN, MASTER_ADMIN, PLAYER_OWNER, MASTER_OWNER),
                MatrixRow.of("GET /api/v1/admin/users/{id}", w -> MatrixRequest.path(w.person("target").getId()))
                        .cell(denied(401), ANONYMOUS)
                        .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                        .cell(denied(403), PLAYER, MASTER, RESOURCE_OWNER)
                        .cell(allowed(200), ADMIN, OWNER),
                MatrixRow.of("POST /api/v1/admin/users/{id}/grant-role",
                        w -> MatrixRequest.path(w.person("target").getId()).json(roleBody("Master")))
                        .cell(denied(401), ANONYMOUS)
                        .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                        .cell(denied(403), PLAYER, MASTER)
                        .cell(allowed(200), ADMIN, OWNER),
                MatrixRow.variant("role Admin", "POST /api/v1/admin/users/{id}/grant-role",
                        w -> MatrixRequest.path(w.person("target").getId()).json(roleBody("Admin")))
                        .cell(denied(403, "ROLE_GRANT_FORBIDDEN"), ADMIN)
                        .cell(allowed(200), OWNER),
                MatrixRow.variant("role Admin on another Owner", "POST /api/v1/admin/users/{id}/grant-role",
                        w -> MatrixRequest.path(w.person("target owner", PlatformRole.OWNER).getId()).json(roleBody("Admin")))
                        .cell(allowed(200), OWNER),
                MatrixRow.variant("role Admin on oneself", "POST /api/v1/admin/users/{id}/grant-role",
                        w -> MatrixRequest.path(w.me().getId()).json(roleBody("Admin")))
                        .cell(denied(409, "CANNOT_REVOKE_OWN_OWNER"), OWNER),
                MatrixRow.variant("role Owner", "POST /api/v1/admin/users/{id}/grant-role",
                        w -> MatrixRequest.path(w.person("target").getId()).json(roleBody("Owner")))
                        .cell(denied(403, "OWNER_NOT_GRANTABLE"), ADMIN, OWNER),
                MatrixRow.of("POST /api/v1/admin/users/{id}/revoke-role",
                        w -> MatrixRequest.path(w.person("target", PlatformRole.PLAYER, PlatformRole.MASTER).getId())
                                .json(roleBody("Master")))
                        .cell(denied(401), ANONYMOUS)
                        .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                        .cell(denied(403), PLAYER, MASTER)
                        .cell(allowed(200), ADMIN, OWNER),
                MatrixRow.variant("role Admin", "POST /api/v1/admin/users/{id}/revoke-role",
                        w -> MatrixRequest.path(w.person("target admin", PlatformRole.ADMIN).getId()).json(roleBody("Admin")))
                        .cell(denied(403, "ROLE_GRANT_FORBIDDEN"), ADMIN)
                        .cell(allowed(200), OWNER),
                MatrixRow.variant("role Owner", "POST /api/v1/admin/users/{id}/revoke-role",
                        w -> MatrixRequest.path(w.person("target owner", PlatformRole.OWNER).getId()).json(roleBody("Owner")))
                        .cell(denied(403, "ROLE_GRANT_FORBIDDEN"), ADMIN)
                        .cell(allowed(200), OWNER),
                MatrixRow.variant("role Owner on oneself", "POST /api/v1/admin/users/{id}/revoke-role",
                        w -> MatrixRequest.path(w.me().getId()).json(roleBody("Owner")))
                        .cell(denied(409, "CANNOT_REVOKE_OWN_OWNER"), OWNER),
                MatrixRow.of("POST /api/v1/admin/users/{id}/block",
                        w -> MatrixRequest.path(w.person("target").getId()).json(justificationBody()))
                        .cell(denied(401), ANONYMOUS)
                        .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                        .cell(denied(403), PLAYER, MASTER)
                        .cell(allowed(200), ADMIN, OWNER),
                MatrixRow.variant("target Admin", "POST /api/v1/admin/users/{id}/block",
                        w -> MatrixRequest.path(w.person("target admin", PlatformRole.ADMIN).getId()).json(justificationBody()))
                        .cell(denied(403, "CANNOT_BLOCK_PRIVILEGED"), ADMIN, OWNER),
                MatrixRow.variant("target Owner", "POST /api/v1/admin/users/{id}/block",
                        w -> MatrixRequest.path(w.person("target owner", PlatformRole.OWNER).getId()).json(justificationBody()))
                        .cell(denied(403, "CANNOT_BLOCK_PRIVILEGED"), ADMIN, OWNER),
                MatrixRow.variant("target oneself", "POST /api/v1/admin/users/{id}/block",
                        w -> MatrixRequest.path(w.me().getId()).json(justificationBody()))
                        .cell(denied(403, "CANNOT_BLOCK_PRIVILEGED"), ADMIN, OWNER),
                MatrixRow.variant("target already blocked", "POST /api/v1/admin/users/{id}/block", w -> {
                    User target = w.person("target");
                    w.block(target);
                    return MatrixRequest.path(target.getId()).json(justificationBody());
                })
                        .cell(denied(409, "USER_ALREADY_BLOCKED"), ADMIN, OWNER),
                MatrixRow.of("POST /api/v1/admin/users/{id}/unblock", w -> {
                    User target = w.person("target");
                    w.block(target);
                    return MatrixRequest.path(target.getId()).json(justificationBody());
                })
                        .cell(denied(401), ANONYMOUS)
                        .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                        .cell(denied(403), PLAYER, MASTER)
                        .cell(allowed(200), ADMIN, OWNER),
                MatrixRow.variant("target not blocked", "POST /api/v1/admin/users/{id}/unblock",
                        w -> MatrixRequest.path(w.person("target").getId()).json(justificationBody()))
                        .cell(denied(409, "USER_NOT_BLOCKED"), ADMIN, OWNER),
                MatrixRow.of("GET /api/v1/admin/users/{id}/history", w -> {
                    User target = w.person("target");
                    User by = w.person("changer", PlatformRole.ADMIN);
                    w.roleChange(target, PlatformRole.MASTER, UserRoleChangeAction.Granted, by);
                    w.accountStatusChange(target, UserStatus.Allowed, UserStatus.Blocked, by);
                    return MatrixRequest.path(target.getId());
                })
                        .cell(denied(401), ANONYMOUS)
                        .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                        .cell(denied(403), PLAYER, MASTER, RESOURCE_OWNER)
                        .cell(allowed(200), ADMIN, OWNER),
                MatrixRow.of("GET /api/v1/users/me", w -> MatrixRequest.path())
                        .cell(denied(401), ANONYMOUS)
                        .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                        .cell(allowed(200, (w, body) -> assertThat(body).doesNotContain("karma")),
                                PLAYER, MASTER, ADMIN, OWNER)
                        .cell(allowed(200, (w, body) -> assertThat((Boolean) JsonPath.read(body, "$.hasManagedTables")).isTrue()),
                                PRIMARY)
                        .cell(allowed(200, (w, body) -> assertThat((Boolean) JsonPath.read(body, "$.hasManagedTables")).isFalse()),
                                REMOVED_SECONDARY),
                MatrixRow.of("GET /api/v1/users/search", w -> {
                    w.remember("findable", w.person("findable").getId());
                    w.remember("hidden", w.person("hidden").getId());
                    w.block(w.person("hidden"));
                    return MatrixRequest.path();
                })
                        .cell(denied(401), ANONYMOUS)
                        .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                        .cell(denied(403), PLAYER, MASTER, PRIMARY)
                        .cell(allowed(200, (w, body) -> assertThat(body).contains(w.recall("findable"))
                                .doesNotContain(w.recall("hidden"))), ADMIN, OWNER),
                MatrixRow.of("POST /api/v1/users/me/onboarding", w -> {
                    if (w.actor() == RESOURCE_OWNER) {
                        w.withoutOnboarding(w.me());
                    }
                    return MatrixRequest.path().json(profileBody());
                })
                        .cell(denied(401), ANONYMOUS)
                        .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                        .cell(denied(409, "ONBOARDING_ALREADY_COMPLETED"), PLAYER, MASTER, ADMIN, OWNER)
                        .cell(allowed(200), RESOURCE_OWNER),
                MatrixRow.of("PATCH /api/v1/users/me", w -> {
                    if (w.actor() == RESOURCE_OWNER) {
                        w.withoutOnboarding(w.me());
                    }
                    return MatrixRequest.path().json(profileBody());
                })
                        .cell(denied(401), ANONYMOUS)
                        .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                        .cell(allowed(200), PLAYER, MASTER, ADMIN, OWNER)
                        .cell(denied(409, "ONBOARDING_PENDING"), RESOURCE_OWNER));
    }

    // ---------------------------------------------------------------- helpers

    /** The body of approving or rejecting a request, aimed at the request with that id. */
    private static MatrixRequest resolution(MatrixFixtures w, String requestId) {
        return MatrixRequest.path(requestId).json("{\"resolutionNote\": \"Reviewed by the role matrix\"}");
    }

    /** A pending ban request, about a player registered at another table. */
    private static String playerBan(MatrixFixtures w) {
        String registration = w.registration(w.otherTable("banned table", GameTableStatus.Opened), w.person("banned"),
                TableRegistrationStatus.Player).getId();
        return w.approval(ApprovalRequestType.PlayerBan, registration, w.person("vetoer")).getId();
    }

    /**
     * The person whose profile is read: unrelated for the base actors, and related to the main table
     * the way the acting relational actor needs, so what each of them gets depends on the relation.
     * The resource owner reads their own.
     */
    private static User profileSubject(MatrixFixtures w) {
        MatrixActor actor = w.actor();
        if (actor == RESOURCE_OWNER) {
            return w.me();
        }
        User subject = w.person("subject");
        if (actor == PRIMARY || actor == SECONDARY) {
            w.registration(w.table(), subject, TableRegistrationStatus.Candidate);
        } else if (actor.belongsToTable()) {
            w.registration(w.table(), subject, TableRegistrationStatus.Player);
        }
        return subject;
    }

    private static String roleBody(String role) {
        return "{\"role\": \"" + role + "\", \"justification\": \"Decided by the role matrix\"}";
    }

    private static String justificationBody() {
        return "{\"justification\": \"Decided by the role matrix\"}";
    }

    private static String profileBody() {
        return "{\"name\": \"Aria\", \"country\": \"AR\"}";
    }
}
