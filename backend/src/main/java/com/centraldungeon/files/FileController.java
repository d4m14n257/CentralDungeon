package com.centraldungeon.files;

import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.common.security.CurrentUser;
import com.centraldungeon.files.dto.FileResponse;
import com.centraldungeon.files.dto.PublicFileResponse;
import com.centraldungeon.files.dto.UpdateFileRequest;
import com.centraldungeon.files.dto.UploadFileRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * A person's own files: uploading them, finding them again, and downloading one.
 *
 * <p>Every method is {@code isAuthenticated()} and none names a role. Whether somebody may reach a
 * concrete file is <b>pertenencia</b> - they own it, or they belong to a table it is attached to -
 * which a {@code @PreAuthorize} cannot see, so the service settles it before reading anything (#17,
 * #121, #135). The annotation is still written out on each method and never inherited (regla dura 4,
 * CVE-2025-41248).
 *
 * <p>No endpoint takes a user id. The actor comes from the token, always, which is the hole the Node
 * backend had: its upload path was {@code UPLOAD_FILES/<user_id>/...} with the id read straight out
 * of the URL (M21.5).
 */
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    /** The only collaborator: a controller never reaches a repository (regla dura 1). */
    private final FileService fileService;

    /**
     * @param fileService the service that owns the file and its rules
     */
    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    /**
     * Uploading a file.
     *
     * <p>Two parts, not a JSON body with the bytes inside it: {@code file} carries the content and
     * its name, {@code data} the metadata. The {@code data} part has to arrive with
     * {@code Content-Type: application/json} for Spring to bind it to a record - the frontend sends
     * it as a {@code Blob} for exactly that reason.
     *
     * @param file        the content and the name the browser sent
     * @param request     which lifecycle the uploader wants for it (#68)
     * @param currentUser the uploader, from the token
     * @return 201 with the file and its {@code Location}. 400 when the part is empty, its type is not
     *         on the whitelist, it is over the cap, or the request asks for {@code Public} - which is
     *         an admin's to grant (#64). <b>An upload of content this person already has answers 201
     *         with the file they already had</b>, which is the deduplication of #75 and not an error
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FileResponse> upload(
            @RequestPart("file") MultipartFile file,
            @Valid @RequestPart("data") UploadFileRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {
        FileResponse created = fileService.upload(file, request, currentUser.userId());
        return ResponseEntity.created(URI.create("/api/v1/files/" + created.id())).body(created);
    }

    /**
     * The reuse history of #65 - everything this person uploaded and still keeps.
     *
     * <p>This is what makes reuse cheaper than re-uploading, which is the whole cost strategy of #75.
     * If finding an old file were harder than dragging in a new one, everybody would drag in a new one.
     *
     * @param currentUser whose files, from the token. There is no parameter that could name anybody
     *                    else (#121)
     * @param pageable    page, size and sort; <b>most recently used first</b>, with a tie-break by id
     *                    (#171). The direction is spelled out because the default is ascending, and
     *                    ascending here would open the picker on whatever this person has not touched
     *                    in the longest time - the exact opposite of what reuse needs (#65)
     * @return 200 with one page of their files
     */
    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<FileResponse> listMine(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PageableDefault(size = 20, sort = {"lastUsedAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return fileService.listMine(currentUser.userId(), pageable);
    }

    /**
     * What the platform published, for whoever is choosing one to attach (#64, #79).
     *
     * @param audience who to narrow to, or null for everything published
     * @param pageable page, size and sort; by name, with a tie-break by id (#171)
     * @return 200 with one page of published files
     */
    @GetMapping("/public")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<PublicFileResponse> listPublic(
            @RequestParam(name = "audience", required = false) @Nullable PublicAudience audience,
            @PageableDefault(size = 20, sort = {"name", "id"}) Pageable pageable) {
        return fileService.listPublic(audience, pageable);
    }

    /**
     * One file's metadata.
     *
     * @param fileId      the file
     * @param currentUser the reader, from the token
     * @return 200 with the file. 404 when it does not exist, was marked gone, or the reader has no
     *         way to reach it - all three answer the same, so an id nobody should have learns nothing
     */
    @GetMapping("/{fileId}")
    @PreAuthorize("isAuthenticated()")
    public FileResponse findById(@PathVariable String fileId, @AuthenticationPrincipal CurrentUser currentUser) {
        return fileService.findById(fileId, currentUser.userId());
    }

    /**
     * Downloading the content.
     *
     * <p><b>{@code ResponseEntity<Resource>} is not the open type regla dura 3 forbids.</b> That rule
     * is about {@code Map<String, Object>}, {@code Object} and {@code ResponseEntity<?>} - types that
     * leave the contract unwritten. A stream of bytes has no {@code record} to be, and {@code Resource}
     * is closed and named. What is typed here is the metadata, in {@link FileResponse}.
     *
     * <p>The filename goes into {@code Content-Disposition} through Spring's builder, which encodes
     * it. That is the only place the name the user typed is used at all, and it is a header - never a
     * path (#80).
     *
     * @param fileId      the file
     * @param currentUser the reader, from the token
     * @return 200 with the bytes, as an attachment. 404 when the file is not there or out of reach
     */
    @GetMapping("/{fileId}/content")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> download(
            @PathVariable String fileId, @AuthenticationPrincipal CurrentUser currentUser) {
        FileDownload download = fileService.download(fileId, currentUser.userId());
        ContentDisposition disposition =
                ContentDisposition.attachment().filename(download.name(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(download.mimeType()))
                .contentLength(download.content().length)
                .body(new ByteArrayResource(download.content()));
    }

    /**
     * Renaming a file, and deciding whether to keep it in the reuse history (#65, #68).
     *
     * @param fileId      the file
     * @param request     the name it should end up with, and whether to keep it
     * @param currentUser the actor, from the token
     * @return 200 with the file after the change. 403 when it is somebody else's or the platform
     *         published it, 404 when it is not there
     */
    @PatchMapping("/{fileId}")
    @PreAuthorize("isAuthenticated()")
    public FileResponse update(
            @PathVariable String fileId,
            @Valid @RequestBody UpdateFileRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return fileService.update(fileId, request, currentUser.userId());
    }

    /**
     * Letting go of a file. Marks it gone; the bytes wait for the platform owner (#25, #66).
     *
     * @param fileId      the file
     * @param currentUser the owner, from the token
     * @return 204. 403 when it is somebody else's or the platform published it, 404 when it is
     *         not there
     */
    @DeleteMapping("/{fileId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> delete(
            @PathVariable String fileId, @AuthenticationPrincipal CurrentUser currentUser) {
        fileService.delete(fileId, currentUser.userId());
        return ResponseEntity.noContent().build();
    }
}
