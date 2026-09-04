package com.centraldungeon.files;

import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.files.dto.AdminFileResponse;
import com.centraldungeon.files.dto.PublishFileRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * /admin/files: every file the platform holds, and the power to publish one for everybody (#64).
 *
 * <p>Publishing is what makes #79 work. Once the community's default character sheet exists here,
 * every master attaches <em>that</em> file instead of uploading their own copy - so correcting it
 * corrects it on every table at once, and the same bytes are stored once rather than once per master.
 *
 * <p>Admin and Owner are enumerated explicitly on every method. There is no {@code RoleHierarchy} in
 * this project: Owner can do everything Admin can by being listed, not by inheriting (#169). And the
 * authorization is declared here, on the concrete method, never in a route list far from the endpoint
 * it protects (#123) and never on a superclass (regla dura 4, CVE-2025-41248).
 *
 * <p>⚠️ Not to be confused with the physical deletion of #66, which is the <b>platform owner's</b> and
 * is F5. Everything here still only marks (#25).
 */
@RestController
@RequestMapping("/api/v1/admin/files")
public class AdminFileController {

    /** The only collaborator: a controller never reaches a repository (regla dura 1). */
    private final FileService fileService;

    /**
     * @param fileService the service that owns the file and its rules
     */
    public AdminFileController(FileService fileService) {
        this.fileService = fileService;
    }

    /**
     * Every file, searchable, with the usage count that makes "linking is not copying" visible (#79).
     *
     * @param query     the search box in the language of #164 - by name, by uploader or by type - or
     *                  null for everything
     * @param statuses  the statuses to keep, or null for all of them, marked-gone files included
     * @param fileTypes the lifecycles to keep (#68), or null for all of them
     * @param pageable  page, size and sort; newest first, with a tie-break by id (#171)
     * @return 200 with one page of files
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public PageResponse<AdminFileResponse> list(
            @RequestParam(name = "q", required = false) @Nullable String query,
            @RequestParam(name = "status", required = false) @Nullable List<FileStatus> statuses,
            @RequestParam(name = "fileType", required = false) @Nullable List<FileType> fileTypes,
            @PageableDefault(size = 20, sort = {"createdAt", "id"}) Pageable pageable) {
        return fileService.listForAdmin(
                query, statuses == null ? List.of() : statuses, fileTypes == null ? List.of() : fileTypes, pageable);
    }

    /**
     * Publishing a file for the whole platform, with the audience #64 requires.
     *
     * <p>The audience is not optional, and that is the fix for M24.1: the legacy returned every public
     * file everywhere, so a document written for masters turned up in front of a player.
     *
     * @param fileId  the file to publish
     * @param request who it is for
     * @return 200 with the file after publishing. 404 when it is not there or was marked gone
     */
    @PostMapping("/{fileId}/publish")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public AdminFileResponse publish(@PathVariable String fileId, @Valid @RequestBody PublishFileRequest request) {
        return fileService.publish(fileId, request);
    }

    /**
     * Taking a file back out of the published set. Tables that already attached it keep it (#79).
     *
     * @param fileId the file to unpublish
     * @return 200 with the file after unpublishing. 403 when it was not published, 404 when it is
     *         not there
     */
    @PostMapping("/{fileId}/unpublish")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public AdminFileResponse unpublish(@PathVariable String fileId) {
        return fileService.unpublish(fileId);
    }

    /**
     * Removing any file, including one somebody else uploaded. Still a mark, never an erase (#25, #66).
     *
     * @param fileId the file
     * @return 204. 404 when it is not there or was already marked gone
     */
    @DeleteMapping("/{fileId}")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public ResponseEntity<Void> delete(@PathVariable String fileId) {
        fileService.deleteAsAdmin(fileId);
        return ResponseEntity.noContent().build();
    }
}
