package com.centraldungeon.notifications;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Reads and writes the {@code notifications} table. */
public interface NotificationRepository extends JpaRepository<Notification, String> {

    /**
     * Someone's inbox, newest first - the order the bell and the notifications screen read in.
     *
     * @param userId   the recipient, always the actor from the token (#121)
     * @param pageable page and size
     * @return one page of their notifications
     */
    Page<Notification> findByUser_IdOrderByCreatedAtDesc(String userId, Pageable pageable);

    /**
     * Backs "mark all as read", and the unread count with it.
     *
     * @param userId     the recipient, always the actor from the token
     * @param readStatus the status to match, in practice {@link ReadStatus#Unread}
     * @return their notifications in that status
     */
    List<Notification> findByUser_IdAndReadStatus(String userId, ReadStatus readStatus);
}
