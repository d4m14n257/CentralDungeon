package com.centraldungeon.approvals;

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
 * switch rather than spread over the service. F3.4 adds {@code game_table} and
 * {@code table_registration} when it adds the flows that produce them.
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

    /** Resolves the only entity type there is today. {@code existsById} and not a load: the answer is a boolean. */
    private final UserRepository userRepository;

    /**
     * @param userRepository the {@code users} table, asked only whether a row is there
     */
    public ApprovalEntityResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
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
     * @param entityType what kind of thing, as {@code approval_requests.entity_type} spells it
     * @param entityId   the id of that thing
     * @return true when a row with that id exists in the table the type names
     * @throws IllegalStateException when the entity type has no case here - a flow was added without
     *                               teaching this resolver about it, which is a programming error and
     *                               not a state the rest of the application should try to handle
     */
    public boolean exists(String entityType, String entityId) {
        return switch (entityType) {
            case USER -> userRepository.existsById(entityId);
            default -> throw new IllegalStateException("Unknown approval entity type: " + entityType);
        };
    }
}
