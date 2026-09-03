package com.centraldungeon.tables;

import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.common.security.CurrentUser;
import com.centraldungeon.tables.dto.AddMasterRequest;
import com.centraldungeon.tables.dto.AdminTableSummaryResponse;
import com.centraldungeon.tables.dto.AssignMastersRequest;
import com.centraldungeon.tables.dto.ChangeTableStatusRequest;
import com.centraldungeon.tables.dto.CreateGameTableRequest;
import com.centraldungeon.tables.dto.GameTableDetailResponse;
import com.centraldungeon.tables.dto.GameTableSummaryResponse;
import com.centraldungeon.tables.dto.MasterSummaryResponse;
import com.centraldungeon.tables.dto.TableStatusChangeResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Covers the game table aggregate: the table itself, its masters and its status history (arquitectura.md 2.2). */
@RestController
@RequestMapping("/api/v1/game-tables")
public class GameTableController {

    private final GameTableService gameTableService;

    public GameTableController(GameTableService gameTableService) {
        this.gameTableService = gameTableService;
    }

    /**
     * The public explorer. Newest first by default, which closes the gap decisiones.md #163 left
     * annotated: with no sort the rows came back in physical order - oldest first, since the ids are
     * time-ordered UUIDv7 - so a table published today could fall off the first page as the table
     * grew. The default lives here and not in the JPQL so an explicit ?sort= still wins.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PageResponse<GameTableSummaryResponse> list(
            @PageableDefault(sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return gameTableService.list(pageable, currentUser.userId());
    }

    /** /admin/tables - unfiltered by pertenencia, defaults to the statuses waiting on an admin. */
    @GetMapping("/admin")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public PageResponse<AdminTableSummaryResponse> listForAdmin(
            @RequestParam(required = false) @Nullable List<GameTableStatus> status,
            @PageableDefault(sort = {"createdAt", "id"}) Pageable pageable) {
        return gameTableService.listForAdmin(status, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public GameTableDetailResponse getDetail(@PathVariable String id) {
        return gameTableService.getDetail(id);
    }

    /** /master/tables/:id - pertenencia checked in the service before any data is read (decisiones.md #152). */
    @GetMapping("/{id}/managed")
    @PreAuthorize("isAuthenticated()")
    public GameTableDetailResponse getManagedDetail(@PathVariable String id, @AuthenticationPrincipal CurrentUser currentUser) {
        return gameTableService.getManagedDetail(id, currentUser.userId());
    }

    @GetMapping("/{id}/status-history")
    @PreAuthorize("isAuthenticated()")
    public List<TableStatusChangeResponse> getStatusHistory(@PathVariable String id, @AuthenticationPrincipal CurrentUser currentUser) {
        return gameTableService.getStatusHistory(id, currentUser.userId());
    }

    /** /my/tables - only the tables where the actor holds an active Player registration. */
    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<GameTableSummaryResponse> listMine(
            @PageableDefault(sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return gameTableService.listMine(currentUser.userId(), pageable);
    }

    /** /master/tables - every table where the actor is a master of any type, any status (pertenencia, not the platform role - #17). */
    @GetMapping("/managed")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<GameTableSummaryResponse> listManaged(
            @PageableDefault(sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return gameTableService.listManaged(currentUser.userId(), pageable);
    }

    /** Creating a table needs the platform role Master - "I can create tables", nothing about a specific one (decisiones.md #135). */
    @PostMapping
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<GameTableDetailResponse> create(
            @Valid @RequestBody CreateGameTableRequest request, @AuthenticationPrincipal CurrentUser currentUser) {
        GameTableDetailResponse created = gameTableService.create(request, currentUser.userId());
        return ResponseEntity.created(URI.create("/api/v1/game-tables/" + created.id())).body(created);
    }

    /** A mesa an admin creates without being its master (#72) - nace Unassigned, sin Primary. */
    @PostMapping("/unassigned")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public ResponseEntity<GameTableDetailResponse> createUnassigned(
            @Valid @RequestBody CreateGameTableRequest request, @AuthenticationPrincipal CurrentUser currentUser) {
        GameTableDetailResponse created = gameTableService.createUnassigned(request, currentUser.userId());
        return ResponseEntity.created(URI.create("/api/v1/game-tables/" + created.id())).body(created);
    }

    @PostMapping("/{id}/assign-masters")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public GameTableDetailResponse assignMasters(
            @PathVariable String id, @Valid @RequestBody AssignMastersRequest request, @AuthenticationPrincipal CurrentUser currentUser) {
        return gameTableService.assignInitialMasters(id, request, currentUser.userId());
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public GameTableDetailResponse approve(@PathVariable String id, @AuthenticationPrincipal CurrentUser currentUser) {
        return gameTableService.approve(id, currentUser.userId());
    }

    @PostMapping("/{id}/request-changes")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public GameTableDetailResponse requestChanges(
            @PathVariable String id, @Valid @RequestBody ChangeTableStatusRequest request, @AuthenticationPrincipal CurrentUser currentUser) {
        return gameTableService.requestChanges(id, currentUser.userId(), request);
    }

    /** Pertenencia, not role, decides who may act on THIS table (decisiones.md #17, #121) - checked inside the service. */
    @PostMapping("/{id}/resubmit")
    @PreAuthorize("isAuthenticated()")
    public GameTableDetailResponse resubmit(@PathVariable String id, @AuthenticationPrincipal CurrentUser currentUser) {
        return gameTableService.resubmit(id, currentUser.userId());
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("isAuthenticated()")
    public GameTableDetailResponse start(@PathVariable String id, @AuthenticationPrincipal CurrentUser currentUser) {
        return gameTableService.start(id, currentUser.userId());
    }

    @PostMapping("/{id}/finish")
    @PreAuthorize("isAuthenticated()")
    public GameTableDetailResponse finish(@PathVariable String id, @AuthenticationPrincipal CurrentUser currentUser) {
        return gameTableService.finish(id, currentUser.userId());
    }

    /** Either the Primary or an admin may cancel - resolved inside the service, not here (decisiones.md #17, #121). */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public GameTableDetailResponse cancel(
            @PathVariable String id, @Valid @RequestBody ChangeTableStatusRequest request, @AuthenticationPrincipal CurrentUser currentUser) {
        return gameTableService.cancel(id, currentUser.userId(), request);
    }

    @PostMapping("/{id}/pause")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public GameTableDetailResponse pause(
            @PathVariable String id, @Valid @RequestBody ChangeTableStatusRequest request, @AuthenticationPrincipal CurrentUser currentUser) {
        return gameTableService.pauseDirect(id, currentUser.userId(), request);
    }

    @PostMapping("/{id}/resume")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public GameTableDetailResponse resume(@PathVariable String id, @AuthenticationPrincipal CurrentUser currentUser) {
        return gameTableService.resume(id, currentUser.userId());
    }

    /**
     * Only a table that was never public (decisiones.md #175). Pertenencia and state are checked in
     * the service; 204 because there is nothing left to return.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id, @AuthenticationPrincipal CurrentUser currentUser) {
        gameTableService.delete(id, currentUser.userId());
    }

    @PostMapping("/{id}/masters")
    @PreAuthorize("isAuthenticated()")
    public List<MasterSummaryResponse> addOrPromoteMaster(
            @PathVariable String id, @Valid @RequestBody AddMasterRequest request, @AuthenticationPrincipal CurrentUser currentUser) {
        return gameTableService.addOrPromoteMaster(id, currentUser.userId(), request);
    }
}
