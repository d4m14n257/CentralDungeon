package com.centraldungeon.approvals;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * What somebody is asking for. One mechanism, several requests (#42, #90).
 *
 * <p><b>Three, not five.</b> The baseline DDL comments five types and
 * {@code docs/fase-3-admin-owner.md} enumerates them, but {@code TablePause} and {@code PlayerBan}
 * have nobody to produce them until F3.4, and they arrive there <em>with</em> their producer. That is
 * the point of #78: the column is a {@code VARCHAR(32)}, so adding a flow is adding a constant here
 * and never an {@code ALTER TABLE}. Declaring the two today would buy nothing and would create two
 * enum values nothing emits - which is exactly the orphan F1.7 relevó in {@code PauseRequested} and
 * the one this slice exists to close.
 *
 * <p><b>Every type carries the kind of entity its request is about</b> ({@link #entityType()}). The
 * reference is polymorphic and has no foreign key (#78, #126), so the pair
 * {@code entity_type}/{@code entity_id} is resolved by the service and by
 * {@link ApprovalOrphanCheckService}, never by a {@code @ManyToOne}. The three types below all answer
 * {@code "user"}: {@code TableOpen} asks for a table that does not exist yet and {@code General} is
 * about no entity at all, and both columns are {@code NOT NULL} - so they point at the person who
 * asked, which is true and keeps the invariant of #78 without a nullable column per flow (#126).
 */
public enum ApprovalRequestType {

    /**
     * "Make me a master." Approving it grants {@code PlatformRole.MASTER} through
     * {@code UserRoleService}, the one place a role is ever written (fase-3-admin-owner.md 4).
     */
    MasterGrant(ApprovalEntityResolver.USER),

    /**
     * "Open a table, there is nothing to play." Approving it records that the request stands; it does
     * <b>not</b> create the table. The admin creates it {@code Unassigned} and assigns a master (#72),
     * which is the same circuit seen from its other end (#90). Creating it automatically is not a
     * choice that was refused - it is impossible: the request carries no name, no system, no seats and
     * no agenda.
     */
    TableOpen(ApprovalEntityResolver.USER),

    /** Anything else somebody wants to tell the team. Approving or rejecting it has no effect beyond being resolved. */
    General(ApprovalEntityResolver.USER);

    /** What kind of thing this request is about, as {@code approval_requests.entity_type} spells it. */
    private final String entityType;

    ApprovalRequestType(String entityType) {
        this.entityType = entityType;
    }

    /**
     * Returns the name this type travels under.
     *
     * <p><b>{@code @JsonValue} is here on purpose, and it is not decoration.</b> The constants are
     * already spelled the way the wire spells them, so Jackson's default would happen to produce the
     * same string today - and would stop the day a constant is renamed or a type is added with a
     * different wire name. {@code PlatformRole} learned that the expensive way: it crossed HTTP
     * without this and every request naming a role answered 400 while every response published the
     * value it rejected (#253). One method defines the spelling for reading and for writing at once,
     * so the two cannot drift, and {@code ApprovalRequestTypeJsonTest} pins both directions.
     *
     * @return the wire name, never null
     */
    @JsonValue
    public String wireName() {
        return name();
    }

    /**
     * What kind of entity a request of this type is about.
     *
     * <p>The service asks this <em>before</em> inserting and again before resolving, and the orphan
     * sweep asks it of every unresolved row. Keeping the answer on the type is what lets F3.4 add a
     * flow pointing at a {@code game_table} without touching either of them.
     *
     * @return the entity type, as {@code approval_requests.entity_type} spells it
     */
    public String entityType() {
        return entityType;
    }

    /**
     * Looks a type up by the name the API publishes.
     *
     * <p>The second door, the same shape {@code PlatformRole.fromRoleName} has and for the same
     * reason: a type name reaches the backend as JSON in a request body, resolved by
     * {@link #wireName()}'s {@code @JsonValue}, and as literal text inside {@code ?q=} when somebody
     * types {@code /request_type MasterGrant}. This one is the forgiving door - a search box has to
     * survive being typed into, so it matches case-insensitively and an unknown name is simply
     * nobody rather than a 400 (arquitectura.md 2.5).
     *
     * @param wireName a type name, in any case
     * @return the matching constant, or empty when it is not one of the three
     */
    public static Optional<ApprovalRequestType> fromWireName(String wireName) {
        String normalized = wireName.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(type -> type.name().toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst();
    }
}
