package com.centraldungeon.tables;

import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.common.security.CurrentUser;
import com.centraldungeon.tables.dto.AddMasterRequest;
import com.centraldungeon.tables.dto.CreateGameTableRequest;
import com.centraldungeon.tables.dto.GameTableDetailResponse;
import com.centraldungeon.tables.dto.GameTableSummaryResponse;
import com.centraldungeon.tables.dto.MasterSummaryResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Covers the game table aggregate: the table itself and its masters (arquitectura.md 2.2). */
@RestController
@RequestMapping("/api/v1/game-tables")
public class GameTableController {

    private final GameTableService gameTableService;

    public GameTableController(GameTableService gameTableService) {
        this.gameTableService = gameTableService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PageResponse<GameTableSummaryResponse> list(Pageable pageable) {
        return gameTableService.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public GameTableDetailResponse getDetail(@PathVariable String id) {
        return gameTableService.getDetail(id);
    }

    /** /my/tables - only the tables where the actor holds an active Player registration. */
    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<GameTableSummaryResponse> listMine(Pageable pageable, @AuthenticationPrincipal CurrentUser currentUser) {
        return gameTableService.listMine(currentUser.userId(), pageable);
    }

    /** /master/tables - every table where the actor is a master of any type, any status (pertenencia, not the platform role - #17). */
    @GetMapping("/managed")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<GameTableSummaryResponse> listManaged(Pageable pageable, @AuthenticationPrincipal CurrentUser currentUser) {
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

    /** Pertenencia, not role, decides who may act on THIS table (decisiones.md #17, #121) - checked inside the service. */
    @PostMapping("/{id}/open")
    @PreAuthorize("isAuthenticated()")
    public GameTableDetailResponse open(@PathVariable String id, @AuthenticationPrincipal CurrentUser currentUser) {
        return gameTableService.open(id, currentUser.userId());
    }

    @PostMapping("/{id}/masters")
    @PreAuthorize("isAuthenticated()")
    public List<MasterSummaryResponse> addOrPromoteMaster(
            @PathVariable String id, @Valid @RequestBody AddMasterRequest request, @AuthenticationPrincipal CurrentUser currentUser) {
        return gameTableService.addOrPromoteMaster(id, currentUser.userId(), request);
    }
}
