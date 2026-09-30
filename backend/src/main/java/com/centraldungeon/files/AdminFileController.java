package com.centraldungeon.files;

import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.common.security.CurrentUser;
import com.centraldungeon.files.dto.AdminFileResponse;
import com.centraldungeon.files.dto.PublishFileRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * /admin/files: the platform's library - what the community publishes for everybody (#64, #278).
 *
 * <p><b>Only the published files, and nothing else.</b> Somebody's private upload - the sheet a
 * player applied with, what they handed in - is theirs; an admin reaches it by acting as that person,
 * never from here (#278). That is also why the library speaks of three cajones and not five: the two
 * player-side ones hold people's answers, and nothing is ever published into them (#233).
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
     * Every published file, searchable, with the usage count that makes "linking is not copying"
     * visible (#79).
     *
     * @param query     the search box in the language of #164 - by name, by uploader or by type - or
     *                  null for everything
     * @param statuses   the statuses to keep, or null for all of them, marked-gone files included
     * @param category   the cajón to keep (#233), or null for all of them. A filter and not a search
     *                   term, because three known values are chosen from and not typed at
     * @param pageable   page, size and sort; <b>newest first</b>, with a tie-break by id (#171). The
     *                   direction is spelled out because the default is ascending: what an admin opens
     *                   this screen for is what just arrived, not the oldest thing on the platform
     * @return 200 with one page of files. 400 when the cajón is one of the two nothing is ever
     *         published into
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public PageResponse<AdminFileResponse> list(
            @RequestParam(name = "q", required = false) @Nullable String query,
            @RequestParam(name = "status", required = false) @Nullable List<FileStatus> statuses,
            @RequestParam(name = "category", required = false) @Nullable FileCategory category,
            @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return fileService.listForAdmin(
                query,
                statuses == null ? List.of() : statuses,
                category,
                pageable);
    }

    /**
     * Uploading a file straight into the platform's library, published into the cajones it is
     * offered in (#233, #278).
     *
     * <p><b>Uploading is publishing here</b>: the cajones are chosen before the file is sent, so no
     * file ever sits in the library without saying which flow it is for. They used to be two steps,
     * and in between the file was the admin's own {@code Private} with no cajón at all.
     *
     * <p>The cajones are not optional, and that is M24.1's fix carried across from the audience they
     * replaced: the legacy returned every public file everywhere, so a document written for masters
     * turned up in front of a player. They are plural because the same blank serves more than one
     * flow (#233).
     *
     * <p>Two parts, like every upload: {@code file} with the content and {@code data} with the
     * cajones, the latter as {@code application/json}.
     *
     * @param file        the content and the name the browser sent
     * @param request     the cajones it is offered in (#233), at least one
     * @param currentUser the admin uploading, from the token - they stay its uploader
     * @return 201 with the published file when the content was written; 200 when this admin had
     *         already uploaded it and that row came back published (#75, #234). 400 when a cajón is
     *         one nobody may publish into, or the part is empty, off the whitelist or over the cap
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public ResponseEntity<AdminFileResponse> upload(
            @RequestPart("file") MultipartFile file,
            @Valid @RequestPart("data") PublishFileRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {
        PublishedUpload result = fileService.uploadPublished(file, request, currentUser.userId());
        if (result.deduplicated()) {
            return ResponseEntity.ok(result.file());
        }
        return ResponseEntity.created(URI.create("/api/v1/admin/files/" + result.file().id())).body(result.file());
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
     * Removing a file from the platform's library. Still a mark, never an erase (#25, #66).
     *
     * <p>Only a published one: somebody's private file is not the library's to remove (#278).
     *
     * @param fileId the file
     * @return 204. 404 when it is not there, was already marked gone, or is not published
     */
    @DeleteMapping("/{fileId}")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public ResponseEntity<Void> delete(@PathVariable String fileId) {
        fileService.deleteAsAdmin(fileId);
        return ResponseEntity.noContent().build();
    }
}
