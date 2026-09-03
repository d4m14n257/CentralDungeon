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
 * Emits rows only - no WebSocket push yet (that lands in F5, plan-desarrollo.md). "Personal" notifications only;
 * the shared admin queue signal (#101) is a different mechanism, out of E1's scope.
 */
@Service
public class NotificationService {

    /** The {@code notifications} table. */
    private final NotificationRepository notificationRepository;

    /** Only used for {@code getReferenceById}: a recipient is a foreign key, not something to load. */
    private final UserRepository userRepository;

    /** Entity to DTO. */
    private final NotificationMapper notificationMapper;

    /**
     * @param notificationRepository the {@code notifications} table
     * @param userRepository         resolves recipients by reference, without loading them
     * @param notificationMapper     entity to DTO
     */
    public NotificationService(
            NotificationRepository notificationRepository, UserRepository userRepository, NotificationMapper notificationMapper) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.notificationMapper = notificationMapper;
    }

    /**
     * Tells an applicant a master accepted them.
     *
     * <p>Titles/messages are content a player reads, not code (decisiones.md #34, #102 does not apply
     * to them) - written in Spanish directly, same precedent as RegistrationService's auto-reject
     * justification.
     *
     * @param userId the applicant
     * @param table  the table they were accepted into
     */
    @Transactional
    public void notifyRegistrationAccepted(String userId, GameTable table) {
        User recipient = userRepository.getReferenceById(userId);
        String title = "Te aceptaron en " + table.getName();
        notificationRepository.save(
                new Notification(recipient, NotificationType.RegistrationAccepted, title, null, "game_table", table.getId()));
    }

    /**
     * Tells an applicant their application was turned down, and why. The reason travels as the
     * message rather than the title: the bell shows only titles (#156), and a rejection reason is
     * something to read on the notifications screen.
     *
     * @param userId        the applicant
     * @param table         the table they applied to
     * @param justification the master's reason, shown to them verbatim
     */
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
     *
     * @param masterUserId  one master of the table; the caller loops over all of them
     * @param table         the table applied to
     * @param applicantName the applicant's display name, so the title reads as a sentence
     */
    @Transactional
    public void notifyNewCandidate(String masterUserId, GameTable table, String applicantName) {
        User recipient = userRepository.getReferenceById(masterUserId);
        String title = applicantName + " se postuló a " + table.getName();
        notificationRepository.save(new Notification(recipient, NotificationType.NewCandidate, title, null, "game_table", table.getId()));
    }

    /**
     * Tells somebody that two of their tables now fall at the same time (#178).
     *
     * <p>It carries no action of its own on purpose: the notification names both tables and stops
     * there, because deciding which one to keep is the person's call and not the system's (#70). The
     * way out is on the screen it links to - withdrawing the application, or talking to the master.
     *
     * @param userId        who has the clash
     * @param table         the table the notification links to. R4 points at the application that
     *                      clashes, so the person lands where they can act
     * @param otherTableName the table it clashes with, named in the message so the person does not
     *                      have to work out which of theirs it was
     */
    @Transactional
    public void notifyScheduleConflict(String userId, GameTable table, String otherTableName) {
        User recipient = userRepository.getReferenceById(userId);
        String title = "Tu horario en " + table.getName() + " choca con otra mesa";
        String message = "Se superpone con " + otherTableName + ". Podés retirar una postulación o hablarlo con el master.";
        notificationRepository.save(
                new Notification(recipient, NotificationType.ScheduleConflict, title, message, "game_table", table.getId()));
    }

    /**
     * Tells the people signed up to a table that its calendar moved (#33).
     *
     * <p>It names no date. One notification covers a single correction and a whole re-laying after a
     * pause alike, and spelling out an instant here would mean writing UTC into a message that the
     * reader would then have to convert in their head - the conversion belongs on the screen it links
     * to, where {@code lib/date.ts} does it (#22).
     *
     * @param userId the person signed up to the table
     * @param table  the table whose calendar moved; the notification links to it
     */
    @Transactional
    public void notifySessionScheduled(String userId, GameTable table) {
        User recipient = userRepository.getReferenceById(userId);
        String title = "Cambió el calendario de " + table.getName();
        String message = "Revisá las fechas de las sesiones en tu hora local.";
        notificationRepository.save(
                new Notification(recipient, NotificationType.SessionScheduled, title, message, "game_table", table.getId()));
    }

    /**
     * Tells the people signed up to a table that one of its sessions was called off.
     *
     * <p>The message says the run is not shorter, because that is the first thing a player wonders:
     * the table gets the session back at the end (#194).
     *
     * @param userId the person signed up to the table
     * @param table  the table the session belonged to
     */
    @Transactional
    public void notifySessionCanceled(String userId, GameTable table) {
        User recipient = userRepository.getReferenceById(userId);
        String title = "Se canceló una sesión de " + table.getName();
        String message = "La mesa suma otra sesión al final, así que sigue siendo la misma cantidad.";
        notificationRepository.save(
                new Notification(recipient, NotificationType.SessionCanceled, title, message, "game_table", table.getId()));
    }

    /**
     * Somebody's inbox, newest first.
     *
     * @param userId   the recipient, always the actor from the token (#121)
     * @param pageable page and size
     * @return one page of their notifications
     */
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> listMine(String userId, Pageable pageable) {
        Page<Notification> page = notificationRepository.findByUser_IdOrderByCreatedAtDesc(userId, pageable);
        return PageResponse.from(page.map(notificationMapper::toResponse));
    }

    /**
     * Marks one notification as seen.
     *
     * @param notificationId the notification
     * @param actorId        the actor, from the token
     * @throws com.centraldungeon.common.exception.ForbiddenActionException if the notification
     *         belongs to somebody else - a notification is read by its recipient and nobody else (#121)
     * @throws com.centraldungeon.common.exception.NotFoundException if it does not exist
     */
    @Transactional
    public void markAsRead(String notificationId, String actorId) {
        Notification notification =
                notificationRepository.findById(notificationId).orElseThrow(() -> new NotFoundException("Notification not found: " + notificationId));
        if (!notification.getUser().getId().equals(actorId)) {
            throw new ForbiddenActionException("Cannot mark another user's notification as read");
        }
        notification.markRead();
    }

    /**
     * Empties somebody's unread count in one call.
     *
     * @param actorId the actor, from the token. Only their own notifications are touched: there is no
     *                id parameter that could point anywhere else (#121)
     */
    @Transactional
    public void markAllAsRead(String actorId) {
        for (Notification notification : notificationRepository.findByUser_IdAndReadStatus(actorId, ReadStatus.Unread)) {
            notification.markRead();
        }
    }
}
