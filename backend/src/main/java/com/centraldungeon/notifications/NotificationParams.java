package com.centraldungeon.notifications;

import org.jspecify.annotations.Nullable;

/**
 * The names a notification's sentence needs filled in, stored alongside the row rather than baked
 * into it (#197).
 *
 * <p><b>Why a notification does not store its own text.</b> A row is written once and read for
 * months, and the person reading it can change the language of the application in between. Storing
 * "Te aceptaron en La Cripta" freezes the sentence in the language it happened to be written in;
 * storing the type plus this makes the same row read as "Te aceptaron en La Cripta" or "You were
 * accepted into La Cripta" depending on who is looking and when.
 *
 * <p>A named record and not a free map, because the set of values is small, closed and known: every
 * notification type in {@link NotificationType} fills in some subset of these four. A fifth one
 * arrives with the notification that needs it - {@code taskTitle} is the fourth, and arrived exactly
 * that way with F1.5.
 *
 * <p>Persisted as JSON in a single column by {@link NotificationParamsConverter}. That is a
 * deliberate exception to "a column per field": these values are never filtered, sorted or joined
 * on - they are the arguments of a sentence - so a column each would be schema churn every time a
 * message changes shape. It is also why adding this fourth field needed no migration: a row written
 * before it existed simply has no such key, and reads back with it null.
 *
 * @param tableName      the table the notification is about, for every type that names one
 * @param otherTableName the second table, for a clash that has to name both (#178)
 * @param actorName      the person who did the thing, for the types where somebody acted
 * @param taskTitle      what the table is asking for, so the bell can say <em>which</em> request
 *                       arrived (#77). "The table is asking you for something" is a worse headline
 *                       than "Ficha de personaje", for the same reason #156 gives for naming the
 *                       applicant
 */
public record NotificationParams(
        @Nullable String tableName,
        @Nullable String otherTableName,
        @Nullable String actorName,
        @Nullable String taskTitle) {

    /**
     * The common case: a notification that only has to name the table it is about.
     *
     * @param tableName the table
     * @return the parameters, with nothing else set
     */
    public static NotificationParams ofTable(String tableName) {
        return new NotificationParams(tableName, null, null, null);
    }
}
