package com.centraldungeon.features.queue;

import com.centraldungeon.features.queue.dto.QueueItemResponse;
import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.common.security.CurrentUser;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /admin/queue}: the shared tray, and the two routes that reserve an item of it.
 *
 * <p><b>Admin and Owner are enumerated on every method.</b> There is no {@code RoleHierarchy} in this
 * project: an owner can do everything an admin can by being listed, never by inheriting, and the
 * annotation goes on the concrete method rather than on the class, an interface or a list of paths
 * in {@code SecurityConfig}. The role matrix does not separate
 * the two ranks for the tray - both work it - so this guard is the whole authorization story here,
 * which is why writing {@code hasRole('ADMIN')} would lock the owner out and nobody would notice in
 * development.
 *
 * <p><b>The claim routes live here and not next to each resource</b>, which is what
 * {@code AdminApprovalRequestController} reserved them for by name. Reserving is an act of the tray,
 * not of the request or of the table: the same two verbs answer for every source, and splitting them
 * per aggregate would mean adding two more copies of the same pair for every new source.
 *
 * <p>Note what is <b>not</b> here: approving, rejecting and asking for changes stay on their own
 * aggregates ({@code /admin/requests/{id}/approve}, {@code /game-tables/{id}/approve}). The screen
 * moved; the endpoints did not, because they are still operations on those aggregates - what
 * changed is that they now require the reservation this controller hands out.
 */
@RestController
@RequestMapping("/api/v1/admin/queue")
public class QueueController {

    /** The only collaborator: a controller never reaches a repository. */
    private final QueueService queueService;

    /**
     * @param queueService the service that owns the merge and the reservation rules
     */
    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    /**
     * The tray, oldest first.
     *
     * <p><b>No {@code ?q=}</b>, on purpose: a tray sorts itself by age and empties, and a search box
     * over it would be solving the wrong problem. {@code /admin/tables} is the screen that searches.
     * Its sort is fixed for the same reason - "who has been waiting longest" is the tray's
     * whole promise.
     *
     * @param pageable    which page and how big. The sort is ignored: the order is the rule
     * @param currentUser the actor, from the token. An item another admin reserved is not in
     *                    this answer at all, and one this admin reserved still is
     * @return 200 with one page of the tray. An empty one is good news and the screen says so
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public PageResponse<QueueItemResponse> list(
            @PageableDefault(size = 20) Pageable pageable, @AuthenticationPrincipal CurrentUser currentUser) {
        return queueService.list(currentUser.userId(), pageable);
    }

    /**
     * Takes an item, so nobody else starts on it.
     *
     * <p>Idempotent for the same admin: clicking twice answers 200 twice and does not push the
     * reservation's clock forward.
     *
     * @param type        which table the item lives in: {@code approval_request} or
     *                    {@code game_table}, the vocabulary {@code entity_type} already uses
     * @param id          the row's id in that table
     * @param currentUser the actor, from the token
     * @return 200 with the item as it now stands. 404 when the type names nothing or the row is gone,
     *         409 {@code ITEM_ALREADY_CLAIMED} when another admin holds it
     */
    @PostMapping("/{type}/{id}/claim")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public QueueItemResponse claim(
            @PathVariable String type, @PathVariable String id, @AuthenticationPrincipal CurrentUser currentUser) {
        return queueService.claim(type, id, currentUser.userId());
    }

    /**
     * Gives an item back.
     *
     * <p>204 and no body, because there is nothing left to say: the item is back in everybody's tray
     * and the screen refetches it. Releasing something nobody holds is a 204 too - the state asked for
     * already holds.
     *
     * @param type        which table the item lives in
     * @param id          the row's id in that table
     * @param currentUser the actor, from the token
     */
    @DeleteMapping("/{type}/{id}/claim")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public void release(
            @PathVariable String type, @PathVariable String id, @AuthenticationPrincipal CurrentUser currentUser) {
        queueService.release(type, id, currentUser.userId());
    }
}
