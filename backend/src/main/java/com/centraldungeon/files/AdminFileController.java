package com.centraldungeon.files;

import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.common.security.CurrentUser;
import com.centraldungeon.files.dto.AdminFileResponse;
import com.centraldungeon.files.dto.LibraryUploadRequest;
import com.centraldungeon.files.dto.UpdateLibraryCategoryRequest;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * /admin/files: the platform's library - what the community publishes for everybody, and what waits
 * to be published (#64, #278, #282).
 *
 * <p><b>Only the library's files, and nothing else.</b> Somebody's private upload - the sheet a
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
     * Every file of the library - published, unpublished or hidden - searchable, with the usage count that makes "linking is not copying"
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
     * Uploading a file into the platform's library, published now or left for later (#278, #282).
     *
     * <p><b>The cajón is always said; publishing is a choice.</b> The first keeps the library free of
     * files that do not say which flow they are for. The second exists because a published file is
     * attached by tables straight away, and hiding it afterwards does not take it off them (#79) - so
     * going out has to be asked for, not a side effect of uploading.
     *
     * <p>Two parts, like every upload: {@code file} with the content and {@code data} with the
     * cajones and the choice, the latter as {@code application/json}.
     *
     * @param file        the content and the name the browser sent
     * @param request     the cajones (#233), at least one, and whether to publish it now
     * @param currentUser the admin uploading, from the token - they stay its uploader
     * @return 201 with the file when the content was written; 200 when this admin had already
     *         uploaded it and that row came back into the library (#75, #234). 400 when a cajón is
     *         one nobody may publish into, or the part is empty, off the whitelist or over the cap
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public ResponseEntity<AdminFileResponse> upload(
            @RequestPart("file") MultipartFile file,
            @Valid @RequestPart("data") LibraryUploadRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {
        LibraryUpload result = fileService.uploadToLibrary(file, request, currentUser.userId());
        if (result.deduplicated()) {
            return ResponseEntity.ok(result.file());
        }
        return ResponseEntity.created(URI.create("/api/v1/admin/files/" + result.file().id())).body(result.file());
    }

    /**
     * Publishing a file of the library that is not published - never published yet, or hidden (#282).
     *
     * @param fileId the file to publish
     * @return 200 with the file after publishing. 400 when it has no cajón, 403 when it is already
     *         published, 404 when it is not there or not in the library
     */
    @PostMapping("/{fileId}/publish")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public AdminFileResponse publish(@PathVariable String fileId) {
        return fileService.publish(fileId);
    }

    /**
     * Hiding a published file: it stays in the library, offered to nobody (#282). Tables that already
     * attached it keep it (#79).
     *
     * @param fileId the file to hide
     * @return 200 with the file after hiding. 403 when it was not published, 404 when it is not there
     *         or not in the library
     */
    @PostMapping("/{fileId}/unpublish")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public AdminFileResponse unpublish(@PathVariable String fileId) {
        return fileService.unpublish(fileId);
    }

    /**
     * Changing what a file of the library is, while it is not published (#282).
     *
     * @param fileId  the file
     * @param request what it is now
     * @return 200 with the file after the change. 400 when the cajón is a player-side one, 403 when
     *         the file is published, 404 when it is not there or not in the library
     */
    @PutMapping("/{fileId}/category")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public AdminFileResponse changeCategory(
            @PathVariable String fileId, @Valid @RequestBody UpdateLibraryCategoryRequest request) {
        return fileService.changeLibraryCategory(fileId, request);
    }

    /**
     * Removing a file from the platform's library. Still a mark, never an erase (#25, #66).
     *
     * <p>Only a file of the library, published or not: somebody's private file is not the library's
     * to remove (#278, #282).
     *
     * @param fileId the file
     * @return 204. 404 when it is not there, was already marked gone, or is not in the library
     */
    @DeleteMapping("/{fileId}")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public ResponseEntity<Void> delete(@PathVariable String fileId) {
        fileService.deleteAsAdmin(fileId);
        return ResponseEntity.noContent().build();
    }
}
