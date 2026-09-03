package com.centraldungeon.notifications.dto;

import com.centraldungeon.notifications.NotificationParams;
import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * One notification as the bell and the notifications screen show it.
 *
 * <p><b>It carries no rendered sentence</b> (#197). The frontend builds the title and the message
 * from {@code notificationType} and {@code params}, in the language the reader chose - which is the
 * only way a row written months ago can still be read in the language of today.
 *
 * <p>{@code title} and {@code message} are the frozen text of a row written before that change, and
 * they are the fallback for exactly those rows. New rows leave them null.
 *
 * @param id                the notification's identifier
 * @param notificationType  which event this is, as a string. It picks the sentence to render, and
 *                          the icon, and where clicking it should go
 * @param params            the names that sentence needs filled in, or null on a pre-#197 row
 * @param title             the frozen headline of a pre-#197 row, or null
 * @param message           the frozen detail of a pre-#197 row, or null
 * @param relatedEntityType what the notification is about ("game_table"), or null when it points
 *                          nowhere
 * @param relatedEntityId   the id of that thing, so the click can navigate. Null with the type
 * @param readStatus        whether it has been seen, as a string
 * @param createdAt         when it was emitted, in UTC. The frontend renders it as relative time
 */
public record NotificationResponse(
        String id,
        String notificationType,
        @Nullable NotificationParams params,
        @Nullable String title,
        @Nullable String message,
        @Nullable String relatedEntityType,
        @Nullable String relatedEntityId,
        String readStatus,
        LocalDateTime createdAt) {
}
