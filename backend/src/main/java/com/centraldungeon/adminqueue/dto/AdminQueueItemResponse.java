package com.centraldungeon.adminqueue.dto;

import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * One line of the shared admin tray: something that is waiting for an admin, whichever table it
 * lives in (#100).
 *
 * <p><b>The common shape four different rows are normalized to.</b> That normalization is what makes
 * the tray one screen instead of four - #100 asked for a {@code UNION ALL} and what it actually wants
 * is this record: a pending request, a table sitting in review, and in F5 a comment under review and
 * a piece of feedback, all answering the same five questions. What is it, who is waiting, since when,
 * and whether somebody already took it.
 *
 * <p>The two people are <b>names and never ids</b>, resolved by the service: a tray that printed
 * {@code 0192f3...} would be asking the reader to go look somebody up before they can decide what to
 * do, which is the opposite of a work tray (#136).
 *
 * @param type            which table the row came from, as {@code AdminQueueSource} spells it:
 *                        {@code approval_request} or {@code game_table}. It is half of the address of
 *                        the two reservation routes, the other half being {@link #id}
 * @param id              the row's id <b>in its own table</b>, not a synthetic key of the tray. There
 *                        is no {@code admin_queue} table and there is not going to be (#11): the tray
 *                        is a view over work that already exists somewhere
 * @param kind            the fine discriminator, as {@code AdminQueueItemKind} spells it. What the
 *                        frontend picks the icon and the sentence from (#197)
 * @param title           what the item is about at a glance: the table's name, or the kind of request
 * @param requestedByName who provoked it - the person who asked, or the master whose table is in
 *                        review. A display name, always
 * @param detail          the justification the requester wrote. Null for a table, which has none: the
 *                        table <em>is</em> the detail, and it is read by opening it
 * @param waitingSince    since when it has been waiting. <b>It is what orders the tray</b>, oldest
 *                        first: urgency here is time, not volume, the same rule the master's tray
 *                        follows (#136)
 * @param claimedByName   the admin who reserved it, or null when it is free. An item another admin
 *                        holds is not in this listing at all (#100), so a non-null value here always
 *                        names the reader themselves - and the screen shows it so they can find what
 *                        they took
 * @param claimedAt       when they took it, so the screen can say how long ago. Null when free
 */
public record AdminQueueItemResponse(
        String type,
        String id,
        String kind,
        String title,
        String requestedByName,
        @Nullable String detail,
        LocalDateTime waitingSince,
        @Nullable String claimedByName,
        @Nullable LocalDateTime claimedAt) {
}
