package com.centraldungeon.notifications;

import com.centraldungeon.common.exception.ForbiddenActionException;
import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.notifications.dto.NotificationResponse;
import com.centraldungeon.tables.GameTable;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emits rows only in E1 - no WebSocket push yet (that lands in E6). "Personal" notifications only;
 * the shared admin queue signal (#101) is a different mechanism, out of E1's scope.
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationMapper notificationMapper;

    public NotificationService(
            NotificationRepository notificationRepository, UserRepository userRepository, NotificationMapper notificationMapper) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.notificationMapper = notificationMapper;
    }

    /**
     * Titles/messages are content a player reads, not code (decisiones.md #34, #102 does not apply to
     * them) - written in Spanish directly, same precedent as RegistrationService's auto-reject justification.
     */
    @Transactional
    public void notifyRegistrationAccepted(String userId, GameTable table) {
        User recipient = userRepository.getReferenceById(userId);
        String title = "Te aceptaron en " + table.getName();
        notificationRepository.save(
                new Notification(recipient, NotificationType.RegistrationAccepted, title, null, "game_table", table.getId()));
    }

    @Transactional
    public void notifyRegistrationRejected(String userId, GameTable table, String justification) {
        User recipient = userRepository.getReferenceById(userId);
        String title = "Tu postulación a " + table.getName() + " fue rechazada";
        notificationRepository.save(new Notification(
                recipient, NotificationType.RegistrationRejected, title, justification, "game_table", table.getId()));
    }

    /**
     * Every master of the table is notified, Primary and Secondary alike - both can act on candidates.
     * The applicant's name goes in the title, not message - readable at a glance from the bell, which
     * only ever shows the title (frontend-diseno.md, decisiones.md #156).
     */
    @Transactional
    public void notifyNewCandidate(String masterUserId, GameTable table, String applicantName) {
        User recipient = userRepository.getReferenceById(masterUserId);
        String title = applicantName + " se postuló a " + table.getName();
        notificationRepository.save(new Notification(recipient, NotificationType.NewCandidate, title, null, "game_table", table.getId()));
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> listMine(String userId, Pageable pageable) {
        Page<Notification> page = notificationRepository.findByUser_IdOrderByCreatedAtDesc(userId, pageable);
        return PageResponse.from(page.map(notificationMapper::toResponse));
    }

    @Transactional
    public void markAsRead(String notificationId, String actorId) {
        Notification notification =
                notificationRepository.findById(notificationId).orElseThrow(() -> new NotFoundException("Notification not found: " + notificationId));
        if (!notification.getUser().getId().equals(actorId)) {
            throw new ForbiddenActionException("Cannot mark another user's notification as read");
        }
        notification.markRead();
    }

    @Transactional
    public void markAllAsRead(String actorId) {
        for (Notification notification : notificationRepository.findByUser_IdAndReadStatus(actorId, ReadStatus.Unread)) {
            notification.markRead();
        }
    }
}
