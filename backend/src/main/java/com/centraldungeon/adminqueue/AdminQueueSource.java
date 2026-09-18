package com.centraldungeon.adminqueue;

import com.centraldungeon.common.exception.NotFoundException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Which table an item of the shared tray came from, and the {@code {type}} segment of the two
 * reservation routes.
 *
 * <p><b>The spelling is not invented here.</b> It is the vocabulary {@code approval_requests
 * .entity_type} already uses (#78) and that {@code Notification.relatedEntityType} publishes: the
 * singular of the table, in snake case - {@code game_table}, never {@code gameTable} or
 * {@code table}. A second vocabulary for the same things is how a frontend ends up with two maps
 * saying the same thing and a bug living between them.
 *
 * <p>Coarse on purpose, and different from {@link AdminQueueItemKind}: this one says <em>where the
 * row lives</em>, which is what decides how to lock it and where its reservation columns are. The
 * kind says what the reader is looking at. Today they happen to be one-to-one; F5 breaks that as soon
 * as {@code comments} contributes more than one kind of item, so the two are separate from the start.
 */
public enum AdminQueueSource {

    /** A row of {@code approval_requests}. */
    APPROVAL_REQUEST("approval_request"),

    /** A row of {@code game_tables}. */
    GAME_TABLE("game_table");

    /** What the URL carries and what the item publishes as its {@code type}. */
    private final String wireName;

    AdminQueueSource(String wireName) {
        this.wireName = wireName;
    }

    /**
     * Returns what the URL carries and what the item publishes.
     *
     * @return the wire name, snake case
     */
    public String wireName() {
        return wireName;
    }

    /**
     * Resolves the {@code {type}} of a reservation route.
     *
     * <p><b>404 and not 400 for an unknown type</b>, because the segment is part of the path and not
     * of a body: {@code /admin-queue/nonsense/abc/claim} names a resource that does not exist, the
     * same answer any other unrouted id gets. A 400 would be telling the caller their request was
     * malformed when what they asked for simply is not there.
     *
     * @param wireName the path segment, in any case
     * @return the source it names
     * @throws NotFoundException when it names none
     */
    public static AdminQueueSource require(String wireName) {
        return fromWireName(wireName)
                .orElseThrow(() -> new NotFoundException("Unknown admin queue item type: " + wireName));
    }

    /**
     * Looks a source up by the name the API publishes.
     *
     * @param wireName the wire name, in any case
     * @return the matching constant, or empty when it names none
     */
    public static Optional<AdminQueueSource> fromWireName(String wireName) {
        String normalized = wireName.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(source -> source.wireName.equals(normalized)).findFirst();
    }
}
