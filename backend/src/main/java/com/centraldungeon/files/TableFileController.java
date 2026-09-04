package com.centraldungeon.files;

import com.centraldungeon.common.security.CurrentUser;
import com.centraldungeon.files.dto.LinkTableFileRequest;
import com.centraldungeon.files.dto.TableFileResponse;
import com.centraldungeon.files.dto.UpdateTableFileRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * A table's files, as the people running it manage them - the Archivos tab of /master/tables/:id.
 *
 * <p><b>There is no upload here.</b> Uploading is {@code POST /api/v1/files} and attaching is this,
 * which is what makes "upload a new one" and "reuse one I already have" end in the same request. Reuse
 * is the cost lever of the whole fase (#65, #75), so it cannot be the path with the extra step.
 *
 * <p>What a candidate or a player sees is <b>not</b> here either: the shared files travel inside
 * {@code GameTableDetailResponse}, because that read already decides who may see the table, down to
 * answering 404 to somebody vetoed (#29). Repeating the check in a second endpoint would be a second
 * copy of a rule that can only drift. Same decision F1.3 took for the calendar.
 *
 * <p>Every method is {@code isAuthenticated()} and none names a role: running <em>this</em> table is
 * what authorizes touching its files, and that is a row in {@code masters} (#17, #121, #135). The
 * annotation is written out on each method and never inherited (regla dura 4, CVE-2025-41248).
 */
@RestController
@RequestMapping("/api/v1/game-tables/{tableId}/files")
public class TableFileController {

    /** The only collaborator: a controller never reaches a repository (regla dura 1). */
    private final TableFileService tableFileService;

    /**
     * @param tableFileService the service that owns the attachment and its rules
     */
    public TableFileController(TableFileService tableFileService) {
        this.tableFileService = tableFileService;
    }

    /**
     * Everything attached to the table, private notes included.
     *
     * <p>A list and not a page: it is bounded by what the table's masters chose to attach, and read as
     * one shelf - the same criterion the calendar and the status history use.
     *
     * @param tableId     the table
     * @param currentUser the actor, from the token; the service checks they run the table
     * @return 200 with its attachments, oldest first. 403 when the actor does not run it
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<TableFileResponse> list(
            @PathVariable String tableId, @AuthenticationPrincipal CurrentUser currentUser) {
        return tableFileService.listForTable(tableId, currentUser.userId());
    }

    /**
     * Attaching a file the actor already has, or one the platform published (#79).
     *
     * @param tableId     the table
     * @param request     the file to attach and how
     * @param currentUser the actor, from the token
     * @return 201 with the attachment. 403 when the actor does not run the table or the file is
     *         somebody else's and not published, 404 when the file is not there
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TableFileResponse> attach(
            @PathVariable String tableId,
            @Valid @RequestBody LinkTableFileRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {
        TableFileResponse attached = tableFileService.attach(tableId, request, currentUser.userId());
        return ResponseEntity.created(
                        URI.create("/api/v1/game-tables/" + tableId + "/files/" + attached.fileId()))
                .body(attached);
    }

    /**
     * Changing what an attachment is for, or whether the table's players see it.
     *
     * <p>Nothing here reaches the file: sharing a map on this table says nothing about the same map on
     * another one (#79).
     *
     * @param tableId     the table
     * @param fileId      the attached file
     * @param request     what it should be for, and whether it stays with the masters
     * @param currentUser the actor, from the token
     * @return 200 with the attachment after the change. 403 when the actor does not run the table,
     *         404 when the file is not attached to it
     */
    @PatchMapping("/{fileId}")
    @PreAuthorize("isAuthenticated()")
    public TableFileResponse update(
            @PathVariable String tableId,
            @PathVariable String fileId,
            @Valid @RequestBody UpdateTableFileRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return tableFileService.update(tableId, fileId, request, currentUser.userId());
    }

    /**
     * Taking a file off the table.
     *
     * <p><b>The file survives</b> (#79): it stays in its owner's history and on every other table that
     * has it. Removing the community's default sheet from one table cannot remove it from the
     * community.
     *
     * @param tableId     the table
     * @param fileId      the file to take off
     * @param currentUser the actor, from the token
     * @return 204. 403 when the actor does not run the table, 404 when the file is not attached to it
     */
    @DeleteMapping("/{fileId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> detach(
            @PathVariable String tableId,
            @PathVariable String fileId,
            @AuthenticationPrincipal CurrentUser currentUser) {
        tableFileService.detach(tableId, fileId, currentUser.userId());
        return ResponseEntity.noContent().build();
    }
}
