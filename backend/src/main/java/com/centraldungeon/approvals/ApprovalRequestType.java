package com.centraldungeon.approvals;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * What somebody is asking for. One mechanism, several requests (#42, #90).
 *
 * <p><b>Five now, and the last two arrived with their producer.</b> F3.2 declared three and wrote
 * down why: {@code TablePause} and {@code PlayerBan} had nobody to emit them, and «declararlos hoy
 * crearía dos valores de enum que nada emite - que es exactamente el huérfano que F1.7 relevó en
 * {@code PauseRequested}». F3.4 is where both producers were built, so both constants land here at
 * the same time. That is also #78 paying off: the column is a {@code VARCHAR(32)}, so adding a flow
 * was adding a constant and never an {@code ALTER TABLE}.
 *
 * <p><b>The two new ones point somewhere other than the requester</b>, which the first three never
 * did. That is what {@link ApprovalEntityResolver}'s switch was left throwing for: a type whose
 * {@code entity_type} has no case there answers a misleading 404 on every submit and logs a WARN
 * for ever in the orphan sweep.
 *
 * <p><b>Every type carries the kind of entity its request is about</b> ({@link #entityType()}). The
 * reference is polymorphic and has no foreign key (#78, #126), so the pair
 * {@code entity_type}/{@code entity_id} is resolved by the service and by
 * {@link ApprovalOrphanCheckService}, never by a {@code @ManyToOne}. The first three types below all answer
 * {@code "user"}: {@code TableOpen} asks for a table that does not exist yet and {@code General} is
 * about no entity at all, and both columns are {@code NOT NULL} - so they point at the person who
 * asked, which is true and keeps the invariant of #78 without a nullable column per flow (#126).
 * The two F3.4 added point at a {@code game_table} and at a {@code table_registration}.
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
    General(ApprovalEntityResolver.USER),

    /**
     * "Pause my table." The master asks, an admin answers (#32).
     *
     * <p>The first type whose entity is not the requester: it points at the {@code game_table},
     * because the request is about the table and a co-master reading the tray has to see which one.
     * Submitting it moves the table to {@code PauseRequested} in the same transaction - which is how
     * a state that F1.7 surveyed as an orphan finally gets a producer.
     *
     * <p>Approving moves it to {@code Pause} with the resolution note as the justification
     * {@code table_status_changes} requires (#32). <b>Rejecting has an effect too</b>, which no type
     * before this one had: the table goes back to {@code InProgress}. A table left sitting in
     * {@code PauseRequested} after being told no would be a table stuck in a waiting room.
     *
     * <p>It goes to the admins, so it <b>is</b> in {@code /admin/queue}.
     */
    TablePause(ApprovalEntityResolver.GAME_TABLE),

    /**
     * "Veto this person from our table." A {@code Secondary} asks; <b>the {@code Primary} answers,
     * and not an admin</b> (#39).
     *
     * <p>It is the one type that does not belong to the admins, and the decision cost the contract
     * its longest paragraph. #90 is generic - {@code approval_requests} covers «todo pedido dirigido
     * a los admins» - and #39 is specific: the veto is the {@code Primary}'s, and a co-master
     * «necesita aprobación del {@code Primary}». The specific one wins, and sensibly: a veto between
     * a co-master and a player of <em>that</em> table is decided by whoever runs it, not by the
     * platform.
     *
     * <p>Two things follow, and both had to be built rather than inherited. {@code AdminQueueService}
     * <b>excludes</b> this type - new types otherwise join the tray for free, which would put work in
     * front of admins that is not theirs. And the authorization to resolve it is by type, inside
     * {@link ApprovalService}, not by an annotation on a controller.
     *
     * <p>It points at the {@code table_registration} being vetoed. Approving applies the veto through
     * {@code RegistrationService}, the one place that writes {@code Blocked}.
     */
    PlayerBan(ApprovalEntityResolver.TABLE_REGISTRATION);

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
     * @return the matching constant, or empty when it is not one of the five
     */
    public static Optional<ApprovalRequestType> fromWireName(String wireName) {
        String normalized = wireName.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(type -> type.name().toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst();
    }
}
