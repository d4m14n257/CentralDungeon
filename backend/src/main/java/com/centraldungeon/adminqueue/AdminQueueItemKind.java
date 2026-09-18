package com.centraldungeon.adminqueue;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * What kind of work an item of the shared admin tray is - the fine discriminator, next to the coarse
 * one that says which table the row came from ({@link AdminQueueSource}).
 *
 * <p>A code and not a sentence (#197): the backend says <em>what</em> is waiting and the frontend
 * writes the phrase in the reader's language, exactly as {@code MasterWorkItemKind} does for the
 * master's tray.
 *
 * <p><b>Two values, not four.</b> {@code docs/modelo-datos.md} §5 lists four sources - the two here,
 * plus {@code comments} in {@code Under review} and {@code system_feedback} in {@code New} - and the
 * last two land in F5 <em>with the features that produce them</em>. Declaring them today would create
 * two constants nothing can emit, which is the orphan this phase already paid for once
 * ({@code ApprovalRequestType} documents the same decision).
 */
public enum AdminQueueItemKind {

    /** Somebody asked for something and nobody has answered yet: {@code approval_requests.Pending}. */
    ApprovalRequest,

    /**
     * A master sent their table to review and it is sitting there: {@code game_tables.Preparation},
     * and <b>only</b> that.
     *
     * <p>Not {@code Draft}, which nobody has sent; not {@code ChangesRequested}, where the ball is
     * back with the master; not {@code Unassigned}, which is waiting for a master rather than for a
     * review (modelo-datos.md §5, #245). A tray that listed those would be showing an admin work that
     * is not theirs to do.
     */
    TableWaitingReview;

    /**
     * Returns the name this kind travels under - <b>the single definition of that spelling</b>.
     *
     * <p>Be precise about what protects what here, because it is easy to read this as more than it
     * is. {@link com.centraldungeon.adminqueue.dto.AdminQueueItemResponse#kind()} is a
     * {@code String}, so <b>this enum does not itself cross HTTP</b>: what reaches the wire is
     * whatever {@code AdminQueueService} put in that field, and it puts {@code wireName()}. The
     * guarantee is therefore "one method decides the spelling and every writer calls it", not "Jackson
     * enforces it".
     *
     * <p>{@code @JsonValue} is here anyway, and it earns its place: it is what makes the enum safe to
     * accept in a body or to publish directly the day something does either - F5 adding two kinds, or
     * a filter that takes one. Without it that day is the one #253 describes, where every response
     * publishes a value every request is rejected for, and {@code PlatformRole} has already paid for
     * it once in this phase. {@code AdminQueueItemKindJsonTest} pins both directions <em>and</em> pins
     * that the DTO's string is the same string, which is the half that could actually drift today.
     *
     * @return the wire name, never null
     */
    @JsonValue
    public String wireName() {
        return name();
    }

    /**
     * Looks a kind up by the name the API publishes.
     *
     * <p>The forgiving door, the same shape {@code ApprovalRequestType.fromWireName} has: case
     * insensitive, and an unknown name is nobody rather than a 400.
     *
     * @param wireName a kind name, in any case
     * @return the matching constant, or empty when it is not one of them
     */
    public static Optional<AdminQueueItemKind> fromWireName(String wireName) {
        String normalized = wireName.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(kind -> kind.name().toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst();
    }
}
