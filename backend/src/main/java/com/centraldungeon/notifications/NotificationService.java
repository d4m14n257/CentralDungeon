package com.centraldungeon.notifications;

import com.centraldungeon.common.exception.ForbiddenActionException;
import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.notifications.dto.NotificationResponse;
import com.centraldungeon.tables.GameTable;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserRepository;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emits rows only - no WebSocket push yet (that lands in F6, ADR-18 §2). "Personal"
 * notifications only; the shared admin queue signal (ADR-18 §2) is a different mechanism, out of E1's
 * scope.
 *
 * <p><b>No method here writes a sentence</b> (ADR-23 §3). Each one records the type of thing that
 * happened and the names involved; the sentence is rendered by whoever opens their inbox, in the
 * language they chose. Before ADR-23 §3 the text was written here in Spanish - which froze every row in
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
     * only the title, and "somebody applied" is a worse headline than "Carla applied" (ADR-18 §3).
     *
     * @param masterUserId  one master of the table; the caller loops over all of them
     * @param table         the table applied to
     * @param applicantName the applicant's display name
     */
    @Transactional
    public void notifyNewCandidate(String masterUserId, GameTable table, String applicantName) {
        User recipient = userRepository.getReferenceById(masterUserId);
        NotificationParams params = new NotificationParams(table.getName(), null, applicantName, null, null, null, null);
        notificationRepository.save(
                new Notification(recipient, NotificationType.NewCandidate, params, "game_table", table.getId()));
    }

    /**
     * Tells somebody they are now a master of a table (ADR-06 §7, §4, ADR-18 §3).
     *
     * <p><b>The recipient did nothing to cause this</b>, which is what makes it the notification that
     * cannot be skipped: an admin creates a table and assigns it, and until now the person only found
     * out by wandering into their Master context and seeing a table they had never seen before. Every
     * other notification follows an act of theirs or lands on a table they already knew.
     *
     * <p>It is sent to co-masters too, and by the same reasoning: being added to somebody else's
     * table is news for whoever is added, whether or not they hold the {@code Master} role (ADR-06 §4).
     *
     * @param userId the new master
     * @param table  the table they now run; the notification links to it
     */
    @Transactional
    public void notifyMasterAssigned(String userId, GameTable table) {
        User recipient = userRepository.getReferenceById(userId);
        notificationRepository.save(new Notification(
                recipient, NotificationType.MasterAssigned, NotificationParams.ofTable(table.getName()),
                "game_table", table.getId()));
    }

    /**
     * Tells a master their table came out of review (ADR-07 §1, ADR-18 §3).
     *
     * <p>Review is a wait whose other side the master cannot see: the table sits there and nothing on
     * their screen changes until an admin acts. These are the three ways out, and each is different
     * news - it passed, it passed but somebody edited it, or it is back in their hands.
     *
     * <p>The reason for a rejection is <b>not</b> carried here. It is already mandatory on the
     * transition and kept in the status history, which is where the master reads it in full; a copy in
     * the notification would be a second one, free to drift from the first (ADR-23 §3).
     *
     * @param userId one master of the table; the caller loops over all of them
     * @param table  the table that was reviewed
     * @param type   which of the three outcomes it was
     */
    @Transactional
    public void notifyReviewOutcome(String userId, GameTable table, NotificationType type) {
        User recipient = userRepository.getReferenceById(userId);
        notificationRepository.save(new Notification(
                recipient, type, NotificationParams.ofTable(table.getName()), "game_table", table.getId()));
    }

    /**
     * Tells somebody joined to a table that it changed under them: paused, resumed, cancelled,
     * finished, or its agenda moved - or, for a master, that an admin did one of the first three
     * (ADR-06 §8).
     *
     * <p>One method for all of them because they are the same message with a different verb. Who
     * acted is never named, the way ADR-06 §9 shows any change an admin makes on somebody else's
     * behalf, and the reason is not copied in: it is mandatory on the transition and kept with the
     * status change, which is where it is read in full (ADR-23 §3).
     *
     * @param userId     one person joined to the table; the caller loops over all of them
     * @param table      the table that changed; the notification links to it
     * @param type       what happened. One of the plain types or a {@code …ByAdmin} variant, never
     *                   {@link NotificationType#TableDeletedByAdmin}, which links nowhere
     * @param membership how the recipient is joined, so the click lands on their own screen
     */
    @Transactional
    public void notifyTableChanged(String userId, GameTable table, NotificationType type, TableMembership membership) {
        User recipient = userRepository.getReferenceById(userId);
        notificationRepository.save(new Notification(
                recipient, type, NotificationParams.ofTable(table.getName(), membership), "game_table", table.getId()));
    }

    /**
     * Tells a master that an admin rewrote their table, and which parts (ADR-06 §8).
     *
     * @param userId  one master of the table; the caller loops over all of them
     * @param table   the table the admin rewrote; the notification links to it
     * @param changes the codes of the fields that changed, never empty
     */
    @Transactional
    public void notifyTableEditedByAdmin(String userId, GameTable table, List<String> changes) {
        User recipient = userRepository.getReferenceById(userId);
        notificationRepository.save(new Notification(
                recipient, NotificationType.TableEditedByAdmin, NotificationParams.ofEdit(table.getName(), changes),
                "game_table", table.getId()));
    }

    /**
     * Tells a master that an admin removed their table (ADR-06 §8).
     *
     * <p>It links nowhere: a removed table cannot be opened (ADR-03 §7), so the name in the message
     * is the whole of what is left to tell.
     *
     * @param userId one master the table had; the caller loops over all of them
     * @param table  the table that was removed
     */
    @Transactional
    public void notifyTableDeleted(String userId, GameTable table) {
        User recipient = userRepository.getReferenceById(userId);
        notificationRepository.save(new Notification(
                recipient, NotificationType.TableDeletedByAdmin, NotificationParams.ofTable(table.getName()), null, null));
    }

    /**
     * Tells somebody that two of their tables now fall at the same time (ADR-08 §2).
     *
     * <p>It carries no action of its own on purpose: the notification names both tables and stops
     * there, because deciding which one to keep is the person's call and not the system's (ADR-15 §2). The
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
        NotificationParams params = new NotificationParams(table.getName(), otherTableName, null, null, null, null, null);
        notificationRepository.save(
                new Notification(recipient, NotificationType.ScheduleConflict, params, "game_table", table.getId()));
    }

    /**
     * Tells the players of a table that its calendar moved.
     *
     * <p>It names no date. Carrying an instant would mean handing the reader a UTC time to convert in
     * their head - the conversion belongs on the screen it links to, where {@code lib/date.ts} does
     * it (ADR-03 §6).
     *
     * @param userId the player of the table
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
     * Tells the players of a table that one of its sessions was called off.
     *
     * <p>The rendered message says the run is not shorter, because that is the first thing a player
     * wonders: the table gets the session back at the end (ADR-08 §3).
     *
     * @param userId the player of the table
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
     * Tells somebody that a table is asking them for something (ADR-15 §5).
     *
     * <p>Sent once, at publication - correcting the task later says nothing, because a request fixed
     * three times ringing three times is how people learn to ignore the bell.
     *
     * <p>It carries the task's title so the bell can name <em>which</em> request arrived: "the table
     * is asking you for something" is a worse headline than "Ficha de personaje", the same reasoning
     * ADR-18 §3 gives for naming the applicant in {@link #notifyNewCandidate}.
     *
     * <p>It links to the table and not to the task. There is no screen for one task on its own: a
     * request is read next to the others of its table, which is where the answer is handed in too.
     *
     * @param userId    the recipient, resolved from the task's audience (ADR-15 §1)
     * @param table     the table doing the asking; the notification links to it
     * @param taskTitle what is being asked, named in the rendered title
     */
    @Transactional
    public void notifyTaskPublished(String userId, GameTable table, String taskTitle) {
        User recipient = userRepository.getReferenceById(userId);
        NotificationParams params = new NotificationParams(table.getName(), null, null, taskTitle, null, null, null);
        notificationRepository.save(
                new Notification(recipient, NotificationType.TaskPublished, params, "game_table", table.getId()));
    }

    /**
     * Tells whoever asked that their request was answered (ADR-16 §1).
     *
     * <p><b>Nothing is sent when the request is made</b>, and that is a rule rather than an omission:
     * admin work items are not duplicated as notifications (ADR-16 §5) - the shared queue is a view over
     * the rows that already exist, and a bell per request would be the copy ADR-16 §5 removed. The
     * resolution is the half nobody can see coming, and only the requester needs it.
     *
     * <p>The reason is <b>not</b> carried here. It is mandatory on the resolution and stored on the
     * request, which is where it is read in full; a copy would be a second one free to disagree with
     * the first (ADR-23 §3).
     *
     * <p><b>It names nothing in its sentence</b>: the rendered text says "aprobaron tu pedido" without
     * saying which request, so no name is stored for it. The one value it may carry is the table's id,
     * and only for a veto, whose related entity is an application: that value is not for the sentence
     * but for the click, which has to land on the table's players.
     *
     * <p>Where it links is the request's own polymorphic reference, passed in by the caller. For the
     * three types of F3.2 that is {@code user} and the requester themselves, so a click lands on
     * their profile - deliberately <b>not</b> on {@code /admin/requests}, which the requester has no
     * role to open.
     *
     * @param userId            the requester
     * @param type              {@link NotificationType#ApprovalRequestApproved} or
     *                          {@link NotificationType#ApprovalRequestRejected} - two outcomes, two
     *                          pieces of news, the same shape {@link #notifyReviewOutcome} has
     * @param relatedEntityType what the request is about, so the click knows where to go
     * @param relatedEntityId   the id of that thing
     * @param tableId           the table the request belongs to when the entity is not the table
     *                          itself (a veto points at an application), so the click can land on it;
     *                          null otherwise
     */
    @Transactional
    public void notifyApprovalResolved(
            String userId,
            NotificationType type,
            String relatedEntityType,
            String relatedEntityId,
            @Nullable String tableId) {
        User recipient = userRepository.getReferenceById(userId);
        NotificationParams params = tableId == null ? NotificationParams.none() : NotificationParams.ofTableId(tableId);
        notificationRepository.save(new Notification(recipient, type, params, relatedEntityType, relatedEntityId));
    }

    /**
     * Somebody's inbox, newest first.
     *
     * @param userId   the recipient, always the actor from the token (ADR-05 §5)
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
     *         belongs to somebody else - a notification is read by its recipient and nobody else (ADR-05 §5)
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
     *                id parameter that could point anywhere else (ADR-05 §5)
     */
    @Transactional
    public void markAllAsRead(String actorId) {
        for (Notification notification : notificationRepository.findByUser_IdAndReadStatus(actorId, ReadStatus.Unread)) {
            notification.markRead();
        }
    }
}
