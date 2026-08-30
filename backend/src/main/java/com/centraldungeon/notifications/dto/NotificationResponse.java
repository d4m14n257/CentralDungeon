package com.centraldungeon.notifications.dto;

import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

public record NotificationResponse(
        String id,
        String notificationType,
        String title,
        @Nullable String message,
        @Nullable String relatedEntityType,
        @Nullable String relatedEntityId,
        String readStatus,
        LocalDateTime createdAt) {
}
