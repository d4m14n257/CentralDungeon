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

    /** The only collaborator: a controller never reaches a repository (regla dura 1). */
    private final GameTableService gameTableService;

    /**
     * @param gameTableService the service that owns the table's lifecycle and its rules
     */
    public GameTableController(GameTableService gameTableService) {
        this.gameTableService = gameTableService;
    }

    /**
     * The public explorer. Newest first by default, which closes the gap decisiones.md #163 left
     * annotated: with no sort the rows came back in physical order - oldest first, since the ids are
     * time-ordered UUIDv7 - so a table published today could fall off the first page as the table
     * grew. The default lives here and not in the JPQL so an explicit ?sort= still wins.
     *
     * @param pageable    page, size and sort; newest first, with a tie-break by id (#171)
     * @param currentUser the actor, from the token. It goes into the WHERE so a master never sees
     *                    their own table in the list meant for applying (#121, #154)
     * @return 200 with one page of tables the actor could apply to
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PageResponse<GameTableSummaryResponse> list(
            @PageableDefault(sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return gameTableService.list(pageable, currentUser.userId());
    }

    /**
     * /admin/tables - unfiltered by pertenencia, defaults to the statuses waiting on an admin.
     *
     * @param status   which statuses to list, or null to fall back to the ones needing review (#176)
     * @param pageable page, size and sort
     * @return 200 with one page of tables for the admin listing
     */
    @GetMapping("/admin")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public PageResponse<AdminTableSummaryResponse> listForAdmin(
            @RequestParam(required = false) @Nullable List<GameTableStatus> status,
            @PageableDefault(sort = {"createdAt", "id"}) Pageable pageable) {
        return gameTableService.listForAdmin(status, pageable);
    }

    /**
     * The public detail of a table, /tables/:id.
     *
     * @param id the table to read
     * @return 200 with the table. 404 when it does not exist or the actor is vetoed from it - a 403
     *         there would confirm what the 404 denies
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public GameTableDetailResponse getDetail(@PathVariable String id) {
        return gameTableService.getDetail(id);
    }

    /**
     * /master/tables/:id - pertenencia checked in the service before any data is read (#152).
     *
     * @param id          the table to read
     * @param currentUser the actor, from the token
     * @return 200 with the table as its master sees it. 403 when the actor does not run it
     */
    @GetMapping("/{id}/managed")
    @PreAuthorize("isAuthenticated()")
    public GameTableDetailResponse getManagedDetail(@PathVariable String id, @AuthenticationPrincipal CurrentUser currentUser) {
        return gameTableService.getManagedDetail(id, currentUser.userId());
    }

    /**
     * The table's lifecycle history, oldest first - who moved it, where to, and why.
     *
     * @param id          the table
     * @param currentUser the actor, from the token; the service checks they may see it
     * @return 200 with the whole history. Not paginated: it is bounded by the nine states and read
     *         as a single timeline
     */
    @GetMapping("/{id}/status-history")
    @PreAuthorize("isAuthenticated()")
    public List<TableStatusChangeResponse> getStatusHistory(@PathVariable String id, @AuthenticationPrincipal CurrentUser currentUser) {
        return gameTableService.getStatusHistory(id, currentUser.userId());
    }

    /**
     * /my/tables - only the tables where the actor holds an active Player registration.
     *
     * @param pageable    page, size and sort; newest first
     * @param currentUser the actor, from the token (#121)
     * @return 200 with one page of the tables they play at
     */
    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<GameTableSummaryResponse> listMine(
            @PageableDefault(sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return gameTableService.listMine(currentUser.userId(), pageable);
    }

    /**
     * /master/tables - every table where the actor is a master of any type, any status. Pertenencia,
     * not the platform role (#17, #135): somebody made a co-master without holding {@code Master}
     * still sees their table here.
     *
     * @param pageable    page, size and sort; newest first
     * @param currentUser the actor, from the token (#121)
     * @return 200 with one page of the tables they run
     */
    @GetMapping("/managed")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<GameTableSummaryResponse> listManaged(
            @PageableDefault(sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return gameTableService.listManaged(currentUser.userId(), pageable);
    }

    /**
     * Creating a table needs the platform role Master - "I can create tables", nothing about a
     * specific one (#135). The creator becomes the table's Primary (#73).
     *
     * @param request     the draft
     * @param currentUser the actor, from the token; they become the Primary
     * @return 201 with the created table in Preparation, and its Location header
     */
    @PostMapping
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<GameTableDetailResponse> create(
            @Valid @RequestBody CreateGameTableRequest request, @AuthenticationPrincipal CurrentUser currentUser) {
        GameTableDetailResponse created = gameTableService.create(request, currentUser.userId());
        return ResponseEntity.created(URI.create("/api/v1/game-tables/" + created.id())).body(created);
    }

    /**
     * A table an admin creates without running it (#72). It is born Unassigned, with no Primary, and
     * only {@link #assignMasters} moves it on.
     *
     * @param request     the draft
     * @param currentUser the admin, from the token. Recorded as the author, not as a master
     * @return 201 with the created table in Unassigned, and its Location header
     */
    @PostMapping("/unassigned")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public ResponseEntity<GameTableDetailResponse> createUnassigned(
            @Valid @RequestBody CreateGameTableRequest request, @AuthenticationPrincipal CurrentUser currentUser) {
        GameTableDetailResponse created = gameTableService.createUnassigned(request, currentUser.userId());
        return ResponseEntity.created(URI.create("/api/v1/game-tables/" + created.id())).body(created);
    }

    /**
     * Hands an Unassigned table its first masters, which opens it directly - review is skipped,
     * because an admin already vouched for it by creating it (#72).
     *
     * @param id          the table
     * @param request     who runs it and who co-runs it
     * @param currentUser the admin, from the token; recorded in the status history
     * @return 200 with the table, now Opened. 409 if it was not Unassigned
     */
    @PostMapping("/{id}/assign-masters")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public GameTableDetailResponse assignMasters(
            @PathVariable String id, @Valid @RequestBody AssignMastersRequest request, @AuthenticationPrincipal CurrentUser currentUser) {
        return gameTableService.assignInitialMasters(id, request, currentUser.userId());
    }

    /**
     * An admin approving a master's draft (#27) - the only way Preparation reaches Opened.
     *
     * @param id          the table to approve
     * @param currentUser the admin, from the token; recorded in the status history
     * @return 200 with the table, now Opened. 409 if it was not awaiting review
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public GameTableDetailResponse approve(@PathVariable String id, @AuthenticationPrincipal CurrentUser currentUser) {
        return gameTableService.approve(id, currentUser.userId());
    }

    /**
     * Sends a draft back to its master with a reason. The reason is required, and it is what the
     * master reads on the status tab.
     *
     * @param id          the table
     * @param request     the justification
     * @param currentUser the admin, from the token
     * @return 200 with the table, now ChangesRequested
     */
    @PostMapping("/{id}/request-changes")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public GameTableDetailResponse requestChanges(
            @PathVariable String id, @Valid @RequestBody ChangeTableStatusRequest request, @AuthenticationPrincipal CurrentUser currentUser) {
        return gameTableService.requestChanges(id, currentUser.userId(), request);
    }

    /**
     * The master sending a corrected draft back for review.
     *
     * <p>{@code isAuthenticated()} and no role: pertenencia, not role, decides who may act on THIS
     * table (#17, #121), and the service checks it before touching anything.
     *
     * @param id          the table
     * @param currentUser the actor, from the token
     * @return 200 with the table, back in Preparation. 403 when the actor does not run it
     */
    @PostMapping("/{id}/resubmit")
    @PreAuthorize("isAuthenticated()")
    public GameTableDetailResponse resubmit(@PathVariable String id, @AuthenticationPrincipal CurrentUser currentUser) {
        return gameTableService.resubmit(id, currentUser.userId());
    }

    /**
     * The master declaring play has begun: Opened to InProgress.
     *
     * @param id          the table
     * @param currentUser the actor, from the token; the service checks pertenencia (#17, #121)
     * @return 200 with the table, now InProgress
     */
    @PostMapping("/{id}/start")
    @PreAuthorize("isAuthenticated()")
    public GameTableDetailResponse start(@PathVariable String id, @AuthenticationPrincipal CurrentUser currentUser) {
        return gameTableService.start(id, currentUser.userId());
    }

    /**
     * The master closing a table that ran its course.
     *
     * @param id          the table
     * @param currentUser the actor, from the token; the service checks pertenencia (#17, #121)
     * @return 200 with the table, now Finished
     */
    @PostMapping("/{id}/finish")
    @PreAuthorize("isAuthenticated()")
    public GameTableDetailResponse finish(@PathVariable String id, @AuthenticationPrincipal CurrentUser currentUser) {
        return gameTableService.finish(id, currentUser.userId());
    }

    /**
     * Ending a table early, with a reason on the record. The one transition with more than one valid
     * origin.
     *
     * <p>Either the Primary or an admin may cancel, and which of the two the actor is gets resolved
     * inside the service rather than here (#17, #121) - a {@code @PreAuthorize} cannot see
     * pertenencia.
     *
     * @param id          the table
     * @param request     the justification, required
     * @param currentUser the actor, from the token
     * @return 200 with the table, now Canceled
     */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public GameTableDetailResponse cancel(
            @PathVariable String id, @Valid @RequestBody ChangeTableStatusRequest request, @AuthenticationPrincipal CurrentUser currentUser) {
        return gameTableService.cancel(id, currentUser.userId(), request);
    }

    /**
     * An admin pausing a table directly. A master <em>asking</em> for a pause is PauseRequested, and
     * that needs {@code approval_requests} - F3.
     *
     * @param id          the table
     * @param request     the justification, required
     * @param currentUser the admin, from the token
     * @return 200 with the table, now Pause. Its agenda freezes there (#32, #33)
     */
    @PostMapping("/{id}/pause")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public GameTableDetailResponse pause(
            @PathVariable String id, @Valid @RequestBody ChangeTableStatusRequest request, @AuthenticationPrincipal CurrentUser currentUser) {
        return gameTableService.pauseDirect(id, currentUser.userId(), request);
    }

    /**
     * Bringing a paused table back. Rescheduling from the resume date - and re-checking the schedule
     * clash it may now have - is F1.3 (#33, #178).
     *
     * @param id          the table
     * @param currentUser the admin, from the token
     * @return 200 with the table, back in play
     */
    @PostMapping("/{id}/resume")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public GameTableDetailResponse resume(@PathVariable String id, @AuthenticationPrincipal CurrentUser currentUser) {
        return gameTableService.resume(id, currentUser.userId());
    }

    /**
     * Only a table that was never public (decisiones.md #175). Pertenencia and state are checked in
     * the service; 204 because there is nothing left to return.
     *
     * @param id          the table to delete
     * @param currentUser the actor, from the token
     * @return nothing - 204. 409 once anybody has seen or applied to the table, which is cancelled
     *         instead of deleted
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id, @AuthenticationPrincipal CurrentUser currentUser) {
        gameTableService.delete(id, currentUser.userId());
    }

    /**
     * Adds a co-master, or promotes one to Primary. Keeping exactly one live Primary is the
     * service's job (#73).
     *
     * @param id          the table
     * @param request     who to add or promote, and as what
     * @param currentUser the actor, from the token; the service checks pertenencia (#17, #121)
     * @return 200 with the table's masters after the change
     */
    @PostMapping("/{id}/masters")
    @PreAuthorize("isAuthenticated()")
    public List<MasterSummaryResponse> addOrPromoteMaster(
            @PathVariable String id, @Valid @RequestBody AddMasterRequest request, @AuthenticationPrincipal CurrentUser currentUser) {
        return gameTableService.addOrPromoteMaster(id, currentUser.userId(), request);
    }
}
