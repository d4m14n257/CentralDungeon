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
 * Emits rows only - no WebSocket push yet (that lands in F5, plan-desarrollo.md). "Personal"
 * notifications only; the shared admin queue signal (#101) is a different mechanism, out of E1's
 * scope.
 *
 * <p><b>No method here writes a sentence</b> (#197). Each one records the type of thing that
 * happened and the names involved; the sentence is rendered by whoever opens their inbox, in the
 * language they chose. Before #197 the text was written here in Spanish - which froze every row in
 * the language it happened to be created in.
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
     * @param userId the applicant
     * @param table  the table they were accepted into
     */
    @Transactional
    public void notifyRegistrationAccepted(String userId, GameTable table) {
        User recipient = userRepository.getReferenceById(userId);
        notificationRepository.save(new Notification(
                recipient, NotificationType.RegistrationAccepted, NotificationParams.ofTable(table.getName()),
                "game_table", table.getId()));
    }

    /**
     * Tells an applicant their application was turned down.
     *
     * <p>The master's reason is <b>not</b> copied in here. It is already stored on the rejection
     * itself, and it is text a person wrote: duplicating it into a second row would mean two places
     * to keep in step, and the screen this links to shows it verbatim anyway.
     *
     * @param userId the applicant
     * @param table  the table they applied to
     */
    @Transactional
    public void notifyRegistrationRejected(String userId, GameTable table) {
        User recipient = userRepository.getReferenceById(userId);
        notificationRepository.save(new Notification(
                recipient, NotificationType.RegistrationRejected, NotificationParams.ofTable(table.getName()),
                "game_table", table.getId()));
    }

    /**
     * Every master of the table is notified, Primary and Secondary alike - both can act on candidates.
     *
     * <p>The applicant's name travels as a parameter so the rendered title names them: the bell shows
     * only the title, and "somebody applied" is a worse headline than "Carla applied" (#156).
     *
     * @param masterUserId  one master of the table; the caller loops over all of them
     * @param table         the table applied to
     * @param applicantName the applicant's display name
     */
    @Transactional
    public void notifyNewCandidate(String masterUserId, GameTable table, String applicantName) {
        User recipient = userRepository.getReferenceById(masterUserId);
        NotificationParams params = new NotificationParams(table.getName(), null, applicantName, null);
        notificationRepository.save(
                new Notification(recipient, NotificationType.NewCandidate, params, "game_table", table.getId()));
    }

    /**
     * Tells somebody that two of their tables now fall at the same time (#178).
     *
     * <p>It carries no action of its own on purpose: the notification names both tables and stops
     * there, because deciding which one to keep is the person's call and not the system's (#70). The
     * way out is on the screen it links to - withdrawing the application, or talking to the master.
     *
     * @param userId         who has the clash
     * @param table          the table the notification links to. R4 points at the application that
     *                       clashes, so the person lands where they can act
     * @param otherTableName the table it clashes with, named in the rendered message so the person
     *                       does not have to work out which of theirs it was
     */
    @Transactional
    public void notifyScheduleConflict(String userId, GameTable table, String otherTableName) {
        User recipient = userRepository.getReferenceById(userId);
        NotificationParams params = new NotificationParams(table.getName(), otherTableName, null, null);
        notificationRepository.save(
                new Notification(recipient, NotificationType.ScheduleConflict, params, "game_table", table.getId()));
    }

    /**
     * Tells the people signed up to a table that its calendar moved (#33).
     *
     * <p>It names no date. One notification covers a single correction and a whole re-laying after a
     * pause alike, and carrying an instant would mean handing the reader a UTC time to convert in
     * their head - the conversion belongs on the screen it links to, where {@code lib/date.ts} does
     * it (#22).
     *
     * @param userId the person signed up to the table
     * @param table  the table whose calendar moved; the notification links to it
     */
    @Transactional
    public void notifySessionScheduled(String userId, GameTable table) {
        User recipient = userRepository.getReferenceById(userId);
        notificationRepository.save(new Notification(
                recipient, NotificationType.SessionScheduled, NotificationParams.ofTable(table.getName()),
                "game_table", table.getId()));
    }

    /**
     * Tells the people signed up to a table that one of its sessions was called off.
     *
     * <p>The rendered message says the run is not shorter, because that is the first thing a player
     * wonders: the table gets the session back at the end (#194).
     *
     * @param userId the person signed up to the table
     * @param table  the table the session belonged to
     */
    @Transactional
    public void notifySessionCanceled(String userId, GameTable table) {
        User recipient = userRepository.getReferenceById(userId);
        notificationRepository.save(new Notification(
                recipient, NotificationType.SessionCanceled, NotificationParams.ofTable(table.getName()),
                "game_table", table.getId()));
    }

    /**
     * Tells somebody that a table is asking them for something (#77).
     *
     * <p>Sent once, at publication - correcting the task later says nothing, because a request fixed
     * three times ringing three times is how people learn to ignore the bell.
     *
     * <p>It carries the task's title so the bell can name <em>which</em> request arrived: "the table
     * is asking you for something" is a worse headline than "Ficha de personaje", the same reasoning
     * #156 gives for naming the applicant in {@link #notifyNewCandidate}.
     *
     * <p>It links to the table and not to the task. There is no screen for one task on its own: a
     * request is read next to the others of its table, which is where the answer is handed in too.
     *
     * @param userId    the recipient, resolved from the task's audience (#63)
     * @param table     the table doing the asking; the notification links to it
     * @param taskTitle what is being asked, named in the rendered title
     */
    @Transactional
    public void notifyTaskPublished(String userId, GameTable table, String taskTitle) {
        User recipient = userRepository.getReferenceById(userId);
        NotificationParams params = new NotificationParams(table.getName(), null, null, taskTitle);
        notificationRepository.save(
                new Notification(recipient, NotificationType.TaskPublished, params, "game_table", table.getId()));
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
