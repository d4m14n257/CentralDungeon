package com.centraldungeon.notifications.dto;

import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * One notification as the bell and the notifications screen show it.
 *
 * @param id                the notification's identifier
 * @param notificationType  which event this is, as a string. The frontend maps it to an icon and to
 *                          where clicking it should go
 * @param title             the headline. The bell shows only this, so it has to be readable alone (#156)
 * @param message           the detail, or null when the title says everything
 * @param relatedEntityType what the notification is about ("game_table"), or null when it points
 *                          nowhere
 * @param relatedEntityId   the id of that thing, so the click can navigate. Null with the type
 * @param readStatus        whether it has been seen, as a string
 * @param createdAt         when it was emitted, in UTC. The frontend renders it as relative time
 */
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
