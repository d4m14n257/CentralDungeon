package com.centraldungeon.matrix;

import static com.centraldungeon.matrix.MatrixActor.*;
import static com.centraldungeon.matrix.MatrixCell.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.centraldungeon.catalogs.CatalogStatus;
import com.centraldungeon.catalogs.CatalogType;
import com.centraldungeon.catalogs.CatalogValue;
import com.centraldungeon.files.FileCategory;
import com.centraldungeon.files.FileType;
import com.centraldungeon.files.StoredFile;
import com.centraldungeon.registrations.TableRegistrationStatus;
import com.centraldungeon.tasks.TaskAudience;
import com.centraldungeon.users.User;
import com.jayway.jsonpath.JsonPath;
import java.util.List;

/**
 * The role matrix of the {@code files} and {@code catalogs} features: uploads, the personal and the
 * platform library, a table's files, and the systems, platforms and tags with their administration.
 * Thirty-four endpoints, several of them multipart.
 */
class FilesCatalogsRoleMatrixIT extends AbstractRoleMatrixIT {

    /** The catalog the administration rows work on. */
    private static final String CATALOG = "systems";

    /** One main row per endpoint, plus its variants. */
    static final List<MatrixRow> ROWS = List.of(
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
            MatrixRow.of("POST /api/v1/admin/files/{fileId}/unpublish",
                            w -> MatrixRequest.path(w.file(owner(w), FileType.Public, FileCategory.TableMaterial).getId()))
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER, RESOURCE_OWNER)
                    .cell(allowed(200), ADMIN, OWNER),
            MatrixRow.of("PUT /api/v1/admin/files/{fileId}/category",
                            w -> MatrixRequest.path(w.file(owner(w), FileType.Library, FileCategory.TableMaterial).getId())
                                    .json("{\"category\": \"MasterRequest\"}"))
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER, RESOURCE_OWNER)
                    .cell(allowed(200), ADMIN, OWNER),
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
            MatrixRow.variant("cajon PlayerApplication", "POST /api/v1/files", w -> MatrixRequest.path()
                            .filePart("file", w.uniqueBytes())
                            .jsonPart("data", "{\"fileType\": \"SingleUse\", \"fileCategory\": \"PlayerApplication\"}"))
                    .cell(allowed(201), PLAYER, PLAYER_MASTER),
            MatrixRow.variant("cajon TableMaterial", "POST /api/v1/files", w -> MatrixRequest.path()
                            .filePart("file", w.uniqueBytes())
                            .jsonPart("data", "{\"fileType\": \"SingleUse\", \"fileCategory\": \"TableMaterial\"}"))
                    .cell(allowed(201), MASTER)
                    .cell(denied(400), PLAYER),
            MatrixRow.variant("cajon Announcement", "POST /api/v1/files", w -> MatrixRequest.path()
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
            MatrixRow.of("GET /api/v1/files/{fileId}/content",
                            w -> MatrixRequest.path(w.file(owner(w), FileType.Private).getId()))
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(404), PLAYER, MASTER, ADMIN, OWNER)
                    .cell(allowed(200), RESOURCE_OWNER),
            MatrixRow.variant("public file", "GET /api/v1/files/{fileId}/content",
                            w -> MatrixRequest.path(w.file(owner(w), FileType.Public, FileCategory.TableMaterial).getId()))
                    .cell(allowed(200), PLAYER, MASTER, ADMIN, OWNER),
            MatrixRow.variant("shared by a table", "GET /api/v1/files/{fileId}/content", w -> {
                        StoredFile file = w.file(owner(w), FileType.Private);
                        w.link(w.table(), file, false);
                        return MatrixRequest.path(file.getId());
                    })
                    .cell(allowed(200), PLAYER, MASTER, ADMIN, OWNER, CANDIDATE, TABLE_PLAYER),
            MatrixRow.variant("private to a table", "GET /api/v1/files/{fileId}/content", w -> {
                        StoredFile file = w.file(owner(w), FileType.Private);
                        w.link(w.table(), file, true);
                        return MatrixRequest.path(file.getId());
                    })
                    .cell(allowed(200), PRIMARY, SECONDARY, ADMIN, OWNER)
                    .cell(denied(404), PLAYER, MASTER, CANDIDATE, TABLE_PLAYER, REMOVED_SECONDARY),
            MatrixRow.variant("handed in to a request", "GET /api/v1/files/{fileId}/content", w -> {
                        StoredFile file = w.file(w.person("submitter"), FileType.Private);
                        var task = w.task(w.table(), TaskAudience.Players, null);
                        w.submissionFile(w.submission(task, w.person("submitter")), file);
                        return MatrixRequest.path(file.getId());
                    })
                    .cell(allowed(200), PRIMARY, SECONDARY, ADMIN, OWNER)
                    .cell(denied(404), TABLE_PLAYER),
            MatrixRow.variant("form of a request", "GET /api/v1/files/{fileId}/content", w -> {
                        StoredFile file = w.file(owner(w), FileType.Private);
                        w.taskFile(w.task(w.table(), TaskAudience.Players, null), file);
                        return MatrixRequest.path(file.getId());
                    })
                    .cell(allowed(200), TABLE_PLAYER, PRIMARY, SECONDARY, ADMIN, OWNER)
                    .cell(denied(404), PLAYER, MASTER),
            MatrixRow.variant("attached to an application", "GET /api/v1/files/{fileId}/content", w -> {
                        StoredFile file = w.file(w.person("applicant"), FileType.Private);
                        w.registrationFile(
                                w.registration(w.table(), w.person("applicant"), TableRegistrationStatus.Candidate), file);
                        return MatrixRequest.path(file.getId());
                    })
                    .cell(allowed(200), PRIMARY, SECONDARY, ADMIN, OWNER)
                    .cell(denied(404), CANDIDATE),
            MatrixRow.of("PATCH /api/v1/files/{fileId}",
                            w -> MatrixRequest.path(w.file(owner(w), FileType.Private).getId())
                                    .json("{\"name\": \"renamed.pdf\", \"keepInLibrary\": true}"))
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER, ADMIN, OWNER, PRIMARY, TABLE_PLAYER)
                    .cell(allowed(200), RESOURCE_OWNER),
            MatrixRow.variant("public file of the owner", "PATCH /api/v1/files/{fileId}",
                            w -> MatrixRequest.path(w.file(owner(w), FileType.Public, FileCategory.TableMaterial).getId())
                                    .json("{\"name\": \"renamed.pdf\", \"keepInLibrary\": true}"))
                    .cell(denied(403), RESOURCE_OWNER),
            MatrixRow.of("DELETE /api/v1/files/{fileId}",
                            w -> MatrixRequest.path(w.file(owner(w), FileType.Private).getId()))
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER, ADMIN, OWNER, PRIMARY, TABLE_PLAYER)
                    .cell(allowed(204), RESOURCE_OWNER),

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
                    .cell(denied(403), PLAYER, MASTER, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER),
            MatrixRow.of("POST /api/v1/game-tables/{tableId}/files",
                            w -> MatrixRequest.path(w.table().getId())
                                    .json("{\"fileId\": \"" + w.file(w.person("publisher"), FileType.Public,
                                            FileCategory.TableMaterial).getId()
                                            + "\", \"tableFileType\": \"Preparation\", \"isPrivate\": false}"))
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER, ADMIN, OWNER, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER)
                    .cell(allowed(201), PRIMARY, SECONDARY),
            MatrixRow.of("PATCH /api/v1/game-tables/{tableId}/files/{fileId}", w -> MatrixRequest.path(
                                    w.table().getId(), w.link(w.table(), w.file(w.person("publisher"), FileType.Private), false)
                                            .getId().fileId())
                            .json("{\"tableFileType\": \"Preparation\", \"isPrivate\": true}"))
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER, ADMIN, OWNER, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER)
                    .cell(allowed(200), PRIMARY, SECONDARY),
            MatrixRow.of("DELETE /api/v1/game-tables/{tableId}/files/{fileId}", w -> MatrixRequest.path(
                            w.table().getId(), w.link(w.table(), w.file(w.person("publisher"), FileType.Private), false)
                                    .getId().fileId()))
                    .cell(denied(401), ANONYMOUS)
                    .cell(denied(401, "ACCOUNT_BLOCKED"), BLOCKED)
                    .cell(denied(403), PLAYER, MASTER, ADMIN, OWNER, REMOVED_SECONDARY, CANDIDATE, TABLE_PLAYER)
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

            // ------------------------------------------------------------ catalogs: reading and proposing
            publicList("GET /api/v1/platforms", CatalogType.PLATFORMS),
            proposal("POST /api/v1/platforms", "Platform"),
            publicList("GET /api/v1/systems", CatalogType.SYSTEMS),
            proposal("POST /api/v1/systems", "System"),
            publicList("GET /api/v1/tags", CatalogType.TAGS),
            proposal("POST /api/v1/tags", "Tag"));

    @Override
    List<MatrixRow> rows() {
        return ROWS;
    }

    /** @return the account the row hands its file to: the cell's own account only in the RESOURCE_OWNER column */
    private static User owner(MatrixFixtures w) {
        return w.user(RESOURCE_OWNER);
    }

    /** @return a check that the response is exactly these cajones, in any order */
    private static MatrixCell.BodyCheck categories(String... expected) {
        return (w, body) -> assertThat(JsonPath.<List<String>>read(body, "$")).containsExactlyInAnyOrder(expected);
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
