package com.centraldungeon.approvals;

import com.centraldungeon.registrations.TableRegistrationRepository;
import com.centraldungeon.registrations.TableRegistrationStatus;
import com.centraldungeon.tables.GameTableRepository;
import com.centraldungeon.tables.GameTableStatus;
import com.centraldungeon.users.UserRepository;
import org.springframework.stereotype.Component;

/**
 * The half of {@code entity_type} / {@code entity_id} that the database cannot do.
 *
 * <p>There is no foreign key behind those two columns and there never will be (#78, #126): a
 * reference that can point at three different tables cannot be constrained by one. So the question
 * "does what this row points at still exist?" has to be answered in code, and this is the single
 * place that answers it - for three callers that would otherwise each grow their own copy:
 *
 * <ul>
 *   <li>{@link ApprovalService#submit} asks <em>before</em> inserting, so a request is never written
 *       against something that is not there.</li>
 *   <li>{@code ApprovalService.approve} / {@code reject} ask again before acting, because the row
 *       outlives what it points at on purpose - «una solicitud sobre una mesa borrada sigue siendo un
 *       hecho» (#126) - but resolving one would be acting on a ghost.</li>
 *   <li>{@link ApprovalOrphanCheckService} asks it of every unresolved row, on a schedule, so the
 *       answer shows up in a log while it can still be traced.</li>
 * </ul>
 *
 * <p><b>Adding a request type that points somewhere new means adding a case here.</b> That is the
 * whole maintenance cost of the polymorphic reference, and it is deliberately concentrated in one
 * switch rather than spread over the service. F3.4 added {@code game_table} and
 * {@code table_registration}, which is exactly what this note predicted, and the throwing
 * {@code default} is what would have caught the case being forgotten.
 *
 * <p><b>A soft-deleted entity counts as absent</b>, which is the question F3.2 left open and F3.4
 * answered. The resolver reads the same way the rest of the application does (#25), so a request
 * about a deleted table answers {@code REQUEST_ENTITY_GONE} when somebody tries to resolve it -
 * the first time that code is reachable at all. F3.2's own debt table said so: «hoy es inalcanzable
 * [...] empieza a dispararse en F3.4».
 */
@Component
public class ApprovalEntityResolver {

    /**
     * The only entity type F3.2 produces. The three types of this slice are all about the person who
     * asked: {@code TableOpen} is about a table that does not exist yet and {@code General} is about
     * nothing, and both columns are {@code NOT NULL}, so pointing at the requester is the one answer
     * that is true and needs no nullable column per flow (#126).
     */
    public static final String USER = "user";

    /**
     * The table a {@code TablePause} is about (#32).
     *
     * <p>Added by F3.4 together with the flow that produces it, which is the whole discipline of
     * #78: the type and its case arrive in the same commit or the {@code default} below fires.
     */
    public static final String GAME_TABLE = "game_table";

    /** The application a {@code PlayerBan} is about (#39). */
    public static final String TABLE_REGISTRATION = "table_registration";

    /** Resolves {@link #USER}. {@code existsById} and not a load: the answer is a boolean. */
    private final UserRepository userRepository;

    /** Resolves {@link #GAME_TABLE}. A full load, not an {@code existsById} - see {@link #exists}. */
    private final GameTableRepository gameTableRepository;

    /** Resolves {@link #TABLE_REGISTRATION}, the same way and for the same reason. */
    private final TableRegistrationRepository registrationRepository;

    /**
     * @param userRepository         the {@code users} table, asked only whether a row is there
     * @param gameTableRepository    the {@code game_tables} table, asked whether the row is there
     *                               <em>and</em> still alive (#25)
     * @param registrationRepository the {@code table_registrations} table, asked the same
     */
    public ApprovalEntityResolver(
            UserRepository userRepository,
            GameTableRepository gameTableRepository,
            TableRegistrationRepository registrationRepository) {
        this.userRepository = userRepository;
        this.gameTableRepository = gameTableRepository;
        this.registrationRepository = registrationRepository;
    }

    /**
     * Whether the thing a request points at is still there.
     *
     * <p><b>An entity type this resolver does not know is a bug, and it says so.</b> This used to
     * answer {@code false}, which never lied in the dangerous direction - a request was never treated
     * as verified when it could not be - but it gave the wrong diagnosis, which is its own kind of
     * damage. Conflating "I do not know this type" with "the entity is gone" means that the day F3.4
     * adds {@code game_table} and forgets the case here, two concrete things happen: every
     * {@code submit} of that type answers 404 "cannot open a request about a missing game_table",
     * sending somebody to look for a deleted table instead of at the incomplete switch; and the sweep
     * logs a WARN for every row of that type, for ever, indistinguishable from a real orphan - which
     * is exactly the signal the job exists to produce. An exception names the actual fault, once.
     *
     * <p><b>Existing and being gone are different questions, and this answers the second.</b> The
     * {@code USER} case asks {@code existsById} because a user is never soft-deleted out of
     * {@code users}; the two F3.4 added load the row and look at its status, because a table and an
     * application both have a {@code Deleted} state and #25 says a soft-deleted thing does not exist
     * for any read. A resolver that answered "yes, it is there" about a deleted table would let an
     * admin resolve a request to pause something nobody can see.
     *
     * @param entityType what kind of thing, as {@code approval_requests.entity_type} spells it
     * @param entityId   the id of that thing
     * @return true when a row with that id exists in the table the type names, and is not
     *         soft-deleted
     * @throws IllegalStateException when the entity type has no case here - a flow was added without
     *                               teaching this resolver about it, which is a programming error and
     *                               not a state the rest of the application should try to handle
     */
    public boolean exists(String entityType, String entityId) {
        return switch (entityType) {
            case USER -> userRepository.existsById(entityId);
            case GAME_TABLE -> gameTableRepository
                    .findById(entityId)
                    .filter(table -> table.getStatus() != GameTableStatus.Deleted)
                    .isPresent();
            case TABLE_REGISTRATION -> registrationRepository
                    .findById(entityId)
                    .filter(registration -> registration.getStatus() != TableRegistrationStatus.Deleted)
                    .isPresent();
            default -> throw new IllegalStateException("Unknown approval entity type: " + entityType);
        };
    }
}
