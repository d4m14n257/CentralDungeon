package com.centraldungeon.matrix;

import static com.centraldungeon.matrix.MatrixActor.*;
import static com.centraldungeon.matrix.MatrixCell.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.centraldungeon.features.catalogs.CatalogStatus;
import com.centraldungeon.features.catalogs.CatalogType;
import com.centraldungeon.features.catalogs.CatalogValue;
import com.centraldungeon.common.security.JwtService;
import com.centraldungeon.features.files.FileCategory;
import com.centraldungeon.features.files.FileStatus;
import com.centraldungeon.features.files.FileType;
import com.centraldungeon.features.files.StoredFile;
import com.centraldungeon.features.files.StoredFileRepository;
import com.centraldungeon.features.files.TableFile;
import com.centraldungeon.features.files.TableFileId;
import com.centraldungeon.features.files.TableFileRepository;
import com.centraldungeon.features.files.TableFileStatus;
import com.centraldungeon.features.registrations.TableRegistrationStatus;
import com.centraldungeon.features.tables.GameTable;
import com.centraldungeon.features.tables.GameTableStatus;
import com.centraldungeon.features.tasks.TableTask;
import com.centraldungeon.features.tasks.TableTaskRepository;
import com.centraldungeon.features.tasks.TaskAudience;
import com.centraldungeon.features.tasks.TaskFile;
import com.centraldungeon.features.tasks.TaskFileId;
import com.centraldungeon.features.tasks.TaskFileRepository;
import com.centraldungeon.features.tasks.TaskFileStatus;
import com.centraldungeon.features.tasks.TaskStatus;
import com.centraldungeon.features.tasks.TaskSubmission;
import com.centraldungeon.features.tasks.TaskSubmissionRepository;
import com.centraldungeon.features.users.PlatformRole;
import com.centraldungeon.features.users.User;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The role matrix of the {@code files} and {@code catalogs} features: uploads, the personal and the
 * platform library, a table's files, and the systems, platforms and tags with their administration.
 * Thirty-five endpoints, several of them multipart.
 */
class FilesCatalogsRoleMatrixIT extends AbstractRoleMatrixIT {

    /** The catalog the administration rows work on. */
    private static final String CATALOG = "systems";

    /** A valid body for renaming a file. */
    private static final String RENAME = "{\"name\": \"renamed.pdf\", \"keepInLibrary\": true}";

    /** The only door to a file's bytes, and the endpoint most of this class's variants are about. */
    private static final String CONTENT = "GET /api/v1/files/{fileId}/content";

    /** One main row per endpoint, plus its variants. */
    private static final List<MatrixRow> MAIN = List.of(
            // ------------------------------------------------------------ files: the platform's library
            MatrixRow.of("GET /api/v1/admin/files", w -> {
                User uploader = w.person("uploader");
                w.remember("library", w.file(uploader, FileType.Library, FileCategory.TableMaterial).getId());
                w.remember("public", w.file(uploader, FileType.Public, FileCategory.TableMaterial).getId());
                w.remember("private", w.file(uploader, FileType.Private).getId());
                w.remember("singleUse", w.file(uploader, FileType.SingleUse).getId());
                return MatrixRequest.path();
            })
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER)
                    .cell(allowed(200, (w, body) -> assertThat(body)
                                    .contains(w.recall("library"), w.recall("public"))
                                    .doesNotContain(w.recall("private"), w.recall("singleUse"))),
                            ADMIN, OWNER),
            MatrixRow.of("POST /api/v1/admin/files", w -> MatrixRequest.path()
                            .filePart("file", w.uniqueBytes())
                            .jsonPart("data", "{\"categories\": [\"TableMaterial\"], \"publish\": false}"))
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER)
                    .cell(allowed(201), ADMIN, OWNER),
            MatrixRow.of("POST /api/v1/admin/files/{fileId}/publish",
                            w -> MatrixRequest.path(w.file(owner(w), FileType.Library, FileCategory.TableMaterial).getId()))
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER, RESOURCE_OWNER)
                    .cell(allowed(200), ADMIN, OWNER),
            // A state the file is already in is a conflict, never a refusal of the admin.
            MatrixRow.variant("already published", "POST /api/v1/admin/files/{fileId}/publish",
                            w -> MatrixRequest.path(w.file(owner(w), FileType.Public, FileCategory.TableMaterial).getId()))
                    .cell(denied(409, "FILE_ALREADY_PUBLISHED"), ADMIN, OWNER),
            MatrixRow.of("POST /api/v1/admin/files/{fileId}/unpublish",
                            w -> MatrixRequest.path(w.file(owner(w), FileType.Public, FileCategory.TableMaterial).getId()))
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER, RESOURCE_OWNER)
                    .cell(allowed(200), ADMIN, OWNER),
            MatrixRow.variant("not published", "POST /api/v1/admin/files/{fileId}/unpublish",
                            w -> MatrixRequest.path(w.file(owner(w), FileType.Library, FileCategory.TableMaterial).getId()))
                    .cell(denied(409, "FILE_NOT_PUBLISHED"), ADMIN, OWNER),
            MatrixRow.of("PUT /api/v1/admin/files/{fileId}/category",
                            w -> MatrixRequest.path(w.file(owner(w), FileType.Library, FileCategory.TableMaterial).getId())
                                    .json("{\"category\": \"MasterRequest\"}"))
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER, RESOURCE_OWNER)
                    .cell(allowed(200), ADMIN, OWNER),
            MatrixRow.variant("published", "PUT /api/v1/admin/files/{fileId}/category",
                            w -> MatrixRequest.path(w.file(owner(w), FileType.Public, FileCategory.TableMaterial).getId())
                                    .json("{\"category\": \"MasterRequest\"}"))
                    .cell(denied(409, "FILE_ALREADY_PUBLISHED"), ADMIN, OWNER),
            MatrixRow.of("DELETE /api/v1/admin/files/{fileId}",
                            w -> MatrixRequest.path(w.file(owner(w), FileType.Library, FileCategory.TableMaterial).getId()))
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER, RESOURCE_OWNER)
                    .cell(allowed(204), ADMIN, OWNER),
            MatrixRow.variant("somebody's private file", "DELETE /api/v1/admin/files/{fileId}",
                            w -> MatrixRequest.path(w.file(owner(w), FileType.Private).getId()))
                    .cell(denied(404), ADMIN, OWNER),

            // ------------------------------------------------------------ files: a person's own
            MatrixRow.of("POST /api/v1/files", w -> MatrixRequest.path()
                            .filePart("file", w.uniqueBytes())
                            .jsonPart("data", "{\"fileType\": \"SingleUse\"}"))
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(allowed(201), PLAYER, MASTER, PRIMARY, SECONDARY, PLAYER_MASTER)
                    .cell(allowed(201), ADMIN, OWNER),
            MatrixRow.variant("bytes the actor already has", "POST /api/v1/files", w -> {
                        byte[] bytes = w.uniqueBytes();
                        w.file(w.me(), FileType.Private, bytes);
                        return MatrixRequest.path()
                                .filePart("file", bytes)
                                .jsonPart("data", "{\"fileType\": \"SingleUse\"}");
                    })
                    .cell(allowed(200), PLAYER, MASTER, PRIMARY, SECONDARY, PLAYER_MASTER),
            MatrixRow.variant("category PlayerApplication", "POST /api/v1/files", w -> MatrixRequest.path()
                            .filePart("file", w.uniqueBytes())
                            .jsonPart("data", "{\"fileType\": \"SingleUse\", \"fileCategory\": \"PlayerApplication\"}"))
                    .cell(allowed(201), PLAYER, PLAYER_MASTER),
            MatrixRow.variant("category TableMaterial", "POST /api/v1/files", w -> MatrixRequest.path()
                            .filePart("file", w.uniqueBytes())
                            .jsonPart("data", "{\"fileType\": \"SingleUse\", \"fileCategory\": \"TableMaterial\"}"))
                    .cell(allowed(201), MASTER)
                    .cell(denied(400), PLAYER),
            MatrixRow.variant("category Announcement", "POST /api/v1/files", w -> MatrixRequest.path()
                            .filePart("file", w.uniqueBytes())
                            .jsonPart("data", "{\"fileType\": \"SingleUse\", \"fileCategory\": \"Announcement\"}"))
                    .cell(denied(400), MASTER),
            MatrixRow.variant("fileType Public", "POST /api/v1/files", w -> MatrixRequest.path()
                            .filePart("file", w.uniqueBytes())
                            .jsonPart("data", "{\"fileType\": \"Public\"}"))
                    .cell(denied(400), PLAYER),
            MatrixRow.variant("fileType Library", "POST /api/v1/files", w -> MatrixRequest.path()
                            .filePart("file", w.uniqueBytes())
                            .jsonPart("data", "{\"fileType\": \"Library\"}"))
                    .cell(denied(400), PLAYER),
            MatrixRow.of("GET /api/v1/files/mine", w -> {
                        User me = w.me();
                        User stranger = w.person("stranger");
                        w.remember("private", w.file(me, FileType.Private).getId());
                        w.remember("singleUse", w.file(me, FileType.SingleUse).getId());
                        w.remember("foreign", w.file(stranger, FileType.Private).getId());
                        w.remember("ownPublic", w.file(me, FileType.Public, FileCategory.TableMaterial).getId());
                        w.remember("ownLibrary", w.file(me, FileType.Library, FileCategory.TableMaterial).getId());
                        return MatrixRequest.path();
                    })
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(allowed(200, (w, body) -> assertThat(body)
                                    .contains(w.recall("private"), w.recall("singleUse"))
                                    .doesNotContain(w.recall("foreign"), w.recall("ownPublic"), w.recall("ownLibrary"))),
                            PLAYER, MASTER, RESOURCE_OWNER, PRIMARY, SECONDARY)
                    .cell(allowed(200), ADMIN, OWNER),
            MatrixRow.of("GET /api/v1/files/mine/categories", w -> MatrixRequest.path())
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(allowed(200, categories("PlayerApplication", "PlayerSubmission")),
                            PLAYER, REMOVED_SECONDARY, PLAYER_ADMIN, PLAYER_OWNER)
                    .cell(allowed(200, categories("TableMaterial", "MasterRequest")), MASTER, MASTER_ADMIN, MASTER_OWNER)
                    .cell(allowed(200, categories()), ADMIN, OWNER)
                    .cell(allowed(200, categories("PlayerApplication", "PlayerSubmission", "TableMaterial", "MasterRequest")),
                            PLAYER_MASTER, PRIMARY, SECONDARY),
            MatrixRow.of("GET /api/v1/files/public", w -> {
                        User publisher = w.person("publisher");
                        w.remember("public", w.file(publisher, FileType.Public, FileCategory.TableMaterial).getId());
                        w.remember("library", w.file(publisher, FileType.Library, FileCategory.TableMaterial).getId());
                        return MatrixRequest.path().query("category", "TableMaterial");
                    })
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(allowed(200, (w, body) -> assertThat(body)
                                    .contains(w.recall("public"))
                                    .doesNotContain(w.recall("library"))),
                            PLAYER, MASTER, ADMIN, OWNER),
            // The eight ways a file is reachable, each with who must not get through it. Every refusal is
            // the one a file that does not exist gets, so none of them confirms what it denies.
            MatrixRow.of(CONTENT, w -> content(w, w.file(owner(w), FileType.Private)))
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(notThere(), PLAYER, MASTER, ADMIN, OWNER)
                    .cell(allowed(200), RESOURCE_OWNER),
            MatrixRow.variant("a file marked gone", CONTENT, w -> {
                        StoredFile file = w.file(owner(w), FileType.Private);
                        file.setStatus(FileStatus.Deleted);
                        file.setDeletedAt(LocalDateTime.now());
                        return content(w, w.bean(StoredFileRepository.class).save(file));
                    })
                    .cell(notThere(), RESOURCE_OWNER, ADMIN, OWNER),
            MatrixRow.variant("public file", CONTENT,
                            w -> content(w, w.file(owner(w), FileType.Public, FileCategory.TableMaterial)))
                    .cell(allowed(200), PLAYER, MASTER, ADMIN, OWNER, VETOED),
            // The library belongs to the admins as a team: every one of them opens what another uploaded.
            MatrixRow.variant("unpublished in the platform library", CONTENT, w -> content(w,
                            w.file(w.person("uploader", PlatformRole.ADMIN), FileType.Library, FileCategory.TableMaterial)))
                    .cell(notThere(), PLAYER, MASTER)
                    .cell(allowed(200), ADMIN, OWNER),
            MatrixRow.variant("shared by a table", CONTENT, w -> content(w, shared(w, w.table())))
                    .cell(allowed(200), PLAYER, MASTER, ADMIN, OWNER, PRIMARY, SECONDARY, REMOVED_SECONDARY, CANDIDATE,
                            TABLE_PLAYER)
                    .cell(notThere(), VETOED),
            MatrixRow.variant("shared by a table nobody was shown yet", CONTENT,
                            w -> content(w, shared(w, w.table(GameTableStatus.Draft))))
                    .cell(allowed(200), PRIMARY, SECONDARY, ADMIN, OWNER)
                    .cell(notThere(), PLAYER, MASTER, REMOVED_SECONDARY, VETOED),
            MatrixRow.variant("shared by a deleted table", CONTENT, w -> {
                        StoredFile file = shared(w, w.table(GameTableStatus.Draft));
                        w.deleted(w.table());
                        return content(w, file);
                    })
                    .cell(notThere(), PLAYER, PRIMARY, SECONDARY, ADMIN, OWNER, VETOED),
            MatrixRow.variant("shared, then taken off the table", CONTENT, w -> {
                        StoredFile file = shared(w, w.table());
                        takenOff(w, w.table(), file);
                        return content(w, file);
                    })
                    .cell(notThere(), PLAYER, TABLE_PLAYER, SECONDARY, ADMIN, OWNER),
            MatrixRow.variant("private to a table", CONTENT, w -> content(w, privateTo(w, w.table())))
                    .cell(allowed(200), PRIMARY, SECONDARY, ADMIN, OWNER)
                    .cell(notThere(), PLAYER, MASTER, CANDIDATE, TABLE_PLAYER, REMOVED_SECONDARY, VETOED),
            MatrixRow.variant("private to a table nobody was shown yet", CONTENT,
                            w -> content(w, privateTo(w, w.table(GameTableStatus.Draft))))
                    .cell(allowed(200), PRIMARY, SECONDARY, ADMIN, OWNER)
                    .cell(notThere(), PLAYER),
            MatrixRow.variant("private to a deleted table", CONTENT, w -> {
                        StoredFile file = privateTo(w, w.table(GameTableStatus.Draft));
                        w.deleted(w.table());
                        return content(w, file);
                    })
                    .cell(notThere(), PRIMARY, SECONDARY, ADMIN, OWNER),
            MatrixRow.variant("private, then taken off the table", CONTENT, w -> {
                        StoredFile file = privateTo(w, w.table());
                        takenOff(w, w.table(), file);
                        return content(w, file);
                    })
                    .cell(notThere(), SECONDARY, ADMIN, OWNER),
            MatrixRow.variant("handed in to a request", CONTENT, w -> content(w, handedIn(w, w.table())))
                    .cell(allowed(200), PRIMARY, SECONDARY, ADMIN, OWNER, RESOURCE_OWNER)
                    .cell(notThere(), PLAYER, MASTER, CANDIDATE, TABLE_PLAYER, REMOVED_SECONDARY, VETOED),
            MatrixRow.variant("handed in to another table's request", CONTENT,
                            w -> content(w, handedIn(w, w.otherTable("other", GameTableStatus.Opened))))
                    .cell(allowed(200), ADMIN, OWNER)
                    .cell(notThere(), PRIMARY, SECONDARY),
            MatrixRow.variant("handed in with an answer marked gone", CONTENT, w -> {
                        StoredFile file = w.file(owner(w), FileType.Private);
                        TaskSubmission answer = w.submission(w.task(w.table(), TaskAudience.Players, null), owner(w));
                        w.submissionFile(answer, file);
                        answer.setDeletedAt(LocalDateTime.now());
                        w.bean(TaskSubmissionRepository.class).save(answer);
                        return content(w, file);
                    })
                    .cell(notThere(), PRIMARY, ADMIN, OWNER),
            MatrixRow.variant("form of a request", CONTENT,
                            w -> content(w, form(w, w.task(w.table(), TaskAudience.Players, null))))
                    .cell(allowed(200), TABLE_PLAYER, PRIMARY, SECONDARY, ADMIN, OWNER)
                    .cell(notThere(), PLAYER, MASTER, CANDIDATE, REMOVED_SECONDARY, VETOED),
            // What a request to candidates asks is listed to anybody who may see the table, so its blank
            // opens for them too, applied or not: somebody about to apply reads what they will be asked.
            MatrixRow.variant("form of a request to candidates", CONTENT,
                            w -> content(w, form(w, w.task(w.table(), TaskAudience.Candidates, null))))
                    .cell(allowed(200), PLAYER, MASTER, CANDIDATE, TABLE_PLAYER, PRIMARY, ADMIN, OWNER)
                    .cell(notThere(), VETOED),
            MatrixRow.variant("form of a request to candidates of a table nobody was shown yet", CONTENT,
                            w -> content(w, form(w, w.task(w.table(GameTableStatus.Draft), TaskAudience.Candidates, null))))
                    .cell(allowed(200), PRIMARY, ADMIN, OWNER)
                    .cell(notThere(), PLAYER, MASTER),
            // Closing a request ends what it takes in, not what it asked: its blank is part of the history.
            MatrixRow.variant("form of a closed request", CONTENT, w -> {
                        TableTask task = w.task(w.table(), TaskAudience.Players, null);
                        task.setStatus(TaskStatus.Closed);
                        return content(w, form(w, w.bean(TableTaskRepository.class).save(task)));
                    })
                    .cell(allowed(200), TABLE_PLAYER, PRIMARY, ADMIN, OWNER)
                    .cell(notThere(), PLAYER, CANDIDATE),
            MatrixRow.variant("form of a request to the actor alone", CONTENT, w -> content(w,
                            form(w, w.task(w.table(), TaskAudience.Single, w.playerRegistration().getUser()))))
                    .cell(allowed(200), TABLE_PLAYER, PRIMARY, ADMIN, OWNER)
                    .cell(notThere(), PLAYER, CANDIDATE),
            MatrixRow.variant("form of a request to another player alone", CONTENT, w -> {
                        User target = w.person("target");
                        w.registration(w.table(), target, TableRegistrationStatus.Player);
                        return content(w, form(w, w.task(w.table(), TaskAudience.Single, target)));
                    })
                    .cell(allowed(200), PRIMARY, ADMIN, OWNER, RESOURCE_OWNER)
                    .cell(notThere(), TABLE_PLAYER, CANDIDATE),
            MatrixRow.variant("form of a request of a table nobody was shown yet", CONTENT,
                            w -> content(w, form(w, w.task(w.table(GameTableStatus.Draft), TaskAudience.Players, null))))
                    .cell(allowed(200), PRIMARY, SECONDARY, ADMIN, OWNER)
                    .cell(notThere(), PLAYER),
            MatrixRow.variant("form of a request of a deleted table", CONTENT, w -> {
                        StoredFile file = form(w, w.task(w.table(GameTableStatus.Draft), TaskAudience.Players, null));
                        w.deleted(w.table());
                        return content(w, file);
                    })
                    .cell(notThere(), PRIMARY, SECONDARY, ADMIN, OWNER),
            MatrixRow.variant("form taken off its request", CONTENT, w -> {
                        TableTask task = w.task(w.table(), TaskAudience.Players, null);
                        StoredFile file = form(w, task);
                        TaskFile link = w.bean(TaskFileRepository.class)
                                .findById(new TaskFileId(task.getId(), file.getId()))
                                .orElseThrow();
                        link.setStatus(TaskFileStatus.Detached);
                        link.setDeletedAt(LocalDateTime.now());
                        w.bean(TaskFileRepository.class).save(link);
                        return content(w, file);
                    })
                    .cell(notThere(), TABLE_PLAYER, SECONDARY),
            MatrixRow.variant("attached to an application", CONTENT,
                            w -> content(w, applied(w, w.table(), TableRegistrationStatus.Candidate)))
                    .cell(allowed(200), PRIMARY, SECONDARY, ADMIN, OWNER, RESOURCE_OWNER)
                    .cell(notThere(), PLAYER, MASTER, CANDIDATE, TABLE_PLAYER, REMOVED_SECONDARY, VETOED),
            MatrixRow.variant("attached to an application that was accepted", CONTENT,
                            w -> content(w, applied(w, w.table(), TableRegistrationStatus.Player)))
                    .cell(allowed(200), PRIMARY, SECONDARY, ADMIN, OWNER),
            MatrixRow.variant("attached to a withdrawn application", CONTENT,
                            w -> content(w, applied(w, w.table(), TableRegistrationStatus.Deleted)))
                    .cell(allowed(200), RESOURCE_OWNER)
                    .cell(notThere(), PRIMARY, SECONDARY, ADMIN, OWNER),
            MatrixRow.variant("attached to a rejected application", CONTENT,
                            w -> content(w, applied(w, w.table(), TableRegistrationStatus.Rejected)))
                    .cell(notThere(), PRIMARY, SECONDARY, ADMIN, OWNER),
            MatrixRow.variant("attached to an application to another table", CONTENT, w -> content(w,
                            applied(w, w.otherTable("other", GameTableStatus.Opened), TableRegistrationStatus.Candidate)))
                    .cell(allowed(200), ADMIN, OWNER)
                    .cell(notThere(), PRIMARY, SECONDARY),
            MatrixRow.of("PATCH /api/v1/files/{fileId}",
                            w -> content(w, w.file(owner(w), FileType.Private)).json(RENAME))
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    // Somebody's private file that no table links is theirs alone to see.
                    .cell(notThere(), PLAYER, MASTER, ADMIN, OWNER, PRIMARY, TABLE_PLAYER)
                    .cell(allowed(200), RESOURCE_OWNER),
            MatrixRow.variant("shared by a table", "PATCH /api/v1/files/{fileId}",
                            w -> content(w, shared(w, w.table())).json(RENAME))
                    .cell(denied(403), PLAYER, TABLE_PLAYER, PRIMARY, ADMIN, OWNER)
                    .cell(notThere(), VETOED),
            MatrixRow.variant("public file of the owner", "PATCH /api/v1/files/{fileId}",
                            w -> MatrixRequest.path(w.file(owner(w), FileType.Public, FileCategory.TableMaterial).getId())
                                    .json("{\"name\": \"renamed.pdf\", \"keepInLibrary\": true}"))
                    .cell(denied(403), RESOURCE_OWNER),
            MatrixRow.of("DELETE /api/v1/files/{fileId}",
                            w -> content(w, w.file(owner(w), FileType.Private)))
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(notThere(), PLAYER, MASTER, ADMIN, OWNER, PRIMARY, TABLE_PLAYER)
                    .cell(allowed(204), RESOURCE_OWNER),
            MatrixRow.variant("shared by a table", "DELETE /api/v1/files/{fileId}", w -> content(w, shared(w, w.table())))
                    .cell(denied(403), PLAYER, TABLE_PLAYER, PRIMARY, ADMIN, OWNER)
                    .cell(notThere(), VETOED),

            // ------------------------------------------------------------ files: a table's
            MatrixRow.of("GET /api/v1/game-tables/{tableId}/files", w -> {
                        User publisher = w.person("publisher");
                        w.remember("shared", w.link(w.table(), w.file(publisher, FileType.Private), false).getId().fileId());
                        w.remember("private", w.link(w.table(), w.file(publisher, FileType.Private), true).getId().fileId());
                        return MatrixRequest.path(w.table().getId());
                    })
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(allowed(200, (w, body) -> assertThat(body).contains(w.recall("shared"), w.recall("private"))),
                            ADMIN, OWNER, PRIMARY, SECONDARY)
                    .cell(denied(403), PLAYER, MASTER, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER)
                    .cell(noTable(), VETOED),
            MatrixRow.of("POST /api/v1/game-tables/{tableId}/files",
                            w -> MatrixRequest.path(w.table().getId())
                                    .json("{\"fileId\": \"" + w.file(w.person("publisher"), FileType.Public,
                                            FileCategory.TableMaterial).getId()
                                            + "\", \"tableFileType\": \"Preparation\", \"isPrivate\": false}"))
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER, ADMIN, OWNER, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER)
                    .cell(noTable(), VETOED)
                    .cell(allowed(201), PRIMARY, SECONDARY),
            // Somebody else's file the master cannot open is not there; one they can open is theirs to
            // read and still not theirs to attach.
            MatrixRow.variant("somebody else's file the master cannot open", "POST /api/v1/game-tables/{tableId}/files",
                            w -> attachBody(w, w.file(w.person("stranger"), FileType.Private)))
                    .cell(notThere(), PRIMARY, SECONDARY),
            MatrixRow.variant("somebody else's file another table shares", "POST /api/v1/game-tables/{tableId}/files",
                            w -> {
                                StoredFile file = w.file(w.person("stranger"), FileType.Private);
                                w.link(w.otherTable("other", GameTableStatus.Opened), file, false);
                                return attachBody(w, file);
                            })
                    .cell(denied(403), PRIMARY, SECONDARY),
            MatrixRow.of("PATCH /api/v1/game-tables/{tableId}/files/{fileId}", w -> MatrixRequest.path(
                                    w.table().getId(), w.link(w.table(), w.file(w.person("publisher"), FileType.Private), false)
                                            .getId().fileId())
                            .json("{\"tableFileType\": \"Preparation\", \"isPrivate\": true}"))
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER, ADMIN, OWNER, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER)
                    .cell(noTable(), VETOED)
                    .cell(allowed(200), PRIMARY, SECONDARY),
            MatrixRow.of("DELETE /api/v1/game-tables/{tableId}/files/{fileId}", w -> MatrixRequest.path(
                            w.table().getId(), w.link(w.table(), w.file(w.person("publisher"), FileType.Private), false)
                                    .getId().fileId()))
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER, ADMIN, OWNER, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER)
                    .cell(noTable(), VETOED)
                    .cell(allowed(204), PRIMARY, SECONDARY),

            // ------------------------------------------------------------ catalogs: administration
            MatrixRow.of("GET /api/v1/admin/catalogs/{type}", w -> {
                        w.remember("created", w.catalog(CatalogType.SYSTEMS, CatalogStatus.Created, null).getId());
                        w.remember("rejected", w.catalog(CatalogType.SYSTEMS, CatalogStatus.Rejected, null).getId());
                        w.remember("disabled", w.catalog(CatalogType.SYSTEMS, CatalogStatus.Disabled, null).getId());
                        w.remember("accepted", w.accepted(CatalogType.SYSTEMS).getId());
                        return MatrixRequest.path(CATALOG);
                    })
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER)
                    .cell(allowed(200, (w, body) -> assertThat(body).contains(
                                    w.recall("created"), w.recall("rejected"), w.recall("disabled"), w.recall("accepted"))),
                            ADMIN, OWNER),
            MatrixRow.of("GET /api/v1/admin/catalogs/{type}/{id}/group", w -> {
                        CatalogValue head = w.catalog(CatalogType.SYSTEMS, CatalogStatus.Accepted, null);
                        w.remember("alias", w.catalog(CatalogType.SYSTEMS, CatalogStatus.Accepted, head.getId()).getId());
                        w.remember("disabledAlias", w.catalog(CatalogType.SYSTEMS, CatalogStatus.Disabled, head.getId()).getId());
                        return MatrixRequest.path(CATALOG, head.getId());
                    })
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER)
                    .cell(allowed(200, (w, body) -> assertThat(body).contains(w.recall("alias"), w.recall("disabledAlias"))),
                            ADMIN, OWNER),
            MatrixRow.of("POST /api/v1/admin/catalogs/{type}",
                            w -> MatrixRequest.path(CATALOG).json(nameBody(w, "System")))
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER)
                    .cell(allowed(201), ADMIN, OWNER),
            MatrixRow.of("POST /api/v1/admin/catalogs/{type}/{id}/accept",
                            w -> MatrixRequest.path(CATALOG, w.catalog(CatalogType.SYSTEMS, CatalogStatus.Created, null).getId())
                                    .json("{}"))
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER)
                    .cell(allowed(200), ADMIN, OWNER),
            MatrixRow.of("POST /api/v1/admin/catalogs/{type}/{id}/reject",
                            w -> MatrixRequest.path(CATALOG, w.catalog(CatalogType.SYSTEMS, CatalogStatus.Created, null).getId()))
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER)
                    .cell(allowed(200), ADMIN, OWNER),
            MatrixRow.of("POST /api/v1/admin/catalogs/{type}/merge", w -> MatrixRequest.path(CATALOG)
                            .json("{\"sourceCanonicalId\": \"" + w.catalog(CatalogType.SYSTEMS, CatalogStatus.Accepted, null).getId()
                                    + "\", \"targetCanonicalId\": \""
                                    + w.catalog(CatalogType.SYSTEMS, CatalogStatus.Accepted, null).getId() + "\"}"))
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER)
                    .cell(allowed(200), ADMIN, OWNER),
            MatrixRow.of("POST /api/v1/admin/catalogs/{type}/split", w -> {
                        CatalogValue head = w.catalog(CatalogType.SYSTEMS, CatalogStatus.Accepted, null);
                        CatalogValue alias = w.catalog(CatalogType.SYSTEMS, CatalogStatus.Accepted, head.getId());
                        return MatrixRequest.path(CATALOG).json("{\"memberId\": \"" + alias.getId() + "\"}");
                    })
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER)
                    .cell(allowed(200), ADMIN, OWNER),
            MatrixRow.of("POST /api/v1/admin/catalogs/{type}/{id}/reassign", w -> {
                        CatalogValue head = w.catalog(CatalogType.SYSTEMS, CatalogStatus.Accepted, null);
                        CatalogValue alias = w.catalog(CatalogType.SYSTEMS, CatalogStatus.Accepted, head.getId());
                        CatalogValue otherHead = w.catalog(CatalogType.SYSTEMS, CatalogStatus.Accepted, null);
                        return MatrixRequest.path(CATALOG, alias.getId())
                                .json("{\"canonicalId\": \"" + otherHead.getId() + "\"}");
                    })
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER)
                    .cell(allowed(200), ADMIN, OWNER),
            MatrixRow.of("POST /api/v1/admin/catalogs/{type}/{id}/promote", w -> {
                        CatalogValue head = w.catalog(CatalogType.SYSTEMS, CatalogStatus.Accepted, null);
                        return MatrixRequest.path(CATALOG, w.catalog(CatalogType.SYSTEMS, CatalogStatus.Accepted, head.getId()).getId());
                    })
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER)
                    .cell(allowed(200), ADMIN, OWNER),
            MatrixRow.of("POST /api/v1/admin/catalogs/{type}/{id}/disable",
                            w -> MatrixRequest.path(CATALOG, w.catalog(CatalogType.SYSTEMS, CatalogStatus.Accepted, null).getId())
                                    .json("{}"))
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER)
                    .cell(allowed(200), ADMIN, OWNER),
            MatrixRow.of("POST /api/v1/admin/catalogs/{type}/{id}/restore",
                            w -> MatrixRequest.path(CATALOG, w.catalog(CatalogType.SYSTEMS, CatalogStatus.Disabled, null).getId()))
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER)
                    .cell(allowed(200), ADMIN, OWNER),
            MatrixRow.of("PUT /api/v1/tags/{id}/definition",
                            w -> MatrixRequest.path(w.catalog(CatalogType.TAGS, CatalogStatus.Accepted, null).getId())
                                    .json("{\"category\": \"Theme\", \"description\": \"Fear and dread.\"}"))
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER)
                    .cell(allowed(200), ADMIN, OWNER),
            // Only the group's canonical entry stores its definition: an alias is a conflict, never a refusal.
            MatrixRow.variant("an alias", "PUT /api/v1/tags/{id}/definition", w -> {
                        CatalogValue head = w.catalog(CatalogType.TAGS, CatalogStatus.Accepted, null);
                        return MatrixRequest.path(w.catalog(CatalogType.TAGS, CatalogStatus.Accepted, head.getId()).getId())
                                .json("{\"category\": \"Theme\"}");
                    })
                    .cell(denied(409, "CATALOG_VALUE_NOT_CANONICAL"), ADMIN, OWNER),

            // ------------------------------------------------------------ catalogs: reading and proposing
            publicList("GET /api/v1/platforms", CatalogType.PLATFORMS),
            proposal("POST /api/v1/platforms", "Platform"),
            publicList("GET /api/v1/systems", CatalogType.SYSTEMS),
            proposal("POST /api/v1/systems", "System"),
            publicList("GET /api/v1/tags", CatalogType.TAGS),
            proposal("POST /api/v1/tags", "Tag"));

    /** Where the tokens of the header check come from. */
    @Autowired
    private JwtService jwtService;

    /** A table's attachments on a table the actor may not see, refused exactly as an id nobody ever used. */
    private static final List<MatrixRow> HIDDEN = Stream.of(
                    MatrixRow.hidden("GET /api/v1/game-tables/{tableId}/files", w -> MatrixRequest.path(w.table().getId()),
                            noTable()),
                    MatrixRow.hidden("POST /api/v1/game-tables/{tableId}/files", w -> MatrixRequest.path(w.table().getId())
                                    .json("{\"fileId\": \"" + w.file(w.person("publisher"), FileType.Public,
                                            FileCategory.TableMaterial).getId()
                                            + "\", \"tableFileType\": \"Preparation\", \"isPrivate\": false}"),
                            noTable()),
                    MatrixRow.hidden("PATCH /api/v1/game-tables/{tableId}/files/{fileId}", w -> MatrixRequest.path(
                                            w.table().getId(), w.file(w.person("publisher"), FileType.Private).getId())
                                    .json("{\"tableFileType\": \"Preparation\", \"isPrivate\": true}"),
                            noTable()),
                    MatrixRow.hidden("DELETE /api/v1/game-tables/{tableId}/files/{fileId}", w -> MatrixRequest.path(
                                    w.table().getId(), w.file(w.person("publisher"), FileType.Private).getId()),
                            noTable()))
            .flatMap(List::stream)
            .toList();

    /** Every row of the class. */
    static final List<MatrixRow> ROWS = Stream.concat(MAIN.stream(), HIDDEN.stream()).toList();

    @Override
    List<MatrixRow> rows() {
        return ROWS;
    }

    /**
     * The download is a file to save and never a page to render: an attachment the browser is told not
     * to sniff into something it would execute.
     */
    @Test
    void theContentLeavesAsAnAttachmentTheBrowserMayNotSniff() throws Exception {
        RoleMatrixHarness harness = harness();
        MatrixFixtures w = harness.fixtures(RESOURCE_OWNER);
        StoredFile file = w.file(owner(w), FileType.Private);

        MvcResult result = harness.mockMvc()
                .perform(get("/api/v1/files/{fileId}/content", file.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issueAccessToken(owner(w).getId())))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment;");
        assertThat(result.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    /** @return the account the row hands its file to: the cell's own account only in the RESOURCE_OWNER column */
    private static User owner(MatrixFixtures w) {
        return w.user(RESOURCE_OWNER);
    }

    /** The request for a file's content, remembering the file so a refusal can be compared with a missing one. */
    private static MatrixRequest content(MatrixFixtures w, StoredFile file) {
        return MatrixRequest.path(w.remember("file", file.getId()));
    }

    /** The request attaching a file to the table, remembering the file so a refusal can be compared with a missing one. */
    private static MatrixRequest attachBody(MatrixFixtures w, StoredFile file) {
        return MatrixRequest.path(w.table().getId())
                .json("{\"fileId\": \"" + w.remember("file", file.getId())
                        + "\", \"tableFileType\": \"Preparation\", \"isPrivate\": false}");
    }

    /** @return refused exactly as a table that does not exist is */
    private static MatrixCell noTable() {
        return absent(w -> "Table not found: " + w.table().getId());
    }

    /** @return refused exactly as a file that does not exist is: the same status, code and detail */
    private static MatrixCell notThere() {
        return denied(404, "NOT_FOUND", (w, body) -> assertThat(JsonPath.<String>read(body, "$.detail"))
                .isEqualTo("File " + w.recall("file") + " not found"));
    }

    /** @return a file of the resource owner that the table shares */
    private static StoredFile shared(MatrixFixtures w, GameTable table) {
        StoredFile file = w.file(owner(w), FileType.Private);
        w.link(table, file, false);
        return file;
    }

    /** @return a file of the resource owner that the table keeps private to its masters */
    private static StoredFile privateTo(MatrixFixtures w, GameTable table) {
        StoredFile file = w.file(owner(w), FileType.Private);
        w.link(table, file, true);
        return file;
    }

    /** Takes the file off the table, as detaching it does: the link stays, marked gone. */
    private static void takenOff(MatrixFixtures w, GameTable table, StoredFile file) {
        TableFileRepository links = w.bean(TableFileRepository.class);
        TableFile link = links.findById(new TableFileId(table.getId(), file.getId())).orElseThrow();
        link.setStatus(TableFileStatus.Deleted);
        link.setDeletedAt(LocalDateTime.now());
        links.save(link);
    }

    /** @return a file the resource owner handed in to a request of the table */
    private static StoredFile handedIn(MatrixFixtures w, GameTable table) {
        StoredFile file = w.file(owner(w), FileType.Private);
        w.submissionFile(w.submission(w.task(table, TaskAudience.Players, null), owner(w)), file);
        return file;
    }

    /** @return a file of the resource owner attached to the request as its blank */
    private static StoredFile form(MatrixFixtures w, TableTask task) {
        StoredFile file = w.file(owner(w), FileType.Private);
        w.taskFile(task, file);
        return file;
    }

    /** @return a file the resource owner attached to their application to the table, in that status */
    private static StoredFile applied(MatrixFixtures w, GameTable table, TableRegistrationStatus status) {
        StoredFile file = w.file(owner(w), FileType.Private);
        w.registrationFile(w.registration(table, owner(w), status), file);
        return file;
    }

    /** @return a check that the response is exactly these categories, in any order */
    private static MatrixCell.BodyCheck categories(String... expected) {
        return (w, body) -> assertThat(JsonPath.<List<String>>read(body, "$.categories")).containsExactlyInAnyOrder(expected);
    }

    private static String nameBody(MatrixFixtures w, String prefix) {
        return "{\"name\": \"" + w.uniqueName(prefix) + "\"}";
    }

    /** The list every session reads: accepted values in, everybody else's proposals and rejections out. */
    private static MatrixRow publicList(String endpoint, CatalogType type) {
        return MatrixRow.of(endpoint, w -> {
                    w.remember("accepted", w.accepted(type).getId());
                    w.remember("created", w.catalog(type, CatalogStatus.Created, null).getId());
                    w.remember("rejected", w.catalog(type, CatalogStatus.Rejected, null).getId());
                    w.remember("disabled", w.catalog(type, CatalogStatus.Disabled, null).getId());
                    return MatrixRequest.path();
                })
                .cell(denied(401), ANONYMOUS)
                .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                .cell(allowed(200, (w, body) -> assertThat(body)
                                .contains(w.recall("accepted"))
                                .doesNotContain(w.recall("created"), w.recall("rejected"), w.recall("disabled"))),
                        PLAYER)
                .cell(allowed(200, (w, body) -> assertThat(body).contains(w.recall("accepted"))), MASTER, ADMIN, OWNER);
    }

    /** Proposing a value: whoever writes a table may, and the row alone is enough. */
    private static MatrixRow proposal(String endpoint, String prefix) {
        return MatrixRow.of(endpoint, w -> MatrixRequest.path().json(nameBody(w, prefix)))
                .cell(denied(401), ANONYMOUS)
                .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                .cell(denied(403), PLAYER, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER)
                .cell(allowed(201), MASTER, ADMIN, OWNER, PRIMARY, SECONDARY, PLAYER_MASTER, PLAYER_ADMIN, PLAYER_OWNER);
    }
}
