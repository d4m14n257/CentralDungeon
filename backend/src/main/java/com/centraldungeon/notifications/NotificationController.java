package com.centraldungeon.notifications;

import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.common.security.CurrentUser;
import com.centraldungeon.notifications.dto.NotificationResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * A person's own inbox. Every endpoint here is scoped to the actor from the token: there is no way
 * to ask for somebody else's notifications, because the user id is not a parameter anywhere (#121).
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    /** The only collaborator: a controller never reaches a repository (regla dura 1). */
    private final NotificationService notificationService;

    /**
     * @param notificationService the service that owns the inbox
     */
    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * The actor's inbox, newest first - what the bell and the notifications screen read.
     *
     * @param pageable    page, size and sort; newest first, with a tie-break by id (#171)
     * @param currentUser the actor, from the token
     * @return 200 with one page of their notifications
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PageResponse<NotificationResponse> listMine(
            @PageableDefault(sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return notificationService.listMine(currentUser.userId(), pageable);
    }

    /**
     * Marks one notification as seen.
     *
     * @param id          the notification
     * @param currentUser the actor, from the token; the service refuses a notification that is not
     *                    theirs (#121)
     * @return nothing - 204, because the client already knows what changed
     */
    @PatchMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAsRead(@PathVariable String id, @AuthenticationPrincipal CurrentUser currentUser) {
        notificationService.markAsRead(id, currentUser.userId());
    }

    /**
     * Empties the unread count in one call.
     *
     * @param currentUser the actor, from the token; only their own notifications are touched
     * @return nothing - 204
     */
    @PatchMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllAsRead(@AuthenticationPrincipal CurrentUser currentUser) {
        notificationService.markAllAsRead(currentUser.userId());
    }
}
