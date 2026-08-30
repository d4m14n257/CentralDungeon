package com.centraldungeon.notifications;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, String> {

    Page<Notification> findByUser_IdOrderByCreatedAtDesc(String userId, Pageable pageable);
}
